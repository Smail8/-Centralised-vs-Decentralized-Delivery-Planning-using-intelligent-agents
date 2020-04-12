package template;

import logist.simulation.Vehicle;
import logist.topology.Topology.City;

public class VehicleType {
	public int capacity;
	public City homeCity;
	public int costPerKm;
	
	public VehicleType(Vehicle vehicle) {
		capacity = vehicle.capacity();
		homeCity = vehicle.homeCity();
		costPerKm = vehicle.costPerKm();
	}
	
	public VehicleType(VehicleType vehicle) {
		capacity = vehicle.capacity;
		homeCity = vehicle.homeCity;
		costPerKm = vehicle.costPerKm;
	}
}
