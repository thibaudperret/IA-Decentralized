package template;

//the list of imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
public class AuctionTemplate implements AuctionBehavior {

	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private City currentCity;

    private long timeoutSetup;
    private long timeoutPlan;
        
    private Task badVersion;
    private Task badVersionIfWin;
    private Task goodVersion;
    
	private Set<Task> ours;	
	private Solution ourSolution;
	private Solution potentialNextSolution;
	private Solution inefficientSolution;
	private double ourCost;
	private double potentialNextCost;
	
	private double confidence;
	private double valenceFactor = 10;

	@Override
	public void setup(Topology topology, TaskDistribution distribution,	Agent agent) {
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_auction.xml");
        } catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }

        // the setup method cannot last more than timeout_setup milliseconds
        timeoutSetup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeoutPlan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;

//		long seed = -9019554669489983951L * currentCity.hashCode() * agent.id();
//		this.random = new Random(seed);
		this.badVersion = null;
		this.badVersionIfWin = null;
		this.goodVersion = null;
		this.ours = new HashSet<Task>();

		Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        for(Vehicle v : agent.vehicles()) {
            plan.put(v, new LinkedList<TaskAugmented>());
        }
		
		this.ourSolution = new Solution(plan);
		this.ourCost = 0;
		this.confidence = 1.2;
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {	    
		if (winner == agent.id()) {
	        System.out.println("winner is " + winner + ", task " + previous + " (confidence was " + confidence + ")");
	        for (int i = 0; i < bids.length; ++i) {
	            if (bids[i] > 0) {
	                System.out.println("\tagent " + i + " bidded " + bids[i]);
	            } else {
	                System.err.println("\tagent " + i + " a chié dans la colle");
	            }
	        }
	        
	        System.out.println();
	        
			ours.add(previous);
			goodVersion = previous;
			badVersion = badVersionIfWin;
			ourSolution = potentialNextSolution;
			ourCost = potentialNextCost;
			confidence += 0.1;
		} else {
		    confidence -= 0.1;
		    if (confidence < 1) {
		        confidence = 1;
		    }
		}
	}
	
	@Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        List<Plan> plans = planFromSolution(ourSolution, vehicles);
        return plans;
    }
	
	/**
     * Creates a <code>Plan</code> of the Logist library from a solution
     */
    private List<Plan> planFromSolution(Solution finalS, List<Vehicle> vehicles) {
        List<Plan> plans = new ArrayList<Plan>();
        
        System.out.println("agent " + agent.id());
        System.out.println(finalS);
        System.out.println(ours.size() + " tasks\n");
        
        for (Vehicle v : vehicles) {            
            City previous = v.getCurrentCity();
            Plan plan = new Plan(previous);
            
            if (finalS != null) {            
                for (TaskAugmented t : finalS.get(v)) {
                    for (City c : previous.pathTo(t.city())) {
                        plan.append(new Action.Move(c));
                    }
                    
                    if (t.isPickup()) {
                        if (t.task() == badVersion) {
                            plan.append(new Action.Pickup(goodVersion));
                        } else {
                            plan.append(new Action.Pickup(t.task()));
                        }
                    } else {
                        if (t.task() == badVersion) {
                            plan.append(new Action.Delivery(goodVersion));
                        } else {
                            plan.append(new Action.Delivery(t.task()));
                        }
                    }
                    
                    previous = t.city();
                }
            }
            
            plans.add(plan);
        }
        
        return plans;
    }
	
	@Override
	public Long askPrice(Task task) {
	    badVersionIfWin = task;
	    
	    double marginalCost = marginalCost(ours, task);
	    double inefficientCost = 0;
	    if (!ours.isEmpty()) {
	        inefficientCost(ourSolution, task);
	    }
	    
	    double count = 3;
	    
	    while (!ours.isEmpty() && count > 0) {
	        // Means old solution is not optimal
    	    if (marginalCost < 0) {
    	        // Recompute ourSolution
    	        Solution s = Solution.selectInitialSolution(agent.vehicles(), ours);
    	        Solution f = Solution.finalSolution(s, 1000, timeoutPlan);
    	        ourSolution = f;
    	        ourCost = Measures.unitsToKM(Solution.cost(f));
    	        
    	        marginalCost = potentialNextCost - ourCost;
    	    }
    	    // Means new solution is not optimal
    	    if (marginalCost > inefficientCost) {
    	        // Recompute potentialNextSolution
                marginalCost = marginalCost(ours, task);
    	    }
            --count;
	    }
	    
	    // Worst case scenario, one of the solution is still not optimal for sure, we use inefficient solution then
	    if (!ours.isEmpty() && marginalCost < 0) {
	        ourSolution = potentialNextSolution;
	        ourSolution.remove(task);
            ourCost = Measures.unitsToKM(Solution.cost(ourSolution));
            
            marginalCost = potentialNextCost - ourCost;
	    }
	    
	    if (!ours.isEmpty() && marginalCost > inefficientCost) {
	        potentialNextSolution = inefficientSolution;
	        potentialNextCost = Measures.unitsToKM(Solution.cost(potentialNextSolution));
            
            marginalCost = potentialNextCost - ourCost;
	    }
	    
	    return (long) (marginalCost * confidence - (task.deliveryCity.neighbors().size() + task.pickupCity.neighbors().size()) * valenceFactor);
	}
	
	private double inefficientCost(Solution s, Task t) {
	    City pickup = t.pickupCity;
	    City deliver = t.deliveryCity;
	    
        Set<City> cities = new HashSet<City>();

        Vehicle bestVehicle = null;
        TaskAugmented bestTask = null;
        double bestCost = Double.POSITIVE_INFINITY;
	    
	    for (Vehicle v : s.vehicles()) {
	        for (TaskAugmented vt : s.get(v)) {
	            City city = vt.city();
	            if (!cities.contains(city)) {
	                cities.add(vt.city());
	                double cost = Measures.unitsToKM(city.distanceUnitsTo(pickup) + pickup.distanceUnitsTo(deliver) + deliver.distanceUnitsTo(city));
	                if (bestCost > cost) {
	                    bestCost = cost;
	                    bestTask = vt;
	                    bestVehicle = v;
	                }
	            }
	        }
	    }
	    
	    inefficientSolution = new Solution(s);
	    inefficientSolution.add(bestVehicle, inefficientSolution.get(bestVehicle).indexOf(bestTask), t);
	    
	    return bestCost;
	}
	
	private double marginalCost(Set<Task> tasks, Task task) {        
        Set<Task> oursCopy = new HashSet<Task>(tasks);
        oursCopy.add(task);
        Solution s = Solution.selectInitialSolution(agent.vehicles(), oursCopy);
//        Solution s = new Solution(ourSolution);
//        s.add(agent.vehicles().get(0), task);

        Solution f = Solution.finalSolution(s, 1000, timeoutPlan);
        
        double cost = 0;
        for (Vehicle v : agent.vehicles()) {
            cost += Measures.unitsToKM(Solution.cost(s, v) * v.costPerKm());
        }

        potentialNextSolution = f;
        potentialNextCost = cost;
        
        return potentialNextCost - ourCost;
	}
	
	private double bayesMargCost(Task t, Set<Task> tasks) {
		double cost = 0;
		double weights = 0;
		for(City from : topology.cities()) {
			for(City to : topology.cities()) {
				if(isLink(from, to, t, tasks)) {
					Task potential = new Task(-1, from, to, 0, 0);
					Set<Task> includePotential = new HashSet<Task>(tasks);
					includePotential.add(potential);
					double weight = distribution.probability(from, to);
					weights += weight;
					cost += weight * marginalCost(includePotential, t);
				}
			}
		}
		return cost / weights;
	}
	
	private boolean isLink(City potentialFrom, City potentialTo, Task toBid, Set<Task> won) {
		boolean is_link = false;
		
		//it is a link if there is some match between toBid's cities and link's cities
		is_link = is_link || potentialFrom.equals(toBid.pickupCity)
						  || potentialTo.equals(toBid.deliveryCity)
						  || potentialFrom.equals(toBid.deliveryCity)
						  || potentialTo.equals(toBid.pickupCity);
		
		for(Task t : won) {
			is_link = is_link || potentialFrom.equals(t.pickupCity)
							  || potentialTo.equals(t.deliveryCity);
		}
		
		return is_link;
	}
	
	@Deprecated
	private double bayesianMarginalCost(Task task) {
	    Set<City> cities = new HashSet<City>();
	    for (Task t : ours) {
	        cities.add(t.pickupCity);
	        cities.add(t.deliveryCity);
	    }
	    
	    for (City c : cities) {
	        //Task intermediate = new Task(-1, c, task.pickupCity, 0, 3);
	    }
	    
	    return 0.0;
	}
}
