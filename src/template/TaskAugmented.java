package template;

import java.util.Objects;

import logist.task.Task;
import logist.topology.Topology.City;

public class TaskAugmented {

	private Task task;
	private boolean isPickup;
	
	public TaskAugmented(Task task, boolean isPickup) {
		this.isPickup = isPickup;
		this.task = task;
	}
	
	public boolean isPickup() {
		return isPickup;
	}
	
	public boolean isDeliver() {
		return !isPickup;
	}
	
	public Task task() {
		return task;
	}
	
	public City city() {
	    return isPickup ? task.pickupCity : task.deliveryCity;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof TaskAugmented) {
			TaskAugmented that = (TaskAugmented)o;
			return ( (that.isPickup == this.isPickup) && (that.task.equals(this.task)));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(task, isPickup);
	}
	
	@Override
	public String toString() {
	    return (isPickup ? "pick" : "deliver") + " " + task.pickupCity + " -> " + task.deliveryCity;
	}
}
