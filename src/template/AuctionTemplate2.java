package template;

//the list of imports
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import logist.LogistSettings;
import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.config.Parsers;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
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
    
	public Set<Task> wonTasks;

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
        
        wonTasks = new HashSet<Task>();
	}

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		if (winner == agent.id()) {
		    wonTasks.add(previous);
		}
	}
	
	@Override
	public Long askPrice(Task task) {
	    return (long) marginalCost(task);
	}
	
	private double marginalCost(Task toBid) {
	    // 1st step: compute cost/solution without toBid
	    Solution without = Solution.selectInitialSolution(agent.vehicles(), wonTasks);
	    without = Solution.finalSolution(without, 1000, timeoutPlan);	    
	    double costWithout = Solution.cost(without);
	    
	    // 2nd step: compute cost/solution with toBid
	    Set<Task> wonAndToBid = new HashSet<Task>(wonTasks);
	    wonAndToBid.add(toBid);
	    
	    Solution with = Solution.selectInitialSolution(agent.vehicles(), wonAndToBid);
	    with = Solution.finalSolution(with, 1000, timeoutPlan);
        double costWith = Solution.cost(with);
        double marginalCost = costWith - costWithout;
        
	    
	    // 3rd step: check if obvious non optimal solutions
        Solution withoutEstimator = Solution.greedySolutionRemove(with, toBid); 
        double costWithoutEstimator = Solution.cost(withoutEstimator);  
        
        Solution withEstimator = Solution.greedySolutionAdd(without, toBid);     
        double costWithEstimator = Solution.cost(withEstimator);     
        
        double upperBound = costWithEstimator - costWithout;
        int count = 3;
        boolean problemWith = marginalCost > upperBound;
        boolean problemWithout = marginalCost < 0;
        
        while ((problemWith || problemWithout) && count > 0) {
            --count;
        
            if (problemWithout) {
                // Problem with "without" solution
                // Try recompute "without" solution
                System.out.println("Problem without, " + count);

                without = Solution.selectInitialSolution(agent.vehicles(), wonTasks);
                without = Solution.finalSolution(without, 1000, timeoutPlan);       
                costWithout = Solution.cost(without);
            }
            
            if (problemWith) {
                // Problem with "with" solution
                // Recompute "with" solution
                System.out.println("Problem with, " + count);

                with = Solution.selectInitialSolution(agent.vehicles(), wonAndToBid);
                with = Solution.finalSolution(with, 1000, timeoutPlan);
                costWith = Solution.cost(with);
            }            

            marginalCost = costWith - costWithout;
            upperBound = costWithEstimator - costWithout;
            problemWith = marginalCost > upperBound;
            problemWithout = marginalCost < 0;
        }
        
        // Still a problem
        if (problemWithout) {
            System.out.println("Still problem without, switching to estimator");
            // without = withoutEstimator;
            marginalCost = costWith - costWithoutEstimator;
        }
        
        if (problemWith) {
            // with = withEstimator;
            System.out.println("Still problem with, switching to estimator");
            marginalCost = costWithEstimator - costWithout;
        }
        
        return marginalCost;
	}
	
}
