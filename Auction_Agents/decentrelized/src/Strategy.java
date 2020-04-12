package template;

import java.util.List;
import java.util.ArrayList;
import logist.task.Task;
import logist.topology.Topology.City;
import logist.plan.Plan;
import logist.simulation.Vehicle;

public class Strategy {
	private static final int PICKUP = 0;
	private static final int DELIVER = 1;
	
	public Action[] nextAction;
	public Action[] previousAction;
	public int[] time;
	public int[] vehicle;
	public int[][] load;
	public double strategyCost;
	
	public Strategy(int numTasks, int numVehicles) {
		int numActions = 2*numTasks;
		this.nextAction = new Action[numActions + numVehicles];
		this.previousAction = new Action[numActions];
		this.time = new int[numActions];
		this.vehicle = new int[numTasks];
		this.load = new int[numVehicles][numActions];
		this.strategyCost = 0;
		
		for(int i = 0; i < numActions + numVehicles; i++)
			this.nextAction[i] = null;
		
		for(int i = 0; i < numActions ; i++)
			this.previousAction[i] = null;
		
		for(int i = 0; i < numVehicles; i++) {
			for(int j= 0; j < numActions; j++)
				this.load[i][j] = 0;
		}
	}
	
	public Strategy(Strategy strategy, int numTasks, int numVehicles) {
		int numActions = 2*numTasks;
		this.nextAction = new Action[numActions + numVehicles];
		this.previousAction = new Action[numActions];
		this.time = new int[numActions];
		this.vehicle = new int[numTasks];
		this.load = new int[numVehicles][numActions];
		
		if(strategy.nextAction.length == numActions + numVehicles) {
			for(int i = numActions; i < numActions + numVehicles; i++) {
				if(strategy.nextAction[i] != null)
					this.nextAction[i] = new Action(strategy.nextAction[i]);
				else
					this.nextAction[i] = null;
			}
			
			for(int i = 0; i < numTasks; i++) {
				if(strategy.nextAction[i] != null)
					this.nextAction[i] = new Action(strategy.nextAction[i]);
				else
					this.nextAction[i] = null;
				
				if(strategy.nextAction[numTasks + i] != null)
					this.nextAction[numTasks + i] = new Action(strategy.nextAction[numTasks + i]);
				else
					this.nextAction[numTasks + i] = null;
				
				if(strategy.previousAction[i] != null)
					this.previousAction[i] = new Action(strategy.previousAction[i]);
				else
					this.previousAction[i] = null;
				
				if(strategy.previousAction[numTasks + i] != null)
					this.previousAction[numTasks + i] = new Action(strategy.previousAction[numTasks + i]);
				else
					this.previousAction[numTasks + i] = null;
				
				this.time[i] = strategy.time[i];
				this.time[numTasks + i] = strategy.time[numTasks + i];
				this.vehicle[i] = strategy.vehicle[i];
				for(int j = 0; j < numVehicles; j++) {
					this.load[j][i] = strategy.load[j][i];
					this.load[j][numTasks + i] = strategy.load[j][numTasks + i];
				}
			}
			
			this.strategyCost = strategy.strategyCost;
		}
		else {
			for(int i = numActions; i < numActions + numVehicles; i++) {
				if(strategy.nextAction[i-2] != null) {
					this.nextAction[i] = new Action(strategy.nextAction[i-2]);
					this.nextAction[i].adabptIndexer(numTasks);
				}
				else
					this.nextAction[i] = null;
			}
			
			for(int i = 0; i < numTasks-1; i++) {
				if(strategy.nextAction[i] != null) {
					this.nextAction[i] = new Action(strategy.nextAction[i]);
					this.nextAction[i].adabptIndexer(numTasks);
				}
				else
					this.nextAction[i] = null;
				
				if(strategy.nextAction[numTasks - 1 + i] != null) {
					this.nextAction[numTasks + i] = new Action(strategy.nextAction[numTasks - 1 + i]);
					this.nextAction[numTasks + i].adabptIndexer(numTasks);
				}
				else
					this.nextAction[numTasks + i] = null;
				
				if(strategy.previousAction[i] != null) {
					this.previousAction[i] = new Action(strategy.previousAction[i]);
					this.previousAction[i].adabptIndexer(numTasks);
				}
				else
					this.previousAction[i] = null;
				
				if(strategy.previousAction[numTasks - 1 + i] != null) {
					this.previousAction[numTasks + i] = new Action(strategy.previousAction[numTasks - 1 + i]);
					this.previousAction[numTasks + i].adabptIndexer(numTasks);
				}
				else
					this.previousAction[numTasks + i] = null;
				
				this.time[i] = strategy.time[i];
				this.time[numTasks + i] = strategy.time[numTasks - 1 + i];
				this.vehicle[i] = strategy.vehicle[i];
				for(int j = 0; j < numVehicles; j++) {
					this.load[j][i] = strategy.load[j][i];
					this.load[j][numTasks + i] = strategy.load[j][numTasks - 1  + i];
				}
				this.strategyCost = 0;
			}
		}
	}
	
	/*public void updateStrategy(int numTasks, List<Task> tasks, int vehicleId) {
		int numActions = 2*numTasks;
		
		if(nextAction[numActions + vehicleId] == null) {
			nextAction[numActions + vehicleId] = new Action(numTasks - 1, PICKUP, numTasks);
			time[numTasks - 1] = 0;
		}
 		
		else {
			Action currentAction = new Action();
			Action lastAction = new Action();
			
			currentAction = nextAction[numActions + vehicleId];
			do {
				lastAction = currentAction;
				currentAction = nextAction[currentAction.indexer + currentAction.task];
			}while(currentAction != null);
			
			nextAction[lastAction.indexer + lastAction.task] = new Action(numTasks - 1, PICKUP, numTasks);
			time[numTasks - 1] = time[lastAction.indexer + lastAction.task];
		}
		
		nextAction[numTasks - 1] = new Action(numTasks - 1, DELIVER, numTasks);
		nextAction[numActions - 1] = null;
		time[numActions - 1] = time[numTasks - 1] + 1;
		vehicle[numTasks - 1] = vehicleId;
		updateLoad(numTasks, tasks, vehicleId);
	}*/
	
	
	
	public void updateStrategy(int numTasks, int numVehicles, List<Task> tasks, List<VehicleType> vehicles) {
		int numActions = 2*numTasks;
		Action[] nextAct = new Action[numActions + numVehicles];
		Action[] bestNextAction = new Action[numActions + numVehicles];
		Action[] bestPrevAction = new Action[numActions];
		Action[] prevAct = new Action[numActions];
		Action[] nextActP = new Action[numActions + numVehicles];
		Action[] nextActD = new Action[numActions+ numVehicles];
		Action[] prevActP = new Action[numActions];
		Action[] prevActD = new Action[numActions];
		Action currentAction = new Action();
		Action lastAction = new Action();
		Action pickupAction = new Action();
		Action deliveryAction = new Action();
		Action beforeAction = new Action();
		Action afterAction = new Action();
		Action bebeforeAction = new Action();

		
		int bestVehicle = 0;
		double minCost = 999999;
		double cost = 0;
		
		for(int v = 0; v < numVehicles; v++) {
			nextAct = this.nextAction.clone();
			prevAct = this.previousAction.clone();
			
			if(nextAct[numActions + v] == null) {
				nextAct[numActions + v] = new Action(numTasks - 1, PICKUP, numTasks);
				prevAct[numTasks - 1] = null;
			}
	 		
			else {
				currentAction = nextAct[numActions + v];
				do {
					lastAction = currentAction;
					currentAction = nextAct[currentAction.indexer + currentAction.task];
				}while(currentAction != null);
				
				nextAct[lastAction.indexer + lastAction.task] = new Action(numTasks - 1, PICKUP, numTasks);
				prevAct[numTasks - 1] = lastAction;
			}
			nextAct[numTasks - 1] = new Action(numTasks - 1, DELIVER, numTasks);
			nextAct[numActions - 1] = null;
			prevAct[numActions - 1] = new Action(numTasks - 1, PICKUP, numTasks);
			if(verifyLoad(nextAct, numVehicles, numTasks, vehicles, tasks)) {
				cost = computeCost(nextAct, numTasks, numVehicles, tasks, vehicles);
				if(cost < minCost) {
					minCost = cost;
					bestNextAction = nextAct.clone();
					bestPrevAction = prevAct.clone();
					bestVehicle = v;
				}
			}
			
			//print(nextAct, "nextAct", numTasks);
			//System.out.println(" ++++++++++++++++++++++++++++++++++++ ");
			//print(prevAct, "prevAct", numTasks);
			//System.out.println(" ------------------------------------ ");
			//System.out.println(" ------------------------------------ ");
			pickupAction = new Action(numTasks - 1, PICKUP, numTasks);
			deliveryAction = new Action(numTasks - 1, DELIVER, numTasks);
			
			prevActP = prevAct.clone();
			nextActP = nextAct.clone();
			
			while(prevActP[pickupAction.indexer + pickupAction.task] != null) {
				beforeAction = prevActP[pickupAction.indexer + pickupAction.task];
				bebeforeAction = prevActP[beforeAction.indexer + beforeAction.task];
				afterAction = nextActP[pickupAction.indexer + pickupAction.task];
				
				if(bebeforeAction != null)
					nextActP[bebeforeAction.indexer + bebeforeAction.task] = pickupAction;
				else
					nextActP[numActions + v] = pickupAction;
				
				nextActP[beforeAction.indexer + beforeAction.task] = afterAction;
				nextActP[pickupAction.indexer + pickupAction.task] = beforeAction;
				prevActP[beforeAction.indexer + beforeAction.task] = pickupAction;
				prevActP[pickupAction.indexer + pickupAction.task] = bebeforeAction;
				prevActP[afterAction.indexer + afterAction.task] = beforeAction;
				
				
				//print(nextActP, ">>>> nextActP", numTasks);
				//System.out.println(" ++++++++++++++++++++++++++++++++++++ ");
				//print(prevActP, ">>>> prevActP", numTasks);
				//System.out.println(" ------------------------------------ ");
				//System.out.println("test1");
				if(verifyLoad(nextActP, numVehicles, numTasks, vehicles, tasks)) {
					cost = computeCost(nextActP, numTasks, numVehicles, tasks, vehicles);
					//System.out.println("test2");
					if(cost < minCost) {
						minCost = cost;
						bestNextAction = nextActP.clone();
						bestPrevAction = prevActP.clone();
						bestVehicle = v;
					}
				}
				prevActD = prevActP.clone();
				nextActD = nextActP.clone();
				
				while(!prevActD[deliveryAction.indexer + deliveryAction.task].equals(pickupAction)) {
					beforeAction = prevActD[deliveryAction.indexer + deliveryAction.task];
					bebeforeAction = prevActD[beforeAction.indexer + beforeAction.task];
					afterAction = nextActD[deliveryAction.indexer + deliveryAction.task];
					
					nextActD[bebeforeAction.indexer + bebeforeAction.task] = deliveryAction;
					nextActD[beforeAction.indexer + beforeAction.task] = afterAction;
					nextActD[deliveryAction.indexer + deliveryAction.task] = beforeAction;
					prevActD[beforeAction.indexer + beforeAction.task] = deliveryAction;
					prevActD[deliveryAction.indexer + deliveryAction.task] = bebeforeAction;
					if(afterAction != null)
						prevActD[afterAction.indexer + afterAction.task] = beforeAction;
					
				//	print(nextActD, " >>>>>>>>>> nextActD", numTasks);
					//System.out.println(" ++++++++++++++++++++++++++++++++++++ ");
					//print(prevActD, " >>>>>>>>>> prevActD", numTasks);
					//System.out.println(" ------------------------------------ ");
					if(verifyLoad(nextActD, numVehicles, numTasks, vehicles, tasks)) {
						cost = computeCost(nextActD, numTasks, numVehicles, tasks, vehicles);
						if(cost < minCost) {
							minCost = cost;
							bestNextAction = nextActD.clone();
							bestPrevAction = prevActD.clone();
							bestVehicle = v;
						}
					}
				}
			}
		}
		nextAction = bestNextAction.clone();
		previousAction = bestPrevAction.clone();
		vehicle[numTasks - 1] = bestVehicle;
		strategyCost = minCost;
		updateTime(numTasks, numVehicles);
		updateLoad(numTasks, numVehicles, tasks);
	}
	
	
	//need to compute all combination each time
	public double computeCost(Action[] nextAct, int numTasks, int numVehicles, List<Task> tasks, List<VehicleType> vehicles) {
		double cost = 0;
		int numActions = 2*numTasks;
		Action action = new Action();
		Action previousAction = new Action();
		
		for(int v = 0; v < numVehicles; v++) {
			action = nextAct[numActions + v];
			if(action != null)
				cost += vehicles.get(v).homeCity.distanceTo(tasks.get(action.task).pickupCity)
						* vehicles.get(v).costPerKm;
			else
				continue;
			 
			previousAction = action;
			action = nextAct[action.indexer + action.task];

			while(action != null) {
				if(previousAction.command == PICKUP && action.command == PICKUP) {
    				cost += tasks.get(previousAction.task).pickupCity.distanceTo(tasks.get(action.task).pickupCity) 
    						* vehicles.get(v).costPerKm;
    			}
    			else if(previousAction.command == PICKUP && action.command == DELIVER) {
    				cost += tasks.get(previousAction.task).pickupCity.distanceTo(tasks.get(action.task).deliveryCity) 
    						* vehicles.get(v).costPerKm;
    			}
    			else if(previousAction.command == DELIVER && action.command == PICKUP) {
    				cost += tasks.get(previousAction.task).deliveryCity.distanceTo(tasks.get(action.task).pickupCity) 
    						* vehicles.get(v).costPerKm;
    			}
    			else if(previousAction.command == DELIVER && action.command == DELIVER) {
    				cost += tasks.get(previousAction.task).deliveryCity.distanceTo(tasks.get(action.task).deliveryCity) 
    						* vehicles.get(v).costPerKm;
    			}	
				previousAction = action;
				action = nextAct[action.indexer + action.task];
			}
		}
		//System.out.println(" cost = "+cost+"");
		return cost;
	}
	
	private void updateLoad(int numTasks,int numVehicles, List<Task> tasks) {
		Action action1 = new Action();
    	Action action2 = new Action();
    	int numActions = 2*numTasks;
    	int time = 0;
    	
    	for(int vehicle = 0; vehicle < numVehicles; vehicle++) {
	    	action1 = nextAction[numActions + vehicle];
	    	
	    	if(action1 != null) {
	    		if(action1.command == PICKUP) 
	    			load[vehicle][0] = tasks.get(action1.task).weight;
	   
	    		else if(action1.command == DELIVER) 
	    			load[vehicle][0] = -1*tasks.get(action1.task).weight;
	    		
	    		action2 = nextAction[action1.indexer + action1.task];
	    		
	    		do {
	    			action2 = nextAction[action1.indexer + action1.task];
	    			time++;
	
	    			if(action2 != null) {
	    				if(action2.command == PICKUP) 
	    					load[vehicle][time] = load[vehicle][time-1] + tasks.get(action2.task).weight;
	    				
	    				else if(action2.command == DELIVER) 
	    					load[vehicle][time] = load[vehicle][time-1] - tasks.get(action2.task).weight;
	    				
	    				action1 = action2;    				
	    			}
	    		}while(action2 != null);
	    	}
    	}
	}
	
	private void updateTime(int numTasks, int numVehicles) {
    	Action action1 = new Action();
    	Action action2 = new Action();
    	int numActions = 2*numTasks;
    	
    	for(int vehicle = 0; vehicle < numVehicles; vehicle++) {
	    	action1 = nextAction[numActions + vehicle];
	    	
	    	if( action1 != null) {
	    		time[action1.indexer + action1.task] = 0;
	    		action2 = nextAction[action1.indexer + action1.task];
	    		
				do {
	    			action2 = nextAction[action1.indexer + action1.task];
	    			if(action2 != null ) {
	    				time[action2.indexer + action2.task] = time[action1.indexer + action1.task] + 1;
	    				action1 = action2;
	    			}
	    			
	    		}while(action2 != null);
	    	}
    	}
    }
	//pas optimal
	private boolean verifyLoad(Action[] nextAct, int numVehicles, int numTasks, List<VehicleType> vehicles, List<Task> tasks) {
		int load = 0;
		int numActions = 2*numTasks;
		Action action = new Action();
		
		for(int v = 0; v < numVehicles; v++) {
			load = 0;
			action = nextAct[numActions + v];
			while(action != null) {
				if(action.command == PICKUP)
					load += tasks.get(action.task).weight;
				else
					load -= tasks.get(action.task).weight;
				
				if(load > vehicles.get(v).capacity)
					return false;
				
				action = nextAct[action.indexer + action.task];
			}
			
		}
		
		return true;
	}
	
	public List<Plan> strategyToPlans(int numVehicles,int numTasks, List<Vehicle> vehicles, List<Task> tasks){
		List<Plan> plans = new ArrayList<Plan>();
		Plan plan;
		Action action1 = new Action();
		Action action2 = new Action();
		int numActions = 2*numTasks;

		for( int i = 0 ; i < numVehicles ; i++) {
			plan = new Plan(vehicles.get(i).homeCity());
			action1 = nextAction[numActions + i];

			if(action1 != null) {

				for(City city : vehicles.get(i).homeCity().pathTo(tasks.get(action1.task).pickupCity))
					plan.appendMove(city);

				plan.appendPickup(tasks.get(action1.task));
				
				action2 = nextAction[action1.indexer + action1.task];
				
				do {

					action2 = nextAction[action1.indexer + action1.task];
					if(action2 != null) {
						if(action1.command == PICKUP && action2.command == PICKUP) {
							for(City city : tasks.get(action1.task).pickupCity.pathTo(tasks.get(action2.task).pickupCity))
								plan.appendMove(city);
						
							plan.appendPickup(tasks.get(action2.task));
						}
					
						else if(action1.command == PICKUP && action2.command == DELIVER) {
							for(City city : tasks.get(action1.task).pickupCity.pathTo(tasks.get(action2.task).deliveryCity))
								plan.appendMove(city);
						
							plan.appendDelivery(tasks.get(action2.task));
						}
					
						else if(action1.command == DELIVER && action2.command == PICKUP) {
							for(City city : tasks.get(action1.task).deliveryCity.pathTo(tasks.get(action2.task).pickupCity))
								plan.appendMove(city);
						
							plan.appendPickup(tasks.get(action2.task));
						}
					
						else if(action1.command == DELIVER && action2.command == DELIVER) {
							for(City city : tasks.get(action1.task).deliveryCity.pathTo(tasks.get(action2.task).deliveryCity))
								plan.appendMove(city);
						
							plan.appendDelivery(tasks.get(action2.task));
						}
					
						action1 = action2;
					}
				}while(action2 != null);
			}
			plans.add(plan);		
		}
		
		return plans;
	}
	
	public void print(Action[] nextAct, String name, int numTasks, int numVehicles) {
		 /*for(int i = 0; i < nextAct.length; i++) {
			if(i < numTasks) {
				if(nextAct[i] != null) {
					if(nextAct[i].command == PICKUP)
						System.out.println(""+name+"[P"+i+"] = P"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
					else
						System.out.println(""+name+"[P"+i+"] = D"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
				}
				else
					System.out.println(""+name+"[P"+i+"] = null");
			}
			else if(i >= numTasks && i < 2*numTasks){
				if(nextAct[i] != null) {
					if(nextAct[i].command == PICKUP)
						System.out.println(""+name+"[D"+(i-numTasks)+"] = P"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
					else
						System.out.println(""+name+"[D"+(i-numTasks)+"] = D"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
				}
				else
					System.out.println(""+name+"[D"+(i-numTasks)+"] = null");
			}
			else if(i >= 2*numTasks) {
				if(nextAct[i] != null) {
					if(nextAct[i].command == PICKUP)
						System.out.println(""+name+"[V"+(i-2*numTasks)+"] = P"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
					else
						System.out.println(""+name+"[V"+(i-2*numTasks)+"] = D"+nextAct[i].task+" ... indexer ="+nextAct[i].indexer+"");
				}
				else
					System.out.println(""+name+"[V"+(i-2*numTasks)+"] = null");
			}
		}*/
		System.out.println(""+name+" : ");
		Action action;
		for(int i = 0; i < numVehicles; i++) {
			System.out.print("==================> ");
			System.out.print("V"+i+" = ");
			action = nextAct[2*numTasks + i];
			if(action != null) {
				while(action != null) {
					if(action.command == 0)
						System.out.print("P"+action.task+" | ");
					else {
						System.out.print("D"+action.task+"");
						if(nextAct[action.indexer + action.task] != null)
							System.out.print(" | ");	
					}
					
					action = nextAct[action.indexer + action.task];
				}
				System.out.println("");
			}
			else
				System.out.println("null");
		}
		
		
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
}