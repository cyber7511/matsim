/* *********************************************************************** *
 * project: org.matsim.*
 * RunEmissionToolOffline.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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
package org.matsim.contrib.drt.taxibus.run.sim;

import java.util.Collection;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.drt.DrtActionCreator;
import org.matsim.contrib.drt.taxibus.algorithm.optimizer.*;
import org.matsim.contrib.drt.taxibus.algorithm.optimizer.prebooked.PrebookedTaxibusOptimizerContext;
import org.matsim.contrib.drt.taxibus.algorithm.optimizer.prebooked.clustered.*;
import org.matsim.contrib.drt.taxibus.algorithm.optimizer.prebooked.jsprit.JspritTaxibusOptimizer;
import org.matsim.contrib.drt.taxibus.algorithm.optimizer.sharedTaxi.SharedTaxiOptimizer;
import org.matsim.contrib.drt.taxibus.algorithm.passenger.*;
import org.matsim.contrib.drt.taxibus.algorithm.scheduler.*;
import org.matsim.contrib.drt.taxibus.algorithm.utils.TaxibusUtils;
import org.matsim.contrib.drt.taxibus.run.configuration.TaxibusConfigGroup;
import org.matsim.contrib.dvrp.data.Fleet;
import org.matsim.contrib.dvrp.router.TimeAsTravelDisutility;
import org.matsim.contrib.dvrp.trafficmonitoring.VrpTravelTimeModules;
import org.matsim.contrib.dvrp.vrpagent.*;
import org.matsim.contrib.dvrp.vrpagent.VrpLegs.LegCreator;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.mobsim.qsim.*;
import org.matsim.core.router.util.*;

import com.beust.jcommander.internal.Nullable;
import com.google.inject.*;
import com.google.inject.name.Named;

/**
 * @author jbischoff
 */

public class TaxibusQSimProvider implements Provider<QSim> {
	private final Scenario scenario;
	private final EventsManager events;
	private final Collection<AbstractQSimPlugin> plugins;
	private final Fleet fleetData;
	private final TravelTime travelTime;
	private final TaxibusConfigGroup tbcg;
	private final TaxibusPassengerOrderManager orderManager;

	@Inject
	TaxibusQSimProvider(Scenario scenario, EventsManager events, Collection<AbstractQSimPlugin> plugins, Fleet vrpData,
			@Named(VrpTravelTimeModules.DVRP_ESTIMATED) TravelTime travelTime, TaxibusConfigGroup tbcg,
			@Nullable TaxibusPassengerOrderManager orderManager) {
		this.scenario = scenario;
		this.events = events;
		this.plugins = plugins;
		this.fleetData = vrpData;
		this.travelTime = travelTime;
		this.tbcg = tbcg;
		this.orderManager = orderManager;
	}

	@Override
	public QSim get() {
		QSim qSim = QSimUtils.createQSim(scenario, events, plugins);

		TaxibusOptimizer optimizer = createTaxibusOptimizer(qSim);
		qSim.addQueueSimulationListeners(optimizer);

		TaxibusPassengerEngine passengerEngine = new TaxibusPassengerEngine(TaxibusUtils.TAXIBUS_MODE, events,
				new DrtRequestCreator(), optimizer, scenario.getNetwork());
		qSim.addMobsimEngine(passengerEngine);
		qSim.addDepartureHandler(passengerEngine);
		if (orderManager != null) {
			orderManager.setPassengerEngine(passengerEngine);
			qSim.addQueueSimulationListeners(orderManager);
		}

		LegCreator legCreator = VrpLegs.createLegWithOfflineTrackerCreator(qSim.getSimTimer());
		DrtActionCreator actionCreator = new DrtActionCreator(passengerEngine, legCreator, tbcg.getPickupDuration());
		qSim.addAgentSource(new VrpAgentSource(actionCreator, fleetData, optimizer, qSim));

		return qSim;
	}

	private TaxibusOptimizer createTaxibusOptimizer(QSim qSim) {
		// Joschka, now you can safely use travel times for disutility - these are from the past
		// (not the currently collected)
		TravelDisutility travelDisutility = new TimeAsTravelDisutility(travelTime);
		TaxibusSchedulerParams params = new TaxibusSchedulerParams(tbcg.getPickupDuration(), tbcg.getDropoffDuration());
		TaxibusScheduler scheduler = new TaxibusScheduler(fleetData, qSim.getSimTimer(), params);
		TaxibusOptimizerContext optimizerContext = new TaxibusOptimizerContext(fleetData, scenario, qSim.getSimTimer(),
				travelTime, travelDisutility, scheduler, tbcg);

		switch (tbcg.getAlgorithm()) {

			case "sharedTaxi":
				return new SharedTaxiOptimizer(optimizerContext, false, tbcg.getDetourFactor());

			case "jsprit": {
				PrebookedTaxibusOptimizerContext context = new PrebookedTaxibusOptimizerContext(fleetData, scenario,
						qSim.getSimTimer(), travelTime, travelDisutility, scheduler, tbcg);
				return new JspritTaxibusOptimizer(context);
			}

			case "clustered_jsprit": {
				PrebookedTaxibusOptimizerContext context = new PrebookedTaxibusOptimizerContext(fleetData, scenario,
						qSim.getSimTimer(), travelTime, travelDisutility, scheduler, tbcg);
				return new ClusteringTaxibusOptimizer(context, new JspritDispatchCreator(context));
			}
			default:
				throw new RuntimeException("No config parameter set for algorithm, please check and assign in config");
		}
	}
}
