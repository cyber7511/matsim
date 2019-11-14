/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

/**
 *
 */
package org.matsim.contrib.drt.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.facilities.Facility;

/**
 * @author jbischoff
 * @author michalm (Michal Maciejewski)
 */
public class StopBasedDrtRoutingModule implements RoutingModule {
	private static final Logger logger = Logger.getLogger(StopBasedDrtRoutingModule.class);

	public interface AccessEgressFacilityFinder {
		Optional<Pair<Facility, Facility>> findFacilities(Facility fromFacility, Facility toFacility);
	}

	private final DrtStageActivityType drtStageActivityType;
	private final Network modalNetwork;
	private final AccessEgressFacilityFinder stopFinder;
	private final DrtConfigGroup drtCfg;
	private final Scenario scenario;
	private final DrtRouteLegCalculator drtRouteLegCalculator;
	private final RoutingModule accessRouter;
	private final RoutingModule egressRouter;

	public StopBasedDrtRoutingModule(DrtRouteLegCalculator drtRouteLegCalculator, RoutingModule accessRouter,
			RoutingModule egressRouter, AccessEgressFacilityFinder stopFinder, DrtConfigGroup drtCfg, Scenario scenario,
			Network modalNetwork) {
		this.drtRouteLegCalculator = drtRouteLegCalculator;
		this.stopFinder = stopFinder;
		this.drtCfg = drtCfg;
		this.scenario = scenario;
		this.drtStageActivityType = new DrtStageActivityType(drtCfg.getMode());
		this.modalNetwork = modalNetwork;
		this.accessRouter = accessRouter;
		this.egressRouter = egressRouter;
	}

	@Override
	public List<? extends PlanElement> calcRoute(Facility fromFacility, Facility toFacility, double departureTime,
			Person person) {
		Optional<Pair<Facility, Facility>> stops = stopFinder.findFacilities(
				Objects.requireNonNull(fromFacility, "fromFacility is null"),
				Objects.requireNonNull(toFacility, "toFacility is null"));
		if (!stops.isPresent()) {
			printWarning(
					() -> "No access/egress stops found, agent will use fallback mode " + TripRouter.getFallbackMode(
							drtCfg.getMode()) + ". Agent Id:\t" + person.getId());
			return null;
		}

		Facility accessFacility = stops.get().getLeft();
		Facility egressFacility = stops.get().getRight();
		if (accessFacility.getLinkId().equals(egressFacility.getLinkId())) {
			printWarning(
					() -> "Start and end stop are the same, agent will use fallback mode " + TripRouter.getFallbackMode(
							drtCfg.getMode()) + ". Agent Id:\t" + person.getId());
			return null;
		}

		List<PlanElement> trip = new ArrayList<>();

		double now = departureTime;

		// access (sub-)trip:
		List<? extends PlanElement> accessTrip = accessRouter.calcRoute(fromFacility, accessFacility, now, person);
		trip.addAll(accessTrip);
		for (PlanElement planElement : accessTrip) {
			now = TripRouter.calcEndOfPlanElement(now, planElement, scenario.getConfig());
		}

		// interaction activity:
		trip.add(createDrtStageActivity(accessFacility));

		// drt proper leg:
		now++;
		Link accessActLink = modalNetwork.getLinks()
				.get(accessFacility.getLinkId()); // we want that this crashes if not found.  kai/gl, oct'19
		Link egressActLink = modalNetwork.getLinks()
				.get(egressFacility.getLinkId()); // we want that this crashes if not found.  kai/gl, oct'19
		List<? extends PlanElement> drtLeg = drtRouteLegCalculator.createRealDrtLeg(departureTime, accessActLink,
				egressActLink);
		trip.addAll(drtLeg);
		for (PlanElement planElement : drtLeg) {
			now = TripRouter.calcEndOfPlanElement(now, planElement, scenario.getConfig());
		}

		// interaction activity:
		trip.add(createDrtStageActivity(egressFacility));

		// egress (sub-)trip:
		now++;
		List<? extends PlanElement> egressTrip = egressRouter.calcRoute(egressFacility, toFacility, now, person);
		trip.addAll(egressTrip);

		return trip;
	}

	private Activity createDrtStageActivity(Facility stopFacility) {
		Activity activity = scenario.getPopulation()
				.getFactory()
				.createActivityFromCoord(drtStageActivityType.drtStageActivity, stopFacility.getCoord());
		activity.setMaximumDuration(0);
		activity.setLinkId(stopFacility.getLinkId());
		return activity;
	}

	private void printWarning(Supplier<String> supplier) {
		if (drtCfg.isPrintDetailedWarnings()) {
			logger.warn(supplier.get());
		}
	}

	static Coord getFacilityCoord(Facility facility, Network network) {
		Coord coord = facility.getCoord();
		if (coord == null) {
			coord = network.getLinks().get(facility.getLinkId()).getCoord();
			if (coord == null)
				throw new RuntimeException("From facility has neither coordinates nor link Id. Should not happen.");
		}
		return coord;
	}
}
