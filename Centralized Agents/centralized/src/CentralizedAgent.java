package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
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
public class CentralizedTemplate implements CentralizedBehavior {
	
	private static final int MAXITERATION = 15000;
	private static final int PICKUP = 0;
	private static final int DELIVER = 1;
	private static final double PROBABILITY = 0.4;

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    private List<Vehicle> vehicles = new ArrayList<Vehicle>();
    private List<Task> tasks = new ArrayList<Task>();
    private int numTasks;
    private int numVehicles;
    private int numActions;
    
    public class Strategy {
    	public Action[] nextAction;
    	public int[] time;
    	public int[] vehicle;
    	public int[][] load;
    	
    	public Strategy() {
    		nextAction = new Action[numActions + numVehicles];
    		time = new int[numActions];
    		vehicle = new int[numTasks];
    		load = new int[numVehicles][numActions];
    		
    		for(int i = 0; i < numActions + numVehicles; i++)
    			nextAction[i] = null;
    		
    		for(int i = 0; i < numVehicles; i++) {
    			for(int j= 0; j < numActions; j++)
    				load[i][j] = 0;
    		}
    	}
    	
    	public Strategy(Strategy strategy) {
    		nextAction = new Action[numActions + numVehicles];
    		time = new int[numActions];
    		vehicle = new int[numTasks];
    		load = new int[numVehicles][numActions];
 
    		this.nextAction = strategy.nextAction.clone();
    		this.time = strategy.time.clone();
    		this.vehicle = strategy.vehicle.clone();
    		this.load = strategy.load.clone();
    	}
    }
    
    private class Action {
    	public int task;
    	public int command;
    	public int indexer;
    	
    	public Action(int task, int command, int numTasks) {
    		this.task = task;
    		this.command = command;
    		this.indexer = numTasks * command;
    	}
    	
    	public Action() {
    	}
    	
    	public Action(Action action) {
    		this.task = action.task;
    		this.command = action.command;
    		this.indexer = action.indexer;
    	}
    	
    	@Override
    	public int hashCode() {
    		int code = 0;
    		int factor = 31;
    		
    		code = factor * task + factor * ((command == 0) ? 123:127) + factor * indexer;
    		return code;
    	}
    	
    	@Override
    	public boolean equals(Object obj) {
    		boolean flag = false;
			if (!(obj instanceof Action)) 
			 return false;
			if (this == obj)
				return true;
			Action action = (Action) obj;
			flag = (action != null && this.task == action.task && this.command == action.command && this.indexer == action.indexer);
			return flag;
    	}
    }
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
        this.vehicles.addAll(vehicles);
        this.tasks.addAll(tasks);
        this.numTasks = tasks.size();
        this.numVehicles = vehicles.size();
        this.numActions = 2*numTasks;
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        
        List<Plan> plans = new ArrayList<Plan>();
        Strategy optimalStrategy = new Strategy();
        
        optimalStrategy = stochasticLocalSearch();
        plans.addAll(strategyToPlans(optimalStrategy));
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        int cost = 0;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        for(Plan plan:plans) {
        	System.out.println(""+plan+"");
        	cost += plan.totalDistance()*5;    	
        }
        System.out.println("cost : "+cost+"");
        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
    
    private Strategy stochasticLocalSearch() {
    	Strategy bestStrategy = new Strategy();
    	Strategy oldStrategy = new Strategy();
    	List<Strategy> neighbors = new ArrayList<Strategy>();
    	int iteration = 0;
    	
    	bestStrategy = selectInitialStrategy();
    	
    	do {
    		iteration++;
    		oldStrategy = bestStrategy;
    		neighbors = findNeighbors(oldStrategy);
    		bestStrategy = localChoice(neighbors);
    	}while(iteration < MAXITERATION);
    	
    	return bestStrategy;
    }
    
    //
    private Strategy selectInitialStrategy() {
    	Strategy initialStrategy = new Strategy();
    	int k = 0, j = 1;
    	boolean deliveryNeeded = false;
    	int[] lastAction = new int[numVehicles];
    	
    	for(int i = 0; i < numTasks; i++) {
    		if(i < numVehicles) {
    			initialStrategy.nextAction[numActions+i] = new Action(i, PICKUP, numVehicles);
    			initialStrategy.nextAction[i] = new Action(i, DELIVER, numTasks);
    			initialStrategy.nextAction[numTasks + i] = null;
    			initialStrategy.time[i] = 0;
    			initialStrategy.time[numTasks + i] = 1;
    			initialStrategy.vehicle[i] = i;
    			lastAction[i] = numTasks + i;
    		}
    		else {
    			initialStrategy.nextAction[lastAction[i % numVehicles]] = new Action(i, PICKUP, numVehicles);
    			initialStrategy.nextAction[i] = new Action(i, DELIVER, numTasks);
    			initialStrategy.nextAction[numTasks + i] = null;
    			initialStrategy.time[i] = initialStrategy.time[lastAction[i % numVehicles]] + 1;
    			initialStrategy.time[numTasks + i] = initialStrategy.time[i] + 1;
    			initialStrategy.vehicle[i] = i % numVehicles;
    			lastAction[i % numVehicles] = numTasks + i;
    		}
    	}
    	
    	for(int i = 0; i < numVehicles; i++) {
			for(int t = 0; t < numActions; t++)
				initialStrategy.load[i][t] = 0;
    	}
    	
    	for(int i = 0; i < numVehicles; i++)
    		updateLoad(initialStrategy, i);
    	
    	return initialStrategy;
    }
    
    private List<Strategy> findNeighbors(Strategy oldStrategy){
    	List<Strategy> neighbors = new ArrayList<Strategy>();
    	Strategy newStrategy = new Strategy();
    	Strategy tempStrategy = new Strategy(oldStrategy);
    	Action action = new Action();
    	int selectedVehicle = 0, planLength = 0;
    	
    	neighbors.add(oldStrategy);

    	Random random = new Random();
    	selectedVehicle = random.nextInt(numVehicles-1);
    	
    	while(oldStrategy.nextAction[numActions + selectedVehicle] == null) {
    		selectedVehicle = random.nextInt(numVehicles);
    	}
    	
    	for(int v = 0; v < numVehicles; v++) {
    		if(v != selectedVehicle) {
	    		if(oldStrategy.load[selectedVehicle][0] <= vehicles.get(v).capacity()) {
	    			tempStrategy = new Strategy(oldStrategy);
	    			
	    			for(int i = 0; i < numTasks; i++) {
		    			newStrategy = changeVehicle(tempStrategy, selectedVehicle, v);
		    			if(verifyConstraints(newStrategy)) {
		    				neighbors.add(newStrategy);
		    				tempStrategy = new Strategy(newStrategy);
		    			}
		    			else
		    				break;
	    			}
	    		}
    		}
    	}
    	
    	planLength = 0;
    	action = oldStrategy.nextAction[numActions + selectedVehicle];
    	
    	do {
    		action = oldStrategy.nextAction[action.indexer + action.task];
    		planLength++;
    		
    	}while(action != null);

    	if(planLength >= 2) {
    		for(int i = 1; i < planLength-1; i++) {
    			for(int j = i+1; j < planLength; j++) {
    				newStrategy = changeActionOrder(oldStrategy, selectedVehicle, i, j);
    				
    				if(verifyConstraints(newStrategy)) {
    					neighbors.add(newStrategy);
    				}
    			}
    		}
    	}

    	return neighbors;
    }
    
    private Strategy changeVehicle(Strategy strategy, int vehicle1, int vehicle2) {
    	Strategy newStrategy = new Strategy(strategy);
    	Action actionP = new Action();
    	Action actionD = new Action();
    	Action newFirstAction1 = new Action();
    	Action oldFirstAction2 = new Action();
    	Action secondAction = new Action();
		Action nextActionD = new Action();
		Action prevActionD = new Action();
		Action tempAction = new Action();
		int count = 0;
    	
    	actionP = newStrategy.nextAction[numActions + vehicle1];
    	if(actionP == null)
    		return null;
    	
    	actionD = new Action(actionP.task, DELIVER, numTasks);
    	
    	if(!newStrategy.nextAction[actionP.indexer + actionP.task].equals(actionD)) {
			secondAction = newStrategy.nextAction[actionP.indexer + actionP.task];
			nextActionD = newStrategy.nextAction[actionD.indexer + actionD.task];
			tempAction = newStrategy.nextAction[numActions + vehicle1];
			
			while(!tempAction.equals(actionD)) {
				prevActionD = tempAction;
				tempAction = newStrategy.nextAction[tempAction.indexer + tempAction.task];
			}
	
			newStrategy.nextAction[actionD.indexer + actionP.task] = secondAction;
			newStrategy.nextAction[actionP.indexer + actionP.task] = actionD;
			newStrategy.nextAction[prevActionD.indexer + prevActionD.task] = nextActionD;
		}

    	oldFirstAction2 = newStrategy.nextAction[numActions + vehicle2];
    	newFirstAction1 = newStrategy.nextAction[actionD.indexer + actionD.task];
    	
    	newStrategy.nextAction[numActions + vehicle1] = newFirstAction1;
    	newStrategy.nextAction[actionD.indexer + actionD.task] = oldFirstAction2;
    	newStrategy.nextAction[actionP.indexer + actionP.task] = actionD;
    	newStrategy.nextAction[numActions + vehicle2] = actionP;
    	
		updateTime(newStrategy, vehicle1);
		updateTime(newStrategy, vehicle2);

		updateLoad(newStrategy, vehicle1);
		updateLoad(newStrategy, vehicle2);

		newStrategy.vehicle[actionP.task] = vehicle2;

    	return newStrategy;
    }
    
    private Strategy changeActionOrder(Strategy strategy, int vehicle, int actionIdx1, int actionIdx2) {
    	Strategy newStrategy = new Strategy(strategy);
    	Action action1 = new Action();
    	Action action2 = new Action();
    	Action previousAction1 = new Action();
    	Action previousAction2 = new Action();
    	Action nextAction1 = new Action();
    	Action nextAction2 = new Action();
    	int count = 0;
    	
    	previousAction1 = null;
    	action1 = newStrategy.nextAction[numActions + vehicle];
    	count = 1;
    	
    	while(count < actionIdx1) {
    		previousAction1 = action1;
    		action1 = newStrategy.nextAction[action1.indexer + action1.task];
    		count++;
    	}
    	
    	nextAction1 = newStrategy.nextAction[action1.indexer + action1.task];
    	previousAction2 = action1;
    	action2 = newStrategy.nextAction[previousAction2.indexer + previousAction2.task];
    	count++;
    	
    	while(count < actionIdx2) {
    		previousAction2 = action2;
    		action2 = newStrategy.nextAction[action2.indexer + action2.task];
    		count++;
    	}
    	
    	nextAction2 = newStrategy.nextAction[action2.indexer + action2.task];
    	
    	if(nextAction1.equals(action2)) {
    		if(previousAction1 != null)
        		newStrategy.nextAction[previousAction1.indexer + previousAction1.task] = action2;
    		else
        		newStrategy.nextAction[numActions + vehicle] = action2;
    		
    		newStrategy.nextAction[action2.indexer + action2.task] = action1;
    		newStrategy.nextAction[action1.indexer + action1.task] = nextAction2;
    	}
    	else {
    		if(previousAction1 != null)
        		newStrategy.nextAction[previousAction1.indexer + previousAction1.task] = action2;
        	else
        		newStrategy.nextAction[numActions + vehicle] = action2;
    		
    		newStrategy.nextAction[previousAction2.indexer + previousAction2.task] = action1;
    		newStrategy.nextAction[action2.indexer + action2.task] = nextAction1;
    		newStrategy.nextAction[action1.indexer + action1.task] = nextAction2;
    	}
    	
    	updateTime(newStrategy, vehicle);
    	updateLoad(newStrategy, vehicle);
    	
    	return newStrategy;
    }
    
    private void updateTime(Strategy strategy, int vehicle) {
    	Action action1 = new Action();
    	Action action2 = new Action();
    	
    	action1 = strategy.nextAction[numActions + vehicle];
    	
    	if( action1 != null) {
    		strategy.time[action1.indexer + action1.task] = 0;
    		action2 = strategy.nextAction[action1.indexer + action1.task];
    		
			do {
    			action2 = strategy.nextAction[action1.indexer + action1.task];
    			if(action2 != null ) {
    				strategy.time[action2.indexer + action2.task] = strategy.time[action1.indexer + action1.task] + 1;
    				action1 = action2;
    			}
    			
    		}while(action2 != null);
    	}
    }
    
    private void updateLoad(Strategy strategy, int vehicle) {
    	Action action1 = new Action();
    	Action action2 = new Action();
    	int time = 0;
    	
    	action1 = strategy.nextAction[numActions + vehicle];
    	
    	
    	
    	if(action1 != null) {
    		if(action1.command == PICKUP) 
    			strategy.load[vehicle][0] = tasks.get(action1.task).weight ;
   
    		else if(action1.command == DELIVER) 
    			strategy.load[vehicle][0] = -1*tasks.get(action1.task).weight ;
    		
    		action2 = strategy.nextAction[action1.indexer + action1.task];
    		
    		do {
    			action2 = strategy.nextAction[action1.indexer + action1.task];
    			time++;

    			if(action2 != null) {
    				if(action2.command == PICKUP) 
    					strategy.load[vehicle][time] = strategy.load[vehicle][time-1] + tasks.get(action2.task).weight ;
    				
    				else if(action2.command == DELIVER) 
    					strategy.load[vehicle][time] = strategy.load[vehicle][time-1] - tasks.get(action2.task).weight ;
    				
    				action1 = action2;    				
    			}
    		}while(action2 != null);
    	}
     }
    
    private boolean verifyConstraints(Strategy strategy) {
    	boolean verified = true;
    	Action action = new Action();
    	Action nextAction = new Action();
    	
    	if(strategy == null)
    		return false;
    	
    	for(int v = 0; v < numVehicles; v++) {
    		action = strategy.nextAction[numActions + v];
    		
    		if(action != null) {
    			if(strategy.vehicle[action.task] != v) {
	    			//System.out.println("test-verifyConstraints ERROR : vehicle mismatch");
	    			return false;
	    		}
	    		
	    		if(action.command != PICKUP) {
		    		//System.out.println("test-verifyConstraints ERROR : first action not pickup");
					return false;
				}
	    		
	    		if(strategy.time[action.indexer + action.task] != 0) {
					//System.out.println("test-verifyConstraints ERROR : first time not 0");
					return false;
				}
	    		
	    		nextAction = strategy.nextAction[numActions + v];
	    		
    			do {
    				action = nextAction;
    				nextAction = strategy.nextAction[action.indexer + action.task];
		    		
		    		if(nextAction == null) {
		    			if(action.command != DELIVER) {
		    				//System.out.println("test-verifyConstraints ERROR : last action not deliver");
		    				return false;
		    			}
		    		}
		    		else {
			    		if(nextAction.equals(action)) {
			    			//System.out.println("test-verifyConstraints ERROR : nextAction = action");
			    			return false;
			    		}
			    		
			    		if(strategy.vehicle[nextAction.task] != strategy.vehicle[action.task]) {
			    			//System.out.println("test-verifyConstraints ERROR : vehicle mismatch between action and nextAction");
			    			return false;
			    		}
			    		
			    		if(strategy.time[nextAction.indexer + nextAction.task] != (strategy.time[action.indexer + action.task] + 1)) {
			    			//System.out.println("test-verifyConstraints ERROR : time(nextAction) != time(action) + 1");
			    			return false;
			    		}
			    		
			    		if(nextAction.command == DELIVER) {
			    			if(strategy.time[nextAction.task] > strategy.time[action.indexer + action.task]) {
			    			//	System.out.println("test-verifyConstraints ERROR : delivery before pickup");
			    				return false;
			    			}
			    		}
			    		
			    		if(strategy.load[v][strategy.time[nextAction.indexer + nextAction.task]] > vehicles.get(v).capacity()) {
			    			//System.out.println("test-verifyConstraints ERROR : overload");
			    			return false;
			    		}
		    		}
    			}while(nextAction != null);
    		}
    	}
    	
    	for(int i = 0; i < numTasks; i++) {
    		if(strategy.time[i] > strategy.time[numTasks + i]) {
    			return false;
    		}
    	}
    	
    	return verified;
    }
    
    private Strategy localChoice(List<Strategy> neighbors) {
    	Strategy chosenNeighbor = new Strategy();
    	Strategy oldStrategy = neighbors.get(0);
    	int minCost = 999999;
    	double randomNumber = 0;
    	int cost = 0 ;
    	
    	neighbors.remove(0);
    	
    	for(Strategy neighbor : neighbors) {
    		cost = 0 ;
    		for(int i = 0; i < numActions; i++) {
    			if(neighbor.nextAction[i] != null) {
	    			if(i < numTasks && neighbor.nextAction[i].command == PICKUP ) {
	    				cost += tasks.get(i).pickupCity.distanceTo(tasks.get(neighbor.nextAction[i].task).pickupCity) 
	    						* vehicles.get(neighbor.vehicle[i]).costPerKm();
	    			}
	    			else if(i < numTasks && neighbor.nextAction[i].command == DELIVER ) {
	    				cost += tasks.get(i).pickupCity.distanceTo(tasks.get(neighbor.nextAction[i].task).deliveryCity) 
	    						* vehicles.get(neighbor.vehicle[i]).costPerKm();
	    			}
	    			else if(i >= numTasks && neighbor.nextAction[i].command == PICKUP ) {
	    				cost += tasks.get(i-numTasks).deliveryCity.distanceTo(tasks.get(neighbor.nextAction[i].task).pickupCity) 
	    						* vehicles.get(neighbor.vehicle[i-numTasks]).costPerKm();
	    			}
	    			else if(i >= numTasks && neighbor.nextAction[i].command == DELIVER ) {
	    				cost += tasks.get(i-numTasks).deliveryCity.distanceTo(tasks.get(neighbor.nextAction[i].task).deliveryCity) 
	    						* vehicles.get(neighbor.vehicle[i-numTasks]).costPerKm();
	    			}	
    			}
    		}
    		
    		for(int i = 0; i < numVehicles ; i++) {
    			if(neighbor.nextAction[numActions + i] != null)
    				cost += vehicles.get(i).homeCity().distanceTo(tasks.get(neighbor.nextAction[numActions + i].task).pickupCity) * vehicles.get(i).costPerKm();
    		}
    		
    		if(cost < minCost) {
    			minCost = cost;
    			chosenNeighbor = neighbor ;
    		}
    	}
    	
    	Random random = new Random();
    	randomNumber = random.nextDouble();

    	if(randomNumber > PROBABILITY) {
    		chosenNeighbor = oldStrategy;
    	}

    	return chosenNeighbor;
    }

	private List<Plan> strategyToPlans(Strategy strategy){
		List<Plan> plans = new ArrayList<Plan>();
		Plan plan;
		Action action1 = new Action();
		Action action2 = new Action();
		int counter = 0;
		
		for( int i = 0 ; i < numVehicles ; i++) {
			plan = new Plan(vehicles.get(i).homeCity());
			action1 = strategy.nextAction[numActions + i];
			
			if(action1 != null) {
				
				for(City city : vehicles.get(i).homeCity().pathTo(tasks.get(action1.task).pickupCity))
					plan.appendMove(city);
				
				plan.appendPickup(tasks.get(action1.task));
				counter++;
				
				action2 = strategy.nextAction[action1.indexer + action1.task];
				
				do {
					action2 = strategy.nextAction[action1.indexer + action1.task];
					if(action2 != null) {
						if(action1.command == PICKUP && action2.command == PICKUP) {
							for(City city : tasks.get(action1.task).pickupCity.pathTo(tasks.get(action2.task).pickupCity))
								plan.appendMove(city);
						
							plan.appendPickup(tasks.get(action2.task));
							counter++;
						}
					
						else if(action1.command == PICKUP && action2.command == DELIVER) {
							for(City city : tasks.get(action1.task).pickupCity.pathTo(tasks.get(action2.task).deliveryCity))
								plan.appendMove(city);
						
							plan.appendDelivery(tasks.get(action2.task));
							counter++;
						}
					
						else if(action1.command == DELIVER && action2.command == PICKUP) {
							for(City city : tasks.get(action1.task).deliveryCity.pathTo(tasks.get(action2.task).pickupCity))
								plan.appendMove(city);
						
							plan.appendPickup(tasks.get(action2.task));
							counter++;
						}
					
						else if(action1.command == DELIVER && action2.command == DELIVER) {
							for(City city : tasks.get(action1.task).deliveryCity.pathTo(tasks.get(action2.task).deliveryCity))
								plan.appendMove(city);
						
							plan.appendDelivery(tasks.get(action2.task));
							counter++;
						}
					
						action1 = action2;
					}
				}while(action2 != null);
			}
			
			plans.add(plan);		
		}
		
		return plans;
	}
}
