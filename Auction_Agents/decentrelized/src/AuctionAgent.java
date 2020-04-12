package template;

//the list of imports
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.agent.Agent;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;
import uchicago.src.sim.engine.HomeController;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 * 
 */
@SuppressWarnings("unused")
public class AuctionTemplate implements AuctionBehavior {

	private static final int BID_MAX = 4000;
	
	private Topology topology;
	private TaskDistribution distribution;
	private Agent agent;
	private Random random;
	private int numVehicles;
	private int numCities;
	private int ownNumTasks;
	private int adversNumTasks;
	private int round;
	private int myId;
	private int adversId;
	private boolean bigFlag = true;
	private boolean[] certainty;
	private Strategy ownFinalStrategy;
	private Strategy adversFinalStrategy;
	private Strategy ownTempStrategy;
	private Strategy adversTempStrategy;
	private Task auctionnedTask;
	private List<Task> ownWonTasks;
	private List<Task> adversWonTasks;
	private List<Task> ownTempTasks; 
	private List<Task> adversTempTasks;
	private List<VehicleType> ownVehicles;
	private List<VehicleType> adversVehicles;
	private List<City> cities;
	private List<City> possibleCities;
	private long totalTime;
	private double myMarginalCost = 0;
	private double adversMarginalCost = 0;
	private double adversPredictedBid = 0;
	private int reward = 0;
	private int adversReward = 0 ;															
	private Long[][] allBids = new Long[2][500];   														
	private double[] allAdversRatios = new double[200];
	private long adversLowestBid = 9999999 ;										
	private double adversRatio = 0;
	private boolean wonLastRound = false;
	private int count = 1;
	
	

	@Override
	public void setup(Topology topology, TaskDistribution distribution,
			Agent agent) {
		System.out.println("Setup start");
		Random random = new Random();
		int randomCity;
		
		this.topology = topology;
		this.distribution = distribution;
		this.agent = agent;
		this.myId = agent.id();
		this.ownNumTasks = 0;
		this.adversNumTasks = 0;
		this.cities = new ArrayList<City>();
		this.cities.addAll(topology.cities());
		this.possibleCities = new ArrayList<City>();
		this.numCities = topology.cities().size();
		this.numVehicles = agent.vehicles().size();
		this.ownVehicles = new ArrayList<VehicleType>();
		this.adversVehicles = new ArrayList<VehicleType>();
		this.ownWonTasks = new ArrayList<Task>();
		this.adversWonTasks = new ArrayList<Task>();
		this.ownTempTasks = new ArrayList<Task>();
		this.adversTempTasks = new ArrayList<Task>();
		this.ownTempStrategy = new Strategy(0, numVehicles);
		this.adversTempStrategy = new Strategy(0, numVehicles);
		this.ownFinalStrategy = new Strategy(0, numVehicles);
		this.adversFinalStrategy = new Strategy(0, numVehicles);
		this.round = 0;
		this.certainty = new boolean[numVehicles];
		Arrays.fill(certainty, false);
		
		
		if(myId == 0)
			this.adversId = 1;
		else
			this.adversId = 0;
		
		possibleCities.addAll(cities);
		for(int i = 0; i < numVehicles; i++) {
			this.ownVehicles.add(new VehicleType(agent.vehicles().get(i)));
			this.adversVehicles.add(new VehicleType(agent.vehicles().get(i)));
			possibleCities.remove(agent.vehicles().get(i).homeCity());
		}
		for(int i = 0; i < numVehicles; i++) {
			randomCity = random.nextInt(possibleCities.size()-1);
			adversVehicles.get(i).homeCity = possibleCities.get(randomCity);
			possibleCities.remove(randomCity);
		}
		
	}

	@Override
	public void auctionResult(Task previous, int winner, Long[] bids) {
		
		if(round == 1) {
			for(int i = 0; i < bids.length; i++) {
				if(i != myId) {
					if(bids[i] != null) {
						
						adversId = i;
						break;
					}
				}
			}
			
		}

		
		allBids[0][round-1] = bids[myId];																
		allBids[1][round-1] = bids[adversId];
		if(bids[adversId] > BID_MAX)
			allBids[1][round-1] = (long) BID_MAX;
		if(adversMarginalCost != 0)
			allAdversRatios[round-1] = bids[adversId]/adversMarginalCost;
		else
			allAdversRatios[round-1] = 0;
		
		if(bids[adversId] != null && bids[adversId] < adversLowestBid) {											
			adversLowestBid = bids[adversId];											
		}																				
		
		adversaryPositions(bids[adversId], previous);
		
		if (winner == agent.id()) {
			reward += bids[myId].intValue();
			ownNumTasks++;
			ownWonTasks.add(previous);
			ownFinalStrategy = new Strategy(ownTempStrategy, ownNumTasks, numVehicles);
			wonLastRound = true;
		}
		else {
			adversReward += bids[adversId].intValue();
			adversNumTasks++;
			adversWonTasks.add(previous);
			adversFinalStrategy = new Strategy(adversTempStrategy, adversNumTasks, numVehicles);
			wonLastRound = false;
		}
	}
	
	@Override
	public Long askPrice(Task task) {
		long startTime = System.currentTimeMillis();


		double ratioA = 1;
		double ratioB = 0.9;
		double ratioC = 0.9;
		double ratioD = 0.5;
		double ratioE = 0.5;
		int roundNumber = 4;
		
		double bid = 0;
		double lowerBound = 0;
		double upperBound = 0;
		double random1 = 0;
		double range = 0;
		double profitDifference = 0;

		
		
		round++;
		
		if(round != 1) {
			adversRatio = 0;
			for(int i = 0; i < round-1; i++)
				adversRatio += allAdversRatios[i];
			adversRatio /= (double) (round-1);
	
		}
		
		
		auctionnedTask = task;
		myMarginalCost = ownMarginalCost(task);
		adversMarginalCost = adversaryMarginalCost(task);
		if(round != 1) {
			if(adversMarginalCost != 0)
				adversPredictedBid = adversRatio*0.95*adversMarginalCost;
			else
				adversPredictedBid = adversLowestBid;
		}
		else {
			if(adversMarginalCost != 0)
				adversPredictedBid = adversMarginalCost;
			else
				adversPredictedBid = 150;
		}
		
		
		lowerBound = ratioA * myMarginalCost;	
		upperBound = ratioB * adversMarginalCost * adversRatio;
		range = Math.abs(upperBound - lowerBound) ;
		
		if(lowerBound < upperBound && round != 1) {
			
			if(wonLastRound) {
				if(ratioE < 1) {
					ratioE += (1-ratioE)/2;
				}
			}
			
			if(!wonLastRound) {
				ratioE = 0.5;
			}
			
			bid = lowerBound + range*ratioE + range*(1-ratioE)/2;
		}
		
		
		else if(lowerBound >= upperBound && round != 1) {
			
			bid = ratioC*myMarginalCost;
			
		}
		
		if(bid < adversLowestBid && round != 1)
			bid = Math.max(adversLowestBid -1 , 150);
		
		if( round < roundNumber)
			bid = ratioD * bid;
		
		if(round == 1)
			bid = ratioD*myMarginalCost ;
		
		
		System.out.println("askPrice done");
		long endTime = System.currentTimeMillis();
		totalTime += (endTime - startTime);
		
		return (long) Math.round(bid);
	}
	
	private double ownMarginalCost(Task task) {
		double marginalCost = 0;

		ownTempTasks.clear();
		ownTempTasks.addAll(ownWonTasks);
		ownTempTasks.add(task);
		ownTempStrategy = new Strategy(ownFinalStrategy, ownNumTasks + 1, numVehicles);
		ownTempStrategy.updateStrategy(ownNumTasks + 1, numVehicles, ownTempTasks, ownVehicles);

		if(round == 1) {
			marginalCost = ownTempStrategy.strategyCost;
		}
		else {
			marginalCost = ownTempStrategy.strategyCost - ownFinalStrategy.strategyCost;
		}
		
		return marginalCost;
	}
	
	private double adversaryMarginalCost(Task task) {
		double marginalCost = 0;
		
		adversTempTasks.clear();
		adversTempTasks.addAll(adversWonTasks);
		adversTempTasks.add(task);
		adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
		adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
		
		if(round == 1) {	
			marginalCost = adversTempStrategy.strategyCost;
		}
		else {
			marginalCost = adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost;
		}
		
		return marginalCost;
	}
	
	private void adversaryPositions(Long adversBid, Task task) {
		System.out.println("adversary positions start");
		List<VehicleType> updatedVehicles = new ArrayList<VehicleType>();
		City tempCity;
		VehicleType tempVehicle;
		double marginalCost;
		boolean flag = false;
		int nv = numVehicles;
		int rand;
		int vcl = 0;
		int x = 0;
		Random random = new Random();
		Strategy testStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
		List<Task> testTasks = new ArrayList<Task>();
		testTasks.addAll(adversWonTasks);
		testTasks.add(task);
		
		if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) {
			if(round == 1) {
				for(int v = 0; v < numVehicles; v++) {
					tempVehicle = adversVehicles.get(v);
					updatedVehicles.clear();
					updatedVehicles.add(tempVehicle);
					testStrategy = new Strategy(adversNumTasks + 1, 1);
					testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);
				
					if(testStrategy.strategyCost != 0 && (adversBid > 1.2*0.7*testStrategy.strategyCost && adversBid < 1.2*1.3*testStrategy.strategyCost)) {
						possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
						adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
						rand = random.nextInt(possibleCities.size());
						adversVehicles.get(v).homeCity = possibleCities.get(rand);
						possibleCities.remove(rand);
						flag = true;
						adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
						adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
						certainty[adversTempStrategy.vehicle[0]] = true;
						adversPredictedBid = adversTempStrategy.strategyCost;
						break;
					}
				}
				
				if(!flag) {
					
					for(City city:possibleCities) {
						tempVehicle = new VehicleType(adversVehicles.get(adversTempStrategy.vehicle[0]));
						tempVehicle.homeCity = city;
						updatedVehicles.clear();
						updatedVehicles.add(tempVehicle);
						testStrategy = new Strategy(adversNumTasks + 1, 1);
						testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);
	
						if(testStrategy.strategyCost != 0 && (adversBid > 1.2*0.7*testStrategy.strategyCost && adversBid < 1.2*1.3*testStrategy.strategyCost)) {
							possibleCities.add(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
							adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = city;
							possibleCities.remove(city);
							adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
							adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
							certainty[adversTempStrategy.vehicle[0]] = true;
							adversPredictedBid = adversTempStrategy.strategyCost;
							
							if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) {
								certainty[adversTempStrategy.vehicle[0]] = false;
								while((adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) && nv > 0) {
									nv--;
									for(int v = 0; v < numVehicles; v++) {
										tempVehicle = adversVehicles.get(v);
										updatedVehicles.clear();
										updatedVehicles.add(tempVehicle);
										testStrategy = new Strategy(adversNumTasks + 1, 1);
										testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

										if(testStrategy.strategyCost != 0 && (adversBid > 1.2*0.7*testStrategy.strategyCost && adversBid < 1.2*1.3*testStrategy.strategyCost)) {
											possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
											adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
											rand = random.nextInt(possibleCities.size());
											adversVehicles.get(v).homeCity = possibleCities.get(rand);
											possibleCities.remove(rand);
											flag = true;
											adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
											adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
											certainty[adversTempStrategy.vehicle[0]] = true;
											adversPredictedBid = adversTempStrategy.strategyCost;
											break;
										}
									}
								}
							}
							break;
						}
					}
				}
				else {
					
					nv = numVehicles;
					if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid ){
						certainty[adversTempStrategy.vehicle[0]] = false;
						
						while((adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) && nv > 0) {
							nv--;
							for(int v = 0; v < numVehicles; v++) {
								tempVehicle = adversVehicles.get(v);
								updatedVehicles.clear();
								updatedVehicles.add(tempVehicle);
								testStrategy = new Strategy(adversNumTasks + 1, 1);
								testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

								if(testStrategy.strategyCost != 0 && (adversBid > 1.2*0.7*testStrategy.strategyCost && adversBid < 1.2*1.3*testStrategy.strategyCost)) {
									possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
									adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
									rand = random.nextInt(possibleCities.size());
									adversVehicles.get(v).homeCity = possibleCities.get(rand);
									possibleCities.remove(rand);
									flag = true;
									adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
									adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
									certainty[adversTempStrategy.vehicle[0]] = true;
									adversPredictedBid = adversTempStrategy.strategyCost;
									break;
								}
							}
						}
					}
				}
			}
			else {
				if(adversNumTasks == 0) {
					for(int v = 0; v < numVehicles; v++) {
						if(!certainty[v]) {
							tempVehicle = adversVehicles.get(v);
							updatedVehicles.clear();
							updatedVehicles.add(tempVehicle);
							testStrategy = new Strategy(adversNumTasks + 1, 1);
							testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

							if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
								possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
								adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
								rand = random.nextInt(possibleCities.size());
								adversVehicles.get(v).homeCity = possibleCities.get(rand);
								possibleCities.remove(rand);
								flag = true;
								adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
								adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
								certainty[adversTempStrategy.vehicle[0]] = true;
								adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
								break;
							}
						}
					}

					if(!flag) {
						if(!certainty[adversTempStrategy.vehicle[0]]) {
							for(City city:possibleCities) {
								tempVehicle = new VehicleType(adversVehicles.get(adversTempStrategy.vehicle[0]));
								tempVehicle.homeCity = city;
								updatedVehicles.clear();
								updatedVehicles.add(tempVehicle);
								testStrategy = new Strategy(adversNumTasks + 1, 1);
								testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);
							
								if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
									possibleCities.add(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
									adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = city;
									possibleCities.remove(city);
									
									adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
									adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
									certainty[adversTempStrategy.vehicle[0]] = true;
									adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
									
									if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) {
										certainty[adversTempStrategy.vehicle[0]] = false;
		
										while((adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) && nv > 0) {
											nv--;
											for(int v = 0; v < numVehicles; v++) {
												tempVehicle = adversVehicles.get(v);
												updatedVehicles.clear();
												updatedVehicles.add(tempVehicle);
												testStrategy = new Strategy(adversNumTasks + 1, 1);
												testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

												if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
													possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
													adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
													rand = random.nextInt(possibleCities.size());
													adversVehicles.get(v).homeCity = possibleCities.get(rand);
													possibleCities.remove(rand);
													
													flag = true;
													adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
													adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
													certainty[adversTempStrategy.vehicle[0]] = true;
													adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
													break;
												}
											}
										}
									}
									break;
								}
							}
						}
						else {
							while(vcl < numVehicles && !flag) {
								for(City city:possibleCities) {
									tempVehicle = new VehicleType(adversVehicles.get(vcl));
									tempVehicle.homeCity = city;
									updatedVehicles.clear();
									updatedVehicles.add(tempVehicle);
									testStrategy = new Strategy(adversNumTasks + 1, 1);
									testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

									if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
										possibleCities.add(adversVehicles.get(vcl).homeCity);
										adversVehicles.get(vcl).homeCity = city;
										possibleCities.remove(city);
										flag = true;
										adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
										adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
										certainty[vcl] = true;
										adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
										
										if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) {
											certainty[vcl] = false;

											while((adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) && nv > 0) {
												nv--;
												for(int v = 0; v < numVehicles; v++) {
													tempVehicle = adversVehicles.get(v);
													updatedVehicles.clear();
													updatedVehicles.add(tempVehicle);
													testStrategy = new Strategy(adversNumTasks + 1, 1);
													testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

													if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
														possibleCities.remove(adversVehicles.get(vcl).homeCity);
														adversVehicles.get(vcl).homeCity = adversVehicles.get(v).homeCity;
														rand = random.nextInt(possibleCities.size());
														adversVehicles.get(v).homeCity = possibleCities.get(rand);
														possibleCities.remove(rand);
														flag = true;
														adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
														adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
														certainty[vcl] = true;
														adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
														break;
													}
												}
											}
										}
										break;
									}
								}
								vcl++;
							}
						}
					}
					else {
						
						nv = numVehicles;
						if(adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) {
							certainty[adversTempStrategy.vehicle[0]] = false;
							
							while((adversBid < 0.7*adversPredictedBid || adversBid > 1.3*adversPredictedBid) && nv > 0) {
								nv--;
								for(int v = 0; v < numVehicles; v++) {
									if(!certainty[v]){										
										tempVehicle = adversVehicles.get(v);
										updatedVehicles.clear();
										updatedVehicles.add(tempVehicle);
										testStrategy = new Strategy(adversNumTasks + 1, 1);
										testStrategy.updateStrategy(adversNumTasks + 1, 1, testTasks, updatedVehicles);

										if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
											possibleCities.remove(adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity);
											adversVehicles.get(adversTempStrategy.vehicle[0]).homeCity = adversVehicles.get(v).homeCity;
											rand = random.nextInt(possibleCities.size());
											adversVehicles.get(v).homeCity = possibleCities.get(rand);
											possibleCities.remove(rand);
											flag = true;
											adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
											adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
											certainty[adversTempStrategy.vehicle[0]] = true;
											adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
											break;
										}
									}
								}
							}
						}
					}
				}
				else {
					if(adversTempStrategy.vehicle[adversNumTasks] != adversTempStrategy.vehicle[adversNumTasks-1] 
							&& adversFinalStrategy.nextAction[2*adversNumTasks + adversTempStrategy.vehicle[adversNumTasks]] == null) {
						tempCity = adversVehicles.get(adversTempStrategy.vehicle[adversNumTasks]).homeCity;
						for(City city:possibleCities) {
							adversVehicles.get(adversTempStrategy.vehicle[adversNumTasks]).homeCity = city;
							if(!certainty[adversTempStrategy.vehicle[adversNumTasks]]) {
								testStrategy = new Strategy(adversFinalStrategy, adversNumTasks+1, numVehicles);
								testStrategy.updateStrategy(adversNumTasks+1, numVehicles, adversTempTasks, adversVehicles);
								if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
									possibleCities.remove(city);
									adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
									adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
									certainty[adversTempStrategy.vehicle[adversNumTasks]] = true;
									adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
									flag = true;
									break;
								}
							}
						}
						if(!flag)
							adversVehicles.get(adversTempStrategy.vehicle[adversNumTasks]).homeCity = tempCity;
					}
					else if(adversTempStrategy.vehicle[adversNumTasks] == adversTempStrategy.vehicle[adversNumTasks-1]){
						
						
						while(x < numVehicles && adversFinalStrategy.nextAction[2*adversNumTasks + x] != null) {
								x++;
								if(x >= numVehicles)
									break;
						}
						
						if(x >= numVehicles)
							x--;
						
						if(adversFinalStrategy.nextAction[2*adversNumTasks + x] != null) {
							tempCity = adversVehicles.get(x).homeCity;
							for(City city:possibleCities) {
								adversVehicles.get(adversTempStrategy.vehicle[adversNumTasks]).homeCity = city;
								if(!certainty[adversTempStrategy.vehicle[adversNumTasks]]) {
									testStrategy = new Strategy(adversFinalStrategy, adversNumTasks+1, numVehicles);
									testStrategy.updateStrategy(adversNumTasks+1, numVehicles, adversTempTasks, adversVehicles);
									if(testStrategy.strategyCost != 0 && (adversBid > adversRatio*0.7*testStrategy.strategyCost && adversBid < adversRatio*1.3*testStrategy.strategyCost)) {
										possibleCities.remove(city);
										adversTempStrategy = new Strategy(adversFinalStrategy, adversNumTasks + 1, numVehicles);
										adversTempStrategy.updateStrategy(adversNumTasks + 1, numVehicles, adversTempTasks, adversVehicles);
										certainty[adversTempStrategy.vehicle[adversNumTasks]] = true;
										adversPredictedBid = adversRatio*0.95*(adversTempStrategy.strategyCost - adversFinalStrategy.strategyCost);
										flag = true;
										break;
									}
								}
							}
							if(!flag)
								adversVehicles.get(adversTempStrategy.vehicle[adversNumTasks]).homeCity = tempCity;
						}
					}
				}
			}
		}
		else {
			if(!certainty[adversTempStrategy.vehicle[adversNumTasks]])
				certainty[adversTempStrategy.vehicle[adversNumTasks]] = true;
		}
		
	}

	@Override
	public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
		Long startTime = System.currentTimeMillis();
		Strategy possibleBetterStrategy = new Strategy(ownFinalStrategy, ownNumTasks, numVehicles);
		List<Task> allTasks = new ArrayList<Task>();
		List<Plan> plans = new ArrayList<Plan>();
		
		allTasks.addAll(tasks);
		
		SLS sls = new SLS(ownNumTasks, numVehicles, allTasks, ownVehicles, possibleBetterStrategy);
		
		if(ownNumTasks != 0) {
			possibleBetterStrategy = sls.stochasticLocalSearch();
			if(possibleBetterStrategy.strategyCost < ownFinalStrategy.strategyCost) 
				plans = possibleBetterStrategy.strategyToPlans(numVehicles, ownNumTasks, vehicles, allTasks);
			else
				plans = ownFinalStrategy.strategyToPlans(numVehicles, ownNumTasks, vehicles, allTasks);
		}
		else
			plans = ownFinalStrategy.strategyToPlans(numVehicles, ownNumTasks, vehicles, allTasks);
		
		for(int p = 0; p < numVehicles; p++)
			System.out.println("plan = "+plans.get(p)+"");

		Long duration = System.currentTimeMillis() - startTime;
		System.out.println(" ownfinalCost = "+ownFinalStrategy.strategyCost+" .... possibleBetterCost = "+possibleBetterStrategy.strategyCost+" .... totalBidTime = "+totalTime+" .... totalPlantTime = "+duration+" .... totalReward = "+reward+"");
		return plans;
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
}
