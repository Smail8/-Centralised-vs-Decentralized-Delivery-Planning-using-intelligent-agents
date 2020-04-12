package template;

import java.util.Random;
import java.util.List;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;


public class ReactiveTemplate implements ReactiveBehavior {

	private Random random;
	private double pPickup;
	private int numActions;
	private int numberOfActions;	
	private Agent myAgent;
	private int numStates;
	private ActionType[] best;
	ActionType[] actionsList;
	State[] statesList;
	
	
	private class State {
		public City currentCity;
		public boolean task;
		public City deliveryCity;
		
		
		public boolean egal(State state) {
			boolean isEqual = false;
			if(this.currentCity == state.currentCity && this.task == state.task && this.deliveryCity == state.deliveryCity)
				isEqual = true;
			return isEqual;
		}
	}
	
	private class ActionType {
		public boolean decision;
		public City destination;
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		
		Double discount = agent.readProperty("discount-factor", Double.class,
				0.95);
		
		double discountFac = (double)discount;
		
		this.random = new Random();
		this.pPickup = discount;
		this.numActions = topology.size()+1;
		this.myAgent = agent;
		this.numStates = topology.size()*topology.size();
		
		List<City> cities;
		cities = topology.cities();
		
		
		//Declare and initialize states table
		statesList = new State[numStates];
		
		for(int i = 0; i < numStates; i++) 
			statesList[i] = new State();
		
		for(int n=0; n < topology.size(); n++) {
			for(int i = 0; i < topology.size(); i++) {
				if(i==0) {
					statesList[(n*topology.size())+i].currentCity = cities.get(n);
					statesList[(n*topology.size())+i].task = false;
					statesList[(n*topology.size())+i].deliveryCity = null;
				}
				else {
					statesList[(n*topology.size())+i].currentCity = cities.get(n);
					statesList[(n*topology.size())+i].task = true;
					if(i<=n)
						statesList[(n*topology.size())+i].deliveryCity = cities.get(i-1);
					else
						statesList[(n*topology.size())+i].deliveryCity = cities.get(i);
				}
			}
		}
		
		//Declare and initialize actions table
		actionsList = new ActionType[numActions];
		for(int i = 0; i < numActions; i++) 
			actionsList[i] = new ActionType();
		
		actionsList[0].decision = true;
		actionsList[0].destination = null;
		
		for(int i = 1; i < numActions; i++) {
			actionsList[i].decision = false;
			actionsList[i].destination = cities.get(i-1);
		}
		
		
		best = new ActionType[numStates];
		
		//Declare and initialize reward table
		int[][] R = new int[numStates][numActions];
		
		for(int i = 0; i < numStates; i++) {
			for(int j = 0; j < numActions; j++) 
				R[i][j] = 0;
		}
		
		for(int i = 0; i < numStates; i++) {
			for(int j = 0; j < numActions; j++) {
				//task available and taken
				if(statesList[i].task == true && actionsList[j].decision == true) {
					R[i][j] = td.reward(statesList[i].currentCity, statesList[i].deliveryCity)-(int)(statesList[i].currentCity.distanceTo(statesList[i].deliveryCity)*agent.vehicles().get(0).costPerKm()); //reward minus cost of travel
				}
				
				//task unavailable
				if(actionsList[j].decision == false) {
					if(statesList[i].currentCity.hasNeighbor(actionsList[j].destination) == true) {
						R[i][j] = -(int)(statesList[i].currentCity.distanceTo(actionsList[j].destination)*agent.vehicles().get(0).costPerKm()); //cost of travel to that city
					}
				}	
				
			}
		}
		
		//Declare and initialize transition table
		double[][][] T = new double[numStates][numActions][numStates];
		
		for(int i = 0; i <numStates; i++) {
			for(int j = 0; j < numActions; j++) 
				for(int k = 0; k < numStates; k++)
					T[i][j][k] = 0.;
		}
		
		for(int i = 0; i < numStates ; i++) {
			for(int j = 0; j < numActions; j++) {
				for(int k = 0; k < numStates; k++) {
				
					//Task available and taken in current state
					if(statesList[i].task == true && actionsList[j].decision == true && statesList[i].deliveryCity == statesList[k].currentCity) {
						if(statesList[k].task == true ) {
							T[i][j][k]=td.probability(statesList[i].deliveryCity, statesList[k].deliveryCity ); //task available in next state
						}
						
						else {
							T[i][j][k]=td.probability(statesList[i].deliveryCity, null); //no task in next state
						}			
					}
					
					//Task not taken
					if(actionsList[j].decision == false && statesList[i].currentCity.hasNeighbor(actionsList[j].destination) == true && actionsList[j].destination == statesList[k].currentCity ) {
						if(statesList[k].task == true) {
							T[i][j][k]=td.probability(actionsList[j].destination, statesList[k].deliveryCity); //task available in next state
						}
						
						else {
							T[i][j][k]=td.probability(actionsList[j].destination, null); //no task in next state
						}
					}
						
				}
			}	
		}
		
		//This is a test
		// Reads the discount factor from the agents.xml file.
		// If the property is not present it defaults to 0.95
		

		//MDP Algorithm
		double[] V = new double[numStates];
		double[] temp = new double[numStates];
		double[][] Q = new double[numStates][numActions];
		double difference = 0;
		double tolerance = 0.001;
		
		
		for(int s = 0; s < numStates; s++)
			V[s] = 0.;
		
		do {
			for(int s = 0; s < numStates; s++)
				temp[s] = V[s];
			
			for(int s = 0; s < numStates; s++) {
				for(int a = 0; a < numActions; a++) {
					if((statesList[s].currentCity != actionsList[a].destination) && !(!(statesList[s].task) && (actionsList[a].decision)) && !(!(actionsList[a].decision) 
							&& !(statesList[s].currentCity.hasNeighbor(actionsList[a].destination)))) {
						Q[s][a] = (double) R[s][a];
						for(int sp = 0; sp < numStates; sp++) {
							if((actionsList[a].destination == statesList[sp].currentCity)  || (actionsList[a].decision && statesList[s].deliveryCity == statesList[sp].currentCity))
								Q[s][a] += (discountFac*(T[s][a][sp])*V[sp]); 
						}
					}
				}
				for(int a = 0; a < numActions; a++) {
					if((statesList[s].currentCity != actionsList[a].destination) && !(!(statesList[s].task) && (actionsList[a].decision)) && !(!(actionsList[a].decision) 
							&& !(statesList[s].currentCity.hasNeighbor(actionsList[a].destination)))) {
						if(V[s] < Q[s][a])
							V[s] = Q[s][a];
					}
				}	
			}
			
			difference = V[0] - temp[0];
			for(int s = 0; s < numStates; s++) {
				if(difference < V[s] - temp[s])
					difference = V[s] - temp[s];
			}
			
		}while(difference > tolerance);
		
		double newValue = 0., maxValue = -999999.;
		
		for(int s = 0; s < numStates; s++) {
			for(int a = 0; a < numActions; a++) {
				if((statesList[s].currentCity != actionsList[a].destination) && !(!(statesList[s].task) && (actionsList[a].decision)) && !(!(actionsList[a].decision) 
						&& !(statesList[s].currentCity.hasNeighbor(actionsList[a].destination)))) {
					newValue = (double) R[s][a];
					for(int sp = 0; sp < numStates; sp++) {
						if((actionsList[a].destination == statesList[sp].currentCity) || (actionsList[a].decision && statesList[s].deliveryCity == statesList[sp].currentCity)) 
							newValue += (discountFac * T[s][a][sp] * V[s]);
					}
					System.out.println("new value is "+newValue+" for s = "+s+" and a = "+a+"");
					if(newValue > maxValue) {
						best[s] = actionsList[a];		
						maxValue = newValue;
					}
				}
			}
			maxValue = -999999.;
		}
		
		//System.out.println("The reward is "+td.reward(topology.parseCity("Rotterdam"), topology.parseCity("Rotterdam"))+"");
		for(int i = 0; i < numStates; i++) {
			//for(int j = 0; j < numActions; j++) {
				//for(int k = 0; k < numStates; k++) {
				//	if((statesList[i].currentCity != actionsList[j].destination) && !(!(statesList[i].task) && (actionsList[j].decision)) && !(!(actionsList[j].decision) 
						//	&& !(statesList[i].currentCity.hasNeighbor(actionsList[j].destination)))) {
						//if(actionsList[j].destination == statesList[k].currentCity)
							System.out.println("best action in "+i+" is "+best[i].decision+"");
					//}
				//}
			//}
		}
	}

	//@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		Action action;
		State currentState = new State();
		ActionType vehicleAction = new ActionType();
		
		currentState.currentCity = vehicle.getCurrentCity();
		
		if(availableTask == null) {
			currentState.task = false;
			currentState.deliveryCity = null;
		}
		else {
			currentState.task = true;
			currentState.deliveryCity = availableTask.deliveryCity;
		}
		
		vehicleAction = getBestAction(currentState);
		
		if (!vehicleAction.decision) {
			
			action = new Move(vehicleAction.destination);
		} 
		else {
			action = new Pickup(availableTask);
		}
		
		if (numberOfActions >= 1) {
			System.out.println("The total profit after "+numberOfActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numberOfActions)+")");
		}
		numberOfActions++;
		
		return action;
	}
	
	public ActionType getBestAction(State state) {
		int s = 0;
		while(!statesList[s].egal(state))
			s++;
		
		return best[s];

	}
	
}
