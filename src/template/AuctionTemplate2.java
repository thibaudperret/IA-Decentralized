package template;

//the list of imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logist.LogistSettings;
import logist.Measures;
import logist.agent.Agent;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.plan.Action;
import logist.plan.Plan;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionTemplate2 implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	
    private long timeoutPlan;
    private long timeoutBid;
    
    private Map<Integer, Integer> wins;
    
	private Set<Task> wonTasks;
	private double confidence;

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
        timeoutBid = ls.get(LogistSettings.TimeoutKey.BID);
        
        wins = new HashMap<Integer, Integer>();
        
        wonTasks = new HashSet<Task>();
        confidence = -0.05d;
	}

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        List<Plan> plans = new ArrayList<Plan>();
        
        Solution finalS = Solution.selectInitialSolution(vehicles, wonTasks);
        finalS = Solution.finalSolution(finalS, 20000, timeoutPlan);

        System.out.println();
        System.out.println("***********");
        System.out.println("| agent " + agent.id() + " |");
        System.out.println("***********");
        System.out.println(finalS);
        System.out.println(wonTasks.size() + " tasks");        
        System.out.println("cost is " + Solution.cost(finalS) + ", win is " + wins.get(agent.id()));

        for (Vehicle v : vehicles) {
            City previous = v.getCurrentCity();
            Plan plan = new Plan(previous);

            if (finalS != null) {
                for (TaskAugmented t : finalS.get(v)) {
                    for (City c : previous.pathTo(t.city())) {
                        plan.append(new Action.Move(c));
                    }

                    if (t.isPickup()) {
                        plan.append(new Action.Pickup(t.task()));
                    } else {
                        plan.append(new Action.Delivery(t.task()));
                    }

                    previous = t.city();
                }
            }

            plans.add(plan);
        }

        return plans;
    }

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
	    wins.put(winner, (int) (wins.getOrDefault(winner, 0) + previous.reward));
	    
        System.out.println();
        System.out.println(winner + " won " + previous);
        for (int i = 0; i < bids.length; ++i) {
            System.out.print("  " + i + " bidded " + bids[i] + " CHF");
            System.out.println(i == agent.id() ? " (confidence was " + String.format("%.2g", confidence) + ")" : "");
        }
        
		if (winner == agent.id()) {
		    wonTasks.add(previous);            
		    confidence += 0.05d;
		} else {
		    confidence = Math.max(confidence - 0.05d, 0d);
		}
	}
	
	@Override
	public Long askPrice(Task task) {
	    long price = (long) marginalCost(task);
	    
	    if (price < 0) {
	        System.out.println(agent.id() + " is pretty dumb, " + price);
	    }
	    
	    price = Math.max(price, 20);
	    
	    return (long) (price * (1 + confidence));
	}
	
	private double bayesianMarginalCost(Task t) {
        double cost = 0;
        double weights = 0;
        
        for(City from : topology.cities()) {
            for(City to : topology.cities()) {
                if(isLink(from, to, t, wonTasks)) {
                    Task potential = new Task(-1, from, to, 0, 0);
                    Set<Task> includePotential = new HashSet<Task>(wonTasks);
                    includePotential.add(potential);
                    double weight = distribution.probability(from, to);
                    weights += weight;
                    cost += weight * marginalCost(t, includePotential, false);
                }
            }
        }
        
        return cost / weights;
    }
	
	private boolean isLink(City potentialFrom, City potentialTo, Task toBid, Set<Task> won) {
        boolean isLink = false;
        
        //it is a link if there is some match between toBid's cities and link's cities
        isLink = potentialFrom.equals(toBid.pickupCity)
              || potentialTo.equals(toBid.deliveryCity)
              || potentialFrom.equals(toBid.deliveryCity)
              || potentialTo.equals(toBid.pickupCity);
        
        for(Task t : won) {
            isLink = isLink || potentialFrom.equals(t.pickupCity)
                            || potentialTo.equals(t.deliveryCity);
        }
        
        return isLink;
	}
	
	private double marginalCost(Task toBid) {
	    return marginalCost(toBid, false);
	}
	
	private double marginalCost(Task toBid, boolean verbose) {
	    return marginalCost(toBid, wonTasks, verbose);
	}
	
	private double marginalCost(Task toBid, Set<Task> wonTasks, boolean verbose) {
	    long start = System.currentTimeMillis();
	    // 1st step: compute cost/solution without toBid
	    Solution without = Solution.selectInitialSolutionBis(agent.vehicles(), wonTasks);
	    without = Solution.finalSolution(without, 10000, timeoutBid / 2);	    
	    int costWithout = Solution.cost(without);
	    
	    // 2nd step: compute cost/solution with toBid
	    Set<Task> wonAndToBid = new HashSet<Task>(wonTasks);
	    wonAndToBid.add(toBid);
	    
	    Solution with = Solution.selectInitialSolutionBis(agent.vehicles(), wonAndToBid);
	    with = Solution.finalSolution(with, 10000, timeoutBid / 2);
        int costWith = Solution.cost(with);
        int marginalCost = costWith - costWithout;
        
	    
	    // 3rd step: check if obvious non optimal solutions
        Solution withoutEstimator = Solution.greedySolutionRemove(with, toBid); 
        int costWithoutEstimator = Solution.cost(withoutEstimator);  
        
        Solution withEstimator = Solution.greedySolutionAdd(without, toBid);     
        int costWithEstimator = Solution.cost(withEstimator);     
        
        int upperBound = costWithEstimator - costWithout;
        int count = 3;
        boolean problemWithout = marginalCost < 0;
        boolean problemWith = marginalCost > upperBound;
        
        while ((problemWith || problemWithout) && count > 0) {
            --count;
            long now = System.currentTimeMillis();
        
            if (problemWithout) {
                // Problem with "without" solution
                // Try recompute "without" solution
                if (verbose) {
                    System.out.println("agent " + agent.id() + " has problem with \'without\', " + count);
                }
                
                without = Solution.selectInitialSolutionBis(agent.vehicles(), wonTasks);
                without = Solution.finalSolution(without, 10000, (timeoutBid - (now - start))/ 4);       
                costWithout = Solution.cost(without);
            }
            
            if (problemWith) {
                // Problem with "with" solution
                // Recompute "with" solution
                if (verbose) {
                    System.out.println("agent " + agent.id() + " has problem with \'with\', " + count);   
                }

                with = Solution.selectInitialSolutionBis(agent.vehicles(), wonAndToBid);
                with = Solution.finalSolution(with, 10000, (timeoutBid - (now - start)) / 4);
                costWith = Solution.cost(with);
            }            

            marginalCost = costWith - costWithout;
            upperBound = costWithEstimator - costWithout;
            problemWithout = marginalCost < 0;
            problemWith = marginalCost > upperBound;
        }
        
        // Still a problem
        if (problemWithout) {
            // without = withoutEstimator;
            marginalCost = costWith - costWithoutEstimator;
            if (verbose) {
                System.out.println("agent " + agent.id() + " still has problem with \'without\', switching to estimator " + marginalCost);
            }
        }
        
        if (problemWith) {
            // with = withEstimator;
            marginalCost = costWithEstimator - costWithout;
            if (verbose) {
                System.out.println("agent " + agent.id() + " still has problem with \'with\', switching to estimator " + marginalCost);
            }
        }
        
        return marginalCost;
	}
	
	@Override
	public String toString() {
	    return agent.id() + "";
	}
	
}
