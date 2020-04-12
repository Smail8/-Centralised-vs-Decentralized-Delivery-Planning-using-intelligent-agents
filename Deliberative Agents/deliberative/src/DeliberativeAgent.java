package template;

/* import table */
import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.DeliberativeBehavior;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import template.Test.State;

import java.util.List;
import java.util.PriorityQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.HashSet;

/**
 * An optimal planner for one vehicle.
 */
@SuppressWarnings("unused")
public class DeliberativeTemplate implements DeliberativeBehavior {

	enum Algorithm { BFS, ASTAR }
	
	private static final boolean PICKUP = true;
	private static final boolean DELIVER = false;
	private static final char REMAINING = 'r';
	private static final char PICKEDUP = 'p';
	private static final char DELIVERED = 'd';
	
	/* Environment */
	Topology topology;
	TaskDistribution td;
	
	/* the properties of the agent */
	Agent agent;
	public int capacity;
	public double costPerKm;
	TaskSet transferCarriedTasks;

	/* the planning class */
	Algorithm algorithm;
	
	//Define State class
	public class State{
		public State previousState;
		public City currentCity;
		public String taskStatus;
		public Task task;
		public double cost;
		public int weight;
		public boolean action;
		
		public State(State prev, City current, String status, int w, boolean a, Task t, double c) {
			this.previousState = prev;
			this.currentCity = current;
			this.taskStatus = new String(status);
			this.weight = w;
			this.action = a;
			this.task = t;
			this.cost = c;
		}
		
		@Override
		public int hashCode() {
			final int factor = 31;
			int code = 0;
			
			code = factor*((currentCity != null) ? currentCity.hashCode() : 0) + factor*((taskStatus != null) ? taskStatus.hashCode() : 0) + factor*weight;
			
			return code;
		}
		
		@Override
		public boolean equals(Object obj) {
			boolean flag = false;
			if (!(obj instanceof State)) 
			 return false;
			if (this == obj)
				return true;
			 State state = (State) obj;
			 flag = (state != null && weight == state.weight && currentCity == state.currentCity && taskStatus.equals(state.taskStatus));
			 if(!flag)
				 return false;
			 else {
				 if(cost < state.cost)
					 return false;
				 else
					 return true;
			 }	 
		}
	}
	
	
	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		this.topology = topology;
		this.td = td;
		this.agent = agent;
		
		
		this.capacity = agent.vehicles().get(0).capacity();
		this.costPerKm = agent.vehicles().get(0).costPerKm();
		
		// initialize the planner
		String algorithmName = agent.readProperty("algorithm", String.class, "ASTAR");
		
		// Throws IllegalArgumentException if algorithm is unknown
		algorithm = Algorithm.valueOf(algorithmName.toUpperCase());
		
		// ...
	}
	
	@Override
	public Plan plan(Vehicle vehicle, TaskSet tasks) {
		
		Plan plan = new Plan(vehicle.getCurrentCity());

		// Compute the plan with the selected algorithm.
		switch (algorithm) {
		case ASTAR:
			// ...
			plan = AStarPlan(vehicle, tasks);
			break;
		case BFS:
			// ...
			plan = BFSPlan(vehicle, tasks);
			break;
		default:
			throw new AssertionError("Should not happen.");
		}		
		
		System.out.println(tasks);
		return plan;
	}
	
	private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
		City current = vehicle.getCurrentCity();
		Plan plan = new Plan(current);

		for (Task task : tasks) {
			// move: current city => pickup location
			for (City city : current.pathTo(task.pickupCity))
				plan.appendMove(city);

			plan.appendPickup(task);

			// move: pickup location => delivery location
			for (City city : task.path())
				plan.appendMove(city);

			plan.appendDelivery(task);

			// set current city
			current = task.deliveryCity;
		}
		return plan;
	}
	
	private Plan BFSPlan (Vehicle vehicle, TaskSet tasks) {
		
		System.out.println("Start BFS");
		
		long startTime = System.currentTimeMillis();
		City startCity = vehicle.getCurrentCity();
		
		Plan bestPlan = new Plan(startCity);
		State goalState = null;

		HashSet<State> stateSet = new HashSet<State>();
		LinkedList<State> Qtable = new LinkedList<State>();
		
		if (transferCarriedTasks != null) {                     
			tasks.addAll(transferCarriedTasks);                  
		}
		
		double minimumFinalCost = 999999;
		int numTasks = tasks.size();
		int iteration = 0;
		
		Task[] allTasks = tasks.toArray(new Task[numTasks]);
		
		String initialTaskStatus = "";
		for (int i = 0; i < numTasks; i++) {
			if(transferCarriedTasks != null && transferCarriedTasks.contains(allTasks[i]))                
				initialTaskStatus += PICKEDUP;                                      					 
			else                                                                     				     
				initialTaskStatus += REMAINING;                               				       
		}

		State initialState = new State(null, startCity, initialTaskStatus, 0, PICKUP, null, 0);
		State tempState = null;
		String tempTaskStatus = null;
		
		Qtable.add(initialState);
		
		do {
	
			if (Qtable.isEmpty()) {
				break;
			}
			
			iteration++;
			
			tempState = Qtable.pop();
			tempTaskStatus = tempState.taskStatus;
			
			if(tempTaskStatus.replace("d", "").length() == 0) {
				
				if(tempState.cost < minimumFinalCost) {
					minimumFinalCost = tempState.cost;
					goalState = tempState;
				}
				continue;
			}
			
			if (tempState == null)
				break;
			else {
				if (!stateSet.contains(tempState)) {
					stateSet.add(tempState);
					findSuccessorsB(tempState, allTasks, Qtable, numTasks, tempTaskStatus);
				}
			}
		}while(true);
		
		retrievePlan(goalState, bestPlan);
		long endTime = System.currentTimeMillis();
		
		System.out.println("plan = "+bestPlan+"");
		System.out.println("cost = "+minimumFinalCost+"");
		System.out.println("iterations = "+iteration+"");
		System.out.println("Execution time: " + (endTime - startTime) + "");
		
		return bestPlan;
	}
	
	private Plan AStarPlan(Vehicle vehicle, TaskSet tasks) {
		
		System.out.println("Start A*");
		
		long startTime = System.currentTimeMillis();
		City startCity = vehicle.getCurrentCity();
		
		Plan bestPlan = new Plan(startCity);
		State goalState = null;

		HashSet<State> stateSet = new HashSet<State>();
		
		if (transferCarriedTasks != null) {                     
			tasks.addAll(transferCarriedTasks);                 
		}
		
		double minimumFinalCost = 999999;
		int numTasks = tasks.size();
		int iteration = 0;
		
		Task[] allTasks = tasks.toArray(new Task[numTasks]);
		
		String initialTaskStatus = "";
		for (int i = 0; i < numTasks; i++) {
			if(transferCarriedTasks != null && transferCarriedTasks.contains(allTasks[i]))                 
				initialTaskStatus += PICKEDUP;                                      					 
			else                                                                     				     
				initialTaskStatus += REMAINING;                               				      
		}

		State initialState = new State(null, startCity, initialTaskStatus, 0, PICKUP, null, 0);
		State tempState = null;
		String tempTaskStatus = null;

		StateComparator stateComparator = new StateComparator(allTasks);
		PriorityQueue<State> Qtable = new PriorityQueue<State>(500000000, stateComparator);
		
		Qtable.add(initialState);
		
		do {
			
			if (Qtable.isEmpty()) {
				break;
			}
			
			iteration++;
			
			tempState = Qtable.poll();
			tempTaskStatus = tempState.taskStatus;

			if(tempTaskStatus.replace("d", "").length() == 0) {
				minimumFinalCost = tempState.cost;
				goalState = tempState;
				break;
			}
			
			if (tempState == null)
				break;
			
			else {
				if (!stateSet.contains(tempState)) {
					stateSet.add(tempState);
					findSuccessorsA(tempState, allTasks, Qtable, numTasks, tempTaskStatus);
				}
			}	
		}while(true);
		
		retrievePlan(goalState, bestPlan);
		long endTime = System.currentTimeMillis();
		
		System.out.println("plan = "+bestPlan+"");
		System.out.println("cost = "+minimumFinalCost+"");
		System.out.println("iterations = "+iteration+"");
		System.out.println("Execution time: " + (endTime - startTime) + "");
		
		return bestPlan;
	}

	@Override
	public void planCancelled(TaskSet carriedTasks) {
		
		if (!carriedTasks.isEmpty()) {
			// This cannot happen for this simple agent, but typically
			// you will need to consider the carriedTasks when the next
			// plan is computed.
			this.transferCarriedTasks = carriedTasks;
		}
	}
	
	public void findSuccessorsB(State tempState, Task[] allTasks, LinkedList<State> Qtable, int numTasks, String tempTaskStatus){
		
		City currentCity = tempState.currentCity;
		double currentCost = tempState.cost;
		int currentWeight = tempState.weight;
		int newWeight = 0;
		double newCost = 0;
		Task task = null;
		
		for(int i=0; i < numTasks; i++) {
			
			if(tempTaskStatus.charAt(i) == REMAINING) {
				task = allTasks[i];
				newWeight = task.weight + currentWeight;
				
				if (newWeight <= capacity) {
					newCost = currentCost + currentCity.distanceTo(task.pickupCity)*costPerKm;
					char[] taskStat = tempTaskStatus.toCharArray();
					taskStat[i] = PICKEDUP;
					
					State stateToAdd = new State(tempState, task.pickupCity, new String(taskStat), newWeight, PICKUP, task, newCost);
					Qtable.add(stateToAdd);
				}
			}
			
			else if(tempTaskStatus.charAt(i) == PICKEDUP) {
				task = allTasks[i];
				newCost = currentCost + currentCity.distanceTo(task.deliveryCity)*costPerKm;
				char[] taskStat = tempTaskStatus.toCharArray();
				taskStat[i] = DELIVERED;
				newWeight = currentWeight - task.weight;
				
				State stateToAdd = new State(tempState, task.deliveryCity, new String(taskStat), newWeight, DELIVER, task, newCost);
				Qtable.add(stateToAdd);
			}
		}
	}
	
public void findSuccessorsA(State tempState, Task[] allTasks, PriorityQueue<State> Qtable, int numTasks, String tempTaskStatus){
		
		City currentCity = tempState.currentCity;
		double currentCost = tempState.cost;
		int currentWeight = tempState.weight;
		int newWeight = 0;
		double newCost = 0;
		Task task = null;
		
		for(int i=0; i < numTasks; i++) {
			
			if(tempTaskStatus.charAt(i) == REMAINING) {
				task = allTasks[i];
				newWeight = task.weight + currentWeight;
				
				if (newWeight <= capacity) {
					newCost = currentCost + currentCity.distanceTo(task.pickupCity)*costPerKm;
					char[] taskStat = tempTaskStatus.toCharArray();
					taskStat[i] = PICKEDUP;
					
					State stateToAdd = new State(tempState, task.pickupCity, new String(taskStat), newWeight, PICKUP, task, newCost);
					Qtable.add(stateToAdd);
				}
			}
			
			else if(tempTaskStatus.charAt(i) == PICKEDUP) {
				task = allTasks[i];
				newCost = currentCost + currentCity.distanceTo(task.deliveryCity)*costPerKm;
				char[] taskStat = tempTaskStatus.toCharArray();
				taskStat[i] = DELIVERED;
				newWeight = currentWeight - task.weight;
				
				State stateToAdd = new State(tempState, task.deliveryCity, new String(taskStat), newWeight, DELIVER, task, newCost);
				Qtable.add(stateToAdd);
			}
		}
	}
	
	public void retrievePlan(State goalState, Plan plan) {
		State previousState = goalState.previousState;
		if(previousState != null) {
			retrievePlan(previousState, plan);
			for(City city:previousState.currentCity.pathTo(goalState.currentCity)) 
				plan.appendMove(city);
			if(goalState.action == PICKUP)
				plan.appendPickup(goalState.task);
			else if(goalState.action == DELIVER)
				plan.appendDelivery(goalState.task);
		}
	}
	
	public class StateComparator implements Comparator<State>{
		
		private Task[] allTasks;


		public StateComparator(Task[] allTasks) {
			this.allTasks = allTasks;
		
		}

		@Override
		public int compare(State a, State b) {
			
			double longestPatha = 0;
			double longestPathb = 0;
			int numTasksa = a.taskStatus.length();
			int numTasksb = b.taskStatus.length();
			
			for(int i= 0 ; i < numTasksa ; i++) {
				
				if(a.taskStatus.charAt(i) == PICKEDUP) {
					if(a.currentCity.distanceTo(allTasks[i].deliveryCity) > longestPatha) 
						longestPatha = a.currentCity.distanceTo(allTasks[i].deliveryCity);		
				}
				
				else if(a.taskStatus.charAt(i) == REMAINING) {
					if(a.currentCity.distanceTo(allTasks[i].pickupCity) + allTasks[i].pathLength() > longestPatha) 
						longestPatha = a.currentCity.distanceTo(allTasks[i].pickupCity) + allTasks[i].pathLength();		
				}
			}
			
			for(int i= 0 ; i < numTasksb ; i++) {
				
				if(b.taskStatus.charAt(i) == PICKEDUP) {
					if(b.currentCity.distanceTo(allTasks[i].deliveryCity) > longestPathb) 
						longestPathb = b.currentCity.distanceTo(allTasks[i].deliveryCity);		
				}
				
				else if(b.taskStatus.charAt(i) == REMAINING) {
					if(b.currentCity.distanceTo(allTasks[i].pickupCity) + allTasks[i].pathLength() > longestPathb) 
						longestPathb = b.currentCity.distanceTo(allTasks[i].pickupCity) + allTasks[i].pathLength();		
				}
			}
			
			
			double difference = (a.cost +longestPatha*costPerKm) - (b.cost+longestPathb*costPerKm);
			
			if(difference > 0)
				return 1;
			if(difference == 0)
				return 0;
			else
				return -1;
		}
	}

}