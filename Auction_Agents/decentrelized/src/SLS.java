package template;

//the list of imports
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import logist.task.Task;



public class SLS {
	
	private static final int MAXITERATION = 20000;
	private static final int PICKUP = 0;
	private static final int DELIVER = 1;
	private static final double PROBABILITY = 0.4;
	
    private List<VehicleType> vehicles = new ArrayList<VehicleType>();
    private List<Task> tasks = new ArrayList<Task>();
    private int numTasks;
    private int numVehicles;
    private int numActions;
    private Strategy initialStrategy;
    
    public SLS(int nTasks, int nVehicles, List<Task> allTasks, List<VehicleType> allVehicles, Strategy strategy) {
    	this.numTasks = nTasks;
    	this.numVehicles = nVehicles;
    	this.numActions = 2*nTasks;
    	this.tasks.addAll(allTasks);
    	this.vehicles.addAll(allVehicles);
    	this.initialStrategy = new Strategy(strategy, numTasks, numVehicles);
    }
    
  
    public Strategy stochasticLocalSearch() {
    	Strategy bestStrategy = new Strategy(initialStrategy, numTasks, numVehicles);
    	Strategy oldStrategy = new Strategy(numTasks, numVehicles);
    	List<Strategy> neighbors = new ArrayList<Strategy>();
    	int iteration = 0;
    	
    	do {
    		iteration++;
    		oldStrategy = new Strategy(bestStrategy, numTasks, numVehicles);
    		neighbors = findNeighbors(oldStrategy);
    		bestStrategy = localChoice(neighbors);
    	}while(iteration < MAXITERATION);
    	
    	return bestStrategy;
    }
    
    private List<Strategy> findNeighbors(Strategy oldStrategy){
    	List<Strategy> neighbors = new ArrayList<Strategy>();
    	Strategy newStrategy = new Strategy(numTasks, numVehicles);
    	Strategy tempStrategy = new Strategy(oldStrategy, numTasks, numVehicles);
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
	    		if(oldStrategy.load[selectedVehicle][0] <= vehicles.get(v).capacity) {
	    			tempStrategy = new Strategy(oldStrategy, numTasks, numVehicles);
	    			
	    			for(int i = 0; i < numTasks; i++) {
		    			newStrategy = changeVehicle(tempStrategy, selectedVehicle, v);
		    			if(verifyConstraints(newStrategy)) {
		    				neighbors.add(newStrategy);
		    				tempStrategy = new Strategy(newStrategy, numTasks, numVehicles);
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
    	Strategy newStrategy = new Strategy(strategy, numTasks, numVehicles);
    	Action actionP = new Action();
    	Action actionD = new Action();
    	Action newFirstAction1 = new Action();
    	Action oldFirstAction2 = new Action();
    	Action secondAction = new Action();
		Action nextActionD = new Action();
		Action prevActionD = new Action();
		Action tempAction = new Action();
    	
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
    	Strategy newStrategy = new Strategy(strategy, numTasks, numVehicles);
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
			    		
			    		if(strategy.load[v][strategy.time[nextAction.indexer + nextAction.task]] > vehicles.get(v).capacity) {
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
    	Strategy chosenNeighbor = new Strategy(numTasks, numVehicles);
    	//Strategy oldStrategy = neighbors.get(0);
    	double minCost = 999999;
    	double randomNumber = 0;
    	double cost = 0 ;
    	
    	neighbors.remove(0);
    	
    	for(Strategy neighbor : neighbors) {
    		cost = neighbor.computeCost(neighbor.nextAction, numTasks, numVehicles, tasks, vehicles);
    		neighbor.strategyCost = cost;
    		if(cost < minCost) {
    			minCost = cost;
    			chosenNeighbor = neighbor;
    		}
    	}
    	
    	Random random = new Random();
    	randomNumber = random.nextDouble();

    	if(randomNumber > PROBABILITY) {
    		//chosenNeighbor = oldStrategy;
    	}

    	return chosenNeighbor;
    }

}
