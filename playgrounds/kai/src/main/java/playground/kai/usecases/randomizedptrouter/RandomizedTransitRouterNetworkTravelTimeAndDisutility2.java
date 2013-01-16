/* *********************************************************************** *
 * project: org.matsim.*
 * RandomizedTransitRouterNetworkTravelTimeAndDisutility2
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package playground.kai.usecases.randomizedptrouter;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.otfvis.OTFVis;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.Mobsim;
import org.matsim.core.mobsim.framework.MobsimFactory;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimFactory;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.router.CustomDataManager;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterFactory;
import org.matsim.pt.router.TransitRouterImpl;
import org.matsim.pt.router.TransitRouterNetwork;
import org.matsim.pt.router.TransitRouterNetwork.TransitRouterNetworkLink;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vis.otfvis.OTFClientLive;
import org.matsim.vis.otfvis.OnTheFlyServer;


/**
 * @author kai
 * @author dgrether
 *
 */
public class RandomizedTransitRouterNetworkTravelTimeAndDisutility2  extends TransitRouterNetworkTravelTimeAndDisutility {

	Id cachedPersonId = null ;
	final TransitRouterConfig originalTransitRouterConfig ;
//	TransitRouterConfig localConfig ;
	private double localMarginalUtilityOfTravelTimeWalk_utl_s = Double.NaN ;
	private double localMarginalUtilityOfWaitingPt_utl_s = Double.NaN ;
	private double localUtilityOfLineSwitch_utl = Double.NaN ;
	private double localMarginalUtilityOfTravelTimePt_utl_s = Double.NaN ;
	private double localMarginalUtilityOfTravelDistancePt_utl_m = Double.NaN ;
	
	public enum DataCollection {randomizedParameters, additionInformation}
	private Map<DataCollection,Boolean> dataCollectionConfig = new HashMap<DataCollection,Boolean>() ;
	private Map<DataCollection,StringBuffer> dataCollectionStrings = new HashMap<DataCollection,StringBuffer>() ;
	
	private final PreparedTransitSchedule data = new PreparedTransitSchedule();
	
	public void setDataCollection( DataCollection item, Boolean bbb ) {
		Logger.getLogger(this.getClass()).info( " settin data collection of " + item.toString() + " to " + bbb.toString() ) ;
		dataCollectionConfig.put( item, bbb ) ;
	}
	public String getDataCollectionString( DataCollection item ) {
		return dataCollectionStrings.get(item).toString() ;
	}
	
	public RandomizedTransitRouterNetworkTravelTimeAndDisutility2(TransitRouterConfig routerConfig) {
		super(routerConfig);
		
		for ( DataCollection dataCollection : DataCollection.values() ) {
			switch ( dataCollection ) {
			case randomizedParameters:
				dataCollectionConfig.put( dataCollection, false ) ;
				dataCollectionStrings.put( dataCollection, new StringBuffer() ) ;
				break;
			case additionInformation:
				dataCollectionConfig.put( dataCollection, false ) ;
				dataCollectionStrings.put( dataCollection, new StringBuffer() ) ;
				break;
			}
		}
		
		// make sure that some parameters are not zero since otherwise the randomization will not work:

		// marg utl time wlk should be around -3/h or -(3/3600)/sec.  Give warning if not at least 1/3600:
		if ( -routerConfig.getMarginalUtilityOfTravelTimeWalk_utl_s() < 1./3600. ) {
			Logger.getLogger(this.getClass()).warn( "marg utl of walk rather close to zero; randomization may not work") ;
		}
		// utl of line switch should be around -300sec or -0.5u.  Give warning if not at least 0.1u:
		if ( -routerConfig.getUtilityOfLineSwitch_utl() < 0.1 ) {
			Logger.getLogger(this.getClass()).warn( "utl of line switch rather close to zero; randomization may not work") ;
		}

			
			this.originalTransitRouterConfig = routerConfig ;
//			this.localConfig = config ;
			this.localMarginalUtilityOfTravelDistancePt_utl_m = routerConfig.getMarginalUtilityOfTravelDistancePt_utl_m();
			this.localMarginalUtilityOfTravelTimePt_utl_s = routerConfig.getMarginalUtilityOfTravelTimePt_utl_s() ;
			this.localMarginalUtilityOfTravelTimeWalk_utl_s = routerConfig.getMarginalUtilityOfTravelTimeWalk_utl_s() ;
			this.localMarginalUtilityOfWaitingPt_utl_s = routerConfig.getMarginalUtilityOfTravelTimePt_utl_s() ;
			this.localUtilityOfLineSwitch_utl = routerConfig.getUtilityOfLineSwitch_utl() ;
	}
	
	@Override
	public double getLinkTravelDisutility(final Link link, final double time, final Person person, final Vehicle vehicle, 
			final CustomDataManager dataManager) {
		
		if ( !person.getId().equals(this.cachedPersonId)) {
			// yyyyyy probably not thread safe (?!?!)
			
			// person has changed, so ...
			
			// ... memorize new person id:
			this.cachedPersonId = person.getId() ;
			
			// ... generate new random parameters.  One param can remain fixed.
			// Since the marginal utility of walk time is used in the
			// multimodal dijkstra outside travel disutility, we keep that one fixed and vary the other ones. 
//			{
//				double tmp = this.originalTransitRouterConfig.getMarginalUtilityOfTravelTimeWalk_utl_s() ;
//				tmp *= 5. * MatsimRandom.getRandom().nextDouble() ;
//				localMarginalUtilityOfTravelTimeWalk_utl_s = tmp ;
//			}
			{
				double tmp = this.originalTransitRouterConfig.getUtilityOfLineSwitch_utl() ;
				tmp *= 5. * MatsimRandom.getRandom().nextDouble() ;
				localUtilityOfLineSwitch_utl = tmp ;
			}
			{
				double tmp = this.originalTransitRouterConfig.getMarginalUtilityOfTravelTimePt_utl_s() ;
				tmp *= 5. * MatsimRandom.getRandom().nextDouble() ;
				localMarginalUtilityOfTravelTimePt_utl_s = tmp ;
			}
			{
				double tmp = this.originalTransitRouterConfig.getMarginalUtilityOfWaitingPt_utl_s();
				tmp *= 5. * MatsimRandom.getRandom().nextDouble();
				localMarginalUtilityOfWaitingPt_utl_s = tmp;
			}
			
			
			
			if ( this.dataCollectionConfig.get(DataCollection.randomizedParameters) ) {
				StringBuffer strb = this.dataCollectionStrings.get(DataCollection.randomizedParameters) ;
				strb.append("to be modfied by Manuel") ;
			}
		}
		
		
		double disutl;
		if (((TransitRouterNetworkLink) link).getRoute() == null) {
			// this means that it is a transfer link (walk)

			double transfertime = getLinkTravelTime(link, time, person, vehicle);
			double waittime = this.originalTransitRouterConfig.additionalTransferTime;
			
			// say that the effective walk time is the transfer time minus some "buffer"
			double walktime = transfertime - waittime;
			
			disutl = -walktime * localMarginalUtilityOfTravelTimeWalk_utl_s
			       -waittime * localMarginalUtilityOfWaitingPt_utl_s
			       - localUtilityOfLineSwitch_utl;
			
		} else {
			// this means that it is a travel link.  With this version, we cannot differentiate between in-vehicle
			// wait and out-of-vehicle wait, but my current intuition is that this will not matter that much (despite what is
			// said in the literature).  kai, sep'12 
			//the code was modified so it differentiates now... dg, nov 2012
			
			double offVehWaitTime = offVehicleWaitTime(link, time);
			
			double inVehTime = getLinkTravelTime(link,time, person, vehicle) - offVehWaitTime ;

			disutl = -inVehTime * this.localMarginalUtilityOfTravelTimePt_utl_s 
					- offVehWaitTime * this.localMarginalUtilityOfWaitingPt_utl_s
					- link.getLength() * this.localMarginalUtilityOfTravelDistancePt_utl_m;
			
//			disutl = - getLinkTravelTime(link, time, person, vehicle) * localMarginalUtilityOfTravelTimePt_utl_s
//			       - link.getLength() * localMarginalUtilityOfTravelDistancePt_utl_m;
		}
		if ( this.dataCollectionConfig.get(DataCollection.additionInformation )) {
			StringBuffer strb = this.dataCollectionStrings.get(DataCollection.additionInformation ) ;
			strb.append("also collecting additional information") ;
		}
		return disutl;
	}
	
	@Override
	protected double offVehicleWaitTime(final Link link, final double time) {
		//calculate off vehicle waiting time as new router parameter
		double offVehWaitTime=0;
		double nextVehArrivalTime = getVehArrivalTime(link, time);
		if (time < nextVehArrivalTime){ // it means the agent waits outside the veh				
			offVehWaitTime = nextVehArrivalTime-time;
		}
		return offVehWaitTime;
	}

	//variables for caching offVehWaitTime
	Link previousWaitLink;
	double previousWaitTime;
	double cachedVehArrivalTime;

//	/* package (for a test) */ double getVehArrivalTime(final Link link, final double now){
//		if ((link == this.previousWaitLink) && (now == this.previousWaitTime)) {
//			return this.cachedVehArrivalTime;
//		}
//		this.previousWaitLink = link;
//		this.previousWaitTime = now;
//
//		//first find out vehicle arrival time to fromStop according to transit schedule
//		TransitRouterNetworkLink wrapped = (TransitRouterNetworkLink) link;
//		if (wrapped.getRoute() == null) { 
//			throw new RuntimeException("should not happen") ;
//		}
//		TransitRouteStop fromStop = wrapped.fromNode.stop;
//
//		double nextDepartureTime = data.getNextDepartureTime(wrapped.getRoute(), fromStop, now);
//
//		double fromStopArrivalOffset = (fromStop.getArrivalOffset() != Time.UNDEFINED_TIME) ? fromStop.getArrivalOffset() : fromStop.getDepartureOffset();
//		double vehWaitAtStopTime = fromStop.getDepartureOffset()- fromStopArrivalOffset; //time in which the veh stops at station
//		double vehArrivalTime = nextDepartureTime - vehWaitAtStopTime; //instead of a method "bestArrivalTime" we calculate the bestDeparture- stopTime 
//		cachedVehArrivalTime = vehArrivalTime ;
//		return vehArrivalTime ;		
//	}


	public static void main(String[] args) {
//		String configFile = "/home/dgrether/data/work/repos/shared-svn/projects/andreas-graf/Szenarien/SimplifiedRandomizedPT/config_adapt_rpt_dg.xml";
		boolean doVisualization = true;
		final Config config = ConfigUtils.loadConfig(args[0]) ;
//		final Config config = ConfigUtils.loadConfig(configFile) ;

		config.planCalcScore().setWriteExperiencedPlans(true) ;

		config.otfVis().setDrawTransitFacilities(true) ; // this DOES work
		config.otfVis().setDrawTransitFacilityIds(false);
		config.otfVis().setShowTeleportedAgents(true) ;
		config.otfVis().setDrawNonMovingItems(true);
		config.otfVis().setScaleQuadTreeRect(true);

		final Scenario scenario = ScenarioUtils.loadScenario(config) ;
		
		final Controler ctrl = new Controler(scenario) ;
		
		ctrl.setOverwriteFiles(true) ;
		
		final TransitSchedule schedule = ctrl.getScenario().getTransitSchedule() ;
		
		final TransitRouterConfig trConfig = new TransitRouterConfig( ctrl.getScenario().getConfig() ) ; 
		
		final TransitRouterNetwork routerNetwork = TransitRouterNetwork.createFromSchedule(schedule, trConfig.beelineWalkConnectionDistance);
		
		ctrl.setTransitRouterFactory( new TransitRouterFactory() {
			@Override
			public TransitRouter createTransitRouter() {
				RandomizedTransitRouterNetworkTravelTimeAndDisutility2 ttCalculator = new RandomizedTransitRouterNetworkTravelTimeAndDisutility2(trConfig);
				ttCalculator.setDataCollection(DataCollection.randomizedParameters, true) ;
				ttCalculator.setDataCollection(DataCollection.additionInformation, false) ;
				return new TransitRouterImpl(trConfig, new PreparedTransitSchedule(schedule), routerNetwork, ttCalculator, ttCalculator);
			}
		}) ;
		
		if (doVisualization){
			ctrl.setMobsimFactory(new MobsimFactory(){
				
				@Override
				public Mobsim createMobsim(Scenario sc, EventsManager eventsManager) {
					QSim qSim = (QSim) new QSimFactory().createMobsim(sc, eventsManager) ;
					
					OnTheFlyServer server = OTFVis.startServerAndRegisterWithQSim(sc.getConfig(), sc, eventsManager, qSim);
					OTFClientLive.run(sc.getConfig(), server);
					
					return qSim ;
				}}) ;
		}
		
		ctrl.run() ;

	}
	
}
