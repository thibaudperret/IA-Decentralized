package template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskSet;
import logist.topology.Topology.City;

public class Solution {
    
    private Map<Vehicle, List<TaskAugmented>> plan;
    
    private final static Random random = new Random(/*12*/);
    private final static double probability = 0.2;
    
    public Solution(Map<Vehicle, List<TaskAugmented>> plan) {
        Map<Vehicle, List<TaskAugmented>> copy = new HashMap<Vehicle, List<TaskAugmented>>();
        
        for (Entry<Vehicle, List<TaskAugmented>> entry : plan.entrySet()) {
            copy.put(entry.getKey(), new LinkedList<TaskAugmented>(entry.getValue()));
        }
        
        this.plan = copy;
    }
    
    public Solution(Solution that) {
        this(that.plan);
    }
    
    @Override
    public boolean equals(Object o) {
        if (o instanceof Solution) {
            Solution that = (Solution) o;
            return this.plan.equals(that.plan);
        }
        
        return false;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(plan);
    }
    
    public List<TaskAugmented> get(Vehicle v) {
        return plan.get(v);
    }
    
    public void put(Vehicle v, List<TaskAugmented> l) {
        plan.put(v, l);
    }
    
    public void add(Vehicle v, TaskAugmented t) {
        plan.get(v).add(t);
    }
    
    public void add(Vehicle v, Task t) {
        TaskAugmented p = new TaskAugmented(t, true);
        TaskAugmented d = new TaskAugmented(t, false);
        plan.get(v).add(p);
        plan.get(v).add(d);
    }
    
    public void add(Vehicle v, int i, TaskAugmented t) {
        plan.get(v).add(i, t);
    }
    
    public void add(Vehicle v, int i, Task t) {
        TaskAugmented p = new TaskAugmented(t, true);
        TaskAugmented d = new TaskAugmented(t, false);
        plan.get(v).add(i, p);
        plan.get(v).add(i + 1, d);
    }
    
    public void remove(Vehicle v, TaskAugmented t) {
        plan.get(v).remove(t);
    }
    
    public void remove(Task t) {
        TaskAugmented p = new TaskAugmented(t, true);
        TaskAugmented d = new TaskAugmented(t, false);
        for (Vehicle v : vehicles()) {
            if (plan.get(v).contains(p)) {
                remove(v, p);
                remove(v, d);
            }
        }
    }
    
    public int nbVehicle() {
        return plan.size();
    }
    
    public Set<Entry<Vehicle, List<TaskAugmented>>> entries() {
        return plan.entrySet();
    }
    
    public List<Vehicle> vehicles() {
        List<Vehicle> vehicles = new ArrayList<Vehicle>();
        
        for (Entry<Vehicle, List<TaskAugmented>> e : entries()) {
            vehicles.add(e.getKey());
        }
        
        return vehicles;
    }
    
    @Override
    public String toString() {
        String s = "{\n";

        for (Entry<Vehicle, List<TaskAugmented>> e : plan.entrySet()) {
            s += "\t" + e.getKey().name() + " : " + e.getValue() + "\n";
        }
        
        s += "}\n";
        
        return s;
    }
    
    public static Solution greedySolutionAdd(Solution s, Task t) {
        Solution newS = new Solution(s);
        
        City tPickup = t.pickupCity;
        City tDeliver = t.deliveryCity;
        
        Set<City> visited = new HashSet<City>();
        
        double bestCost = Double.MAX_VALUE;
        Vehicle bestVehicle = newS.vehicles().get(0);
        int bestI = -1;
        
        for (Vehicle v : newS.vehicles()) {
            for (int i = 0; i < newS.plan.get(v).size(); ++i) { //TaskAugmented vt : newS.plan.get(v)) {
                TaskAugmented vt = newS.plan.get(v).get(i);
                City vtCity = vt.city();
                if (vt.isDeliver() && !visited.contains(vtCity)) {
                    visited.add(vtCity);
                    double cost = vtCity.distanceUnitsTo(tPickup) + tPickup.distanceUnitsTo(tDeliver) + tDeliver.distanceUnitsTo(vtCity);
                    if (bestCost > cost) {
                        bestCost = cost;
                        bestVehicle = v;
                        bestI = i;
                    }
                }
            }
        }
        
        TaskAugmented tp = new TaskAugmented(t, true);
        TaskAugmented td = new TaskAugmented(t, false);
        newS.plan.get(bestVehicle).add(bestI + 1, tp);
        newS.plan.get(bestVehicle).add(bestI + 2, td);
        
        return newS;
    }
    
    public static Solution greedySolutionRemove(Solution s, Task t) {
        TaskAugmented tp = new TaskAugmented(t, true);
        TaskAugmented td = new TaskAugmented(t, false);
        
        Solution newS = new Solution(s);
        
        for (Vehicle v : newS.vehicles()) {
            if (newS.plan.get(v).contains(tp)) {
                newS.plan.get(v).remove(tp);
                newS.plan.get(v).remove(td);
            }
        }
        
        return newS;
    }
    
    /**
     * Creates the initial solution by putting every task in the biggest vehicle given. Every task will be picked up and delivered before the next
     */
    public static Solution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
        Vehicle biggest = null;
        double bestCapacity = 0;
        
        Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        
        // find biggest
        for(Vehicle v : vehicles) {
            plan.put(v, new LinkedList<TaskAugmented>());
            
            double currentCapacity = v.capacity();
            if (currentCapacity > bestCapacity) {
                biggest = v;
                bestCapacity = currentCapacity;
            }
        }
        
        // put in biggest vehicle
        for (Task task : tasks) {
            TaskAugmented tp = new TaskAugmented(task, true);
            TaskAugmented td = new TaskAugmented(task, false);
            
            if (task.weight > bestCapacity) {
                return null;
            }

            plan.get(biggest).add(tp);
            plan.get(biggest).add(td);            
        }
        
        return new Solution(plan);
    }
    
    /**
     * Creates the initial solution by putting every task in the biggest vehicle given. Every task will be picked up and delivered before the next
     */
    public static Solution selectInitialSolution(List<Vehicle> vehicles, Set<Task> tasks) {
        Vehicle biggest = null;
        double bestCapacity = 0;
        
        Map<Vehicle, List<TaskAugmented>> plan = new HashMap<Vehicle, List<TaskAugmented>>();
        
        // find biggest
        for(Vehicle v : vehicles) {
            plan.put(v, new LinkedList<TaskAugmented>());
            
            double currentCapacity = v.capacity();
            if (currentCapacity > bestCapacity) {
                biggest = v;
                bestCapacity = currentCapacity;
            }
        }
        
        // put in biggest vehicle
        for (Task task : tasks) {
            TaskAugmented tp = new TaskAugmented(task, true);
            TaskAugmented td = new TaskAugmented(task, false);
            
            if (task.weight > bestCapacity) {
                return null;
            }

            plan.get(biggest).add(tp);
            plan.get(biggest).add(td);            
        }
        
        return new Solution(plan);
    }


    /**
     * Does <code>iter</code> iterations of <code>chooseNeighbors</code> to find a suboptimal solution
     */
    public static Solution finalSolution(Solution initS, int iter, long timeoutPlan) {
        Solution returnS = initS;
        long start = System.currentTimeMillis();
        int lastCost = 0;
        boolean inferior;
        int count = 0;
        
        for (int i = 0; i < iter; ++i) {
            // We subtract 100 ms from the timeoutPlan so that we do not realise too late we've taken too much time
            if (System.currentTimeMillis() - start > timeoutPlan - 1000 || count > 100) {
                return returnS;
            }
            returnS = chooseNeighbors(returnS, probability);   
            
            int cost = cost(returnS);
            inferior = Math.abs(lastCost - cost) < 0.01;
            lastCost = cost;
            
            if (inferior) {
                ++count;
            } else {
                count = 0;
            }
        }

        return returnS;
    }
    
    /**
     * Computed the best solution from vehicle changes and order changes, return
     * this best solution according to probability
     */
    private static Solution chooseNeighbors(Solution s, double pickProb) {
        List<Vehicle> nonEmptyVehicles = new ArrayList<Vehicle>();
        for (Vehicle v2 : s.vehicles()) {
            if (!s.get(v2).isEmpty()) {
                nonEmptyVehicles.add(v2);
            }
        }
        
        if (nonEmptyVehicles.isEmpty()) {
            return s;
        }
        
        // get random vehicle that's not empty
        Vehicle v = nonEmptyVehicles.get(random.nextInt(nonEmptyVehicles.size()));

        List<TaskAugmented> vTasks = s.get(v);
        Task t = vTasks.get(random.nextInt(vTasks.size())).task(); // the task that will be passed to other vehicles and changed in order

        List<Solution> changedVehicleList = changeVehicle(s, v, t);

        List<Solution> changedOrderList = changeOrder(s, v, t);

        List<Solution> changedEverythingList = new ArrayList<Solution>();
        changedEverythingList.addAll(changedVehicleList);
        changedEverythingList.addAll(changedOrderList);

        Solution best = getBest(changedEverythingList);

        return random.nextDouble() < pickProb ? best : s;
    }
    
    /**
     * Selects the best solution amongst the given ones regarding the cost of a solution
     */
    private static Solution getBest(List<Solution> sList) {
        double bestCost = Double.POSITIVE_INFINITY;
        double bestMaxLength = Double.POSITIVE_INFINITY;
        Solution bestS = null;
        
        for (Solution s : sList) {
            double cost = 0;
            int maxLength = 0;
            for (Vehicle v : s.vehicles()) {
                cost += cost(s, v);
                maxLength = Integer.max(maxLength, s.get(v).size());
            }
            
            if (cost < bestCost || (cost == bestCost && maxLength < bestMaxLength)) {
                bestCost = cost;
                bestMaxLength = maxLength;
                bestS = s;
            }
        }
        
        return bestS;
    }
    
    public static int cost(Solution s) {
        int totalCost = 0;
        for (Vehicle v : s.vehicles()) {
            totalCost += cost(s, v);
        }
        
        return totalCost;
    }
     
    /**
     * Cost of a solution for one vehicle
     */
    public static int cost(Solution s, Vehicle v) {
        int cost = 0;
        
        if (s.get(v).isEmpty()) {
            return cost;
        }

        cost += v.getCurrentCity().distanceUnitsTo(s.get(v).get(0).city());
                
        for (int i = 0; i < s.get(v).size() - 1; ++i) {
            cost += s.get(v).get(i).city().distanceUnitsTo(s.get(v).get(i + 1).city());
        }
        
        return cost;
    }
    
    /**
     * Creates derivated solutions from <code>s</code> by putting random task of <code>v</code> to other vehicles
     */
    private static List<Solution> changeVehicle(Solution s, Vehicle v, Task t) {
        List<Solution> sList = new ArrayList<Solution>();
        if (s.get(v).isEmpty()) {
            return sList;
        }
                
        TaskAugmented tp = new TaskAugmented(t, true); // a pick up
        TaskAugmented td = new TaskAugmented(t, false); // equivalent delivery
        
        for (Vehicle v2 : s.vehicles()) {
            if (!v.equals(v2)) {
                if (v2.capacity() > tp.task().weight) {
                    Solution newS = new Solution(s);
                    
                    newS.remove(v, tp);
                    newS.remove(v, td);
                    
                    newS.add(v2, 0, tp);
                    newS.add(v2, 1, td);
                    
                    sList.add(newS);
                }
            }
        }
        
        return sList;
    }
    
    /**
     * Creates derivated solutions from <code>s</code> by changing the order of task <code>t</code> in vehicle <code>v</code>
     */
    private static List<Solution> changeOrder(Solution s, Vehicle v, Task t) {
        List<Solution> sList = new ArrayList<Solution>();
        Solution newS = new Solution(s);

        TaskAugmented tp = new TaskAugmented(t, true);
        TaskAugmented td = new TaskAugmented(t, false);
        
        newS.remove(v, tp);
        newS.remove(v, td);
        
        if (newS.get(v).isEmpty()) {
            return Arrays.asList(new Solution(s));
        }
        
        List<List<Integer>> indicesList = new ArrayList<List<Integer>>();
        
        int min = 0;
        int max = 0;
        
        int weightAcceptable = v.capacity();
        
        boolean commited = false;
        
        for (int i = 0; i < newS.get(v).size(); ++i) {
            TaskAugmented curr = newS.get(v).get(i);
            
            // finish
            if ((weightAcceptable < t.weight && !commited) || i == (newS.get(v).size() - 1)) {
                max = i + 1;
                indicesList.add(Arrays.asList(min, max));
                commited = true;
            }
            
            if (weightAcceptable >= t.weight && commited && i != (newS.get(v).size() - 1)) {
                min = i;
                commited = false;
            }
            
            if (curr.isPickup()) {
                weightAcceptable -= curr.task().weight;
            } else {
                weightAcceptable += curr.task().weight;
            }
        }
        
        for (List<Integer> minMax : indicesList) {
            for (int i = minMax.get(0); i <= minMax.get(1); ++i) {
                for (int j = i; j <= minMax.get(1); ++j) {
                    Solution newNewS = new Solution(newS);
                    
                    newNewS.add(v, i, tp);
                    newNewS.add(v, j + 1, td);   
                    
                    sList.add(newNewS);
                }
            }
        }
        
        return sList;
    }
}
