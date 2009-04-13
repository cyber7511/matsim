/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.households;

import java.util.List;
import java.util.Map;

import org.matsim.api.basic.v01.BasicLocation;
import org.matsim.api.basic.v01.Id;
import org.matsim.core.api.facilities.Facilities;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Population;
import org.matsim.core.basic.v01.BasicHousehold;
import org.matsim.core.basic.v01.HouseholdBuilder;
import org.matsim.vehicles.Vehicle;

/**
 * @author dgrether
 */
public class HouseholdBuilderImpl implements HouseholdBuilder {

	private Map<Id, Household> households;
	private Population population;
	private Map<Id, Vehicle> vehicles = null;
	private Facilities facilities;

	public HouseholdBuilderImpl(Population pop, Map<Id, Household> households, Facilities fac) {
		this.households = households;
		this.population = pop;
		this.facilities = fac;
	}
	
	public HouseholdBuilderImpl(Population pop, Map<Id, Household> households, Facilities fac, Map<Id, Vehicle> vehicles) {
		this(pop, households, fac);
		this.vehicles = vehicles;
	}

	public Map<Id, BasicHousehold> getHouseholds() {
		return (Map)this.households;
	}

	public BasicHousehold createHousehold(Id householdId,
			List<Id> membersPersonIds, BasicLocation loc, List<Id> vehicleIds) {
		HouseholdImpl hh = new HouseholdImpl(householdId);
		Person p;
		for (Id id : membersPersonIds){
			p = this.population.getPersons().get(id);
			if (p !=  null) {
				hh.addMember(p);
			}
			else {
				throw new IllegalArgumentException("Household member with Id: " + id + " is not part of population!");
			}
		}
		// FIXME [DG]
//		if (loc.getLocationType() == LocationType.FACILITY){
//			Facility f = this.facilities.getFacilities().get(loc.getId());
//			if (f != null) {
//				hh.setLocation(f);
//			}
//			else {
//				throw new IllegalStateException("Facility with Id: " + loc.getId() + " doesn't exist, however household with Id: "+ householdId + " is specified to be located there!");
//			}
//		}
//		else {
//			hh.setLocation(loc);
//		}
		if (this.vehicles != null) {
			for (Id id : vehicleIds) {
				Vehicle v = this.vehicles.get(id);
				if (v == null) {
					throw new IllegalStateException("The specified Vehicle with Id: " + id.toString() + " is not part of the vehicle database.");
				}
				hh.addVehicle(v);
			}
		}
		this.households.put(householdId, hh);
		return hh;
	}

}
