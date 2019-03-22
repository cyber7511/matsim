/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

package org.matsim.contrib.dvrp.examples.onetaxi;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.dvrp.passenger.ActivityEngineWithWakeup;
import org.matsim.contrib.dvrp.passenger.BookingEngine;
import org.matsim.contrib.dvrp.passenger.PassengerRequestEventToPassengerEngineForwarder;
import org.matsim.contrib.dvrp.passenger.PassengerRequestScheduledEvent;
import org.matsim.contrib.dvrp.run.*;
import org.matsim.contrib.dynagent.run.DynActivityEngineModule;
import org.matsim.contrib.otfvis.OTFVisLiveModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.mobsim.qsim.ActivityEngineModule;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfigGroup;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfigurator;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vis.otfvis.OTFVisConfigGroup;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RunOneTaxiWithPrebookingExampleIT{
	private static final Logger log = Logger.getLogger(RunOneTaxiWithPrebookingExampleIT.class);

	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public void testRun() {
		// load config
		Config config = ConfigUtils.loadConfig( RunOneTaxiExample.CONFIG_FILE, new DvrpConfigGroup(), new OTFVisConfigGroup() );
		config.controler().setLastIteration( 0 );

		config.controler().setOutputDirectory( utils.getOutputDirectory() );

		DvrpConfigGroup dvrpConfig = ConfigUtils.addOrGetModule( config, DvrpConfigGroup.class );

		QSimComponentsConfigGroup componentsConfig = ConfigUtils.addOrGetModule( config, QSimComponentsConfigGroup.class );
		for( String component : componentsConfig.getActiveComponents() ){
			log.info( "mobsimComponent=" + component ) ;
		}

		config.addConfigConsistencyChecker(new DvrpConfigConsistencyChecker() );
		config.checkConsistency();

		// load scenario
		Scenario scenario = ScenarioUtils.loadScenario(config );

		for( Person person : scenario.getPopulation().getPersons().values() ){
			Plan plan = person.getSelectedPlan() ;
			plan.getAttributes().putAttribute( ActivityEngineWithWakeup.PREBOOKING_OFFSET_ATTRIBUTE_NAME, 900. ) ;
//			for( PlanElement pe : plan.getPlanElements() ){
//				if ( pe instanceof Leg ) {
//					if ( ((Leg) pe).getMode().equals( TransportMode.drt ) || ((Leg) pe).getMode().equals( TransportMode.taxi ) ) {
//						log.warn("adding attribute ...") ;
//						pe.getAttributes().putAttribute( ActivityEngineWithWakeup.PREBOOKING_OFFSET_ATTRIBUTE_NAME, 900. ) ;
//					}
//				}
//			}
		}

		PopulationUtils.writePopulation( scenario.getPopulation(), utils.getOutputDirectory() + "/../pop.xml" );

		// setup controler
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new DvrpModule() );
		controler.addOverridingModule(new OneTaxiModule( RunOneTaxiExample.TAXIS_FILE) );
		// yyyy I find it unexpected to have an example as "module".  kai, mar'19
		controler.addOverridingModule( new AbstractModule(){
			@Override
			public void install(){
				this.bind( PassengerRequestEventToPassengerEngineForwarder.class ).in( Singleton.class ) ;
				this.addEventHandlerBinding().to( PassengerRequestEventToPassengerEngineForwarder.class ) ;
				// yyyyyy this should presumably not be in user code. kai, mar'19
			}
		} ) ;
		controler.configureQSimComponents( components -> {
			log.info("=== before ...") ;
			for( Object component : components.getActiveComponents() ){
				log.info( component.toString() ) ;
			}
			components.removeNamedComponent( ActivityEngineModule.COMPONENT_NAME );
			components.addNamedComponent( DynActivityEngineModule.COMPONENT_NAME );
			components.addNamedComponent( "abc" );
			components.addNamedComponent( "BookingEngine" );
			for( String m : new String[]{TransportMode.taxi} ){
				components.addComponent( DvrpModes.mode( m ) );
				// note that this is not an "addNAMEDComponent"!
			}
			log.info("=== after ...") ;
			for( Object component : components.getActiveComponents() ){
				log.info( component.toString() ) ;
			}
		} );
		controler.addOverridingQSimModule( new AbstractQSimModule(){
			@Override protected void configureQSim(){
				this.bind( ActivityEngineWithWakeup.class ).in( Singleton.class ) ;
				this.addQSimComponentBinding( "abc" ).to( ActivityEngineWithWakeup.class ) ;

				this.bind( BookingEngine.class ).in( Singleton.class ) ;
				this.addQSimComponentBinding( "BookingEngine" ).to( BookingEngine.class ) ;

//				MapBinder<String, TripInfo.Provider> mapBinder = MapBinder.newMapBinder( this.binder(), String.class, TripInfo.Provider.class );
//				mapBinder.addBinding("abc" ).toProvider( new Provider<TripInfo.Provider>() {
//					@Override public TripInfo.Provider get(){
//						return new PassengerEngine( mode, eventsManager, requestCreator, optimizer, network, requestValidator ) ;
//					}
//				} );

//				this.binder().bind( TripInfo.Provider.class ).annotatedWith( Names.named( TransportMode.taxi ) ).to( PassengerEngine.class ) ;
				// does not work since PassengerEngine does not have a constructor annotated with @Inject.  kai, mar'19
				// In general I am not sure if this is the right syntax, or if one should rather use a Multibinder or a MultiSet.  kai, mar'19
			}
		} ) ;

		if ( true ) {
//			controler.addOverridingModule(new OTFVisLiveModule() ); // OTFVis visualisation
		}

		controler.addOverridingModule( new AbstractModule(){
			@Override
			public void install(){
				this.addEventHandlerBinding().toInstance( new BasicEventHandler(){
					private int cnt = 0 ;
					private int cnt2 = 0 ;
					@Override public synchronized void handleEvent( Event event ){
						if (event instanceof ActivityEngineWithWakeup.AgentWakeupEvent ) {
							final ActivityEngineWithWakeup.AgentWakeupEvent ev = (ActivityEngineWithWakeup.AgentWakeupEvent) event;
							log.warn("cnt=" + cnt + "; event=" + event) ;
							switch(cnt) {
								case 0 :
								case 1 :
								case 2 :
								case 3 :
									Assert.assertEquals( 0., event.getTime(), Double.MIN_VALUE );
									final List<String> personIds = Arrays.asList( "passenger_0", "passenger_1", "passenger_2", "passenger_3" );
									Assert.assertTrue( personIds.contains( ev.getPersonId().toString() ) ) ;
									break ;
								case 4 :
									Assert.assertEquals( 300., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_4", ev.getPersonId().toString() );
									break ;
								case 5 :
									Assert.assertEquals( 600., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_5", ev.getPersonId().toString() );
									break ;
								case 6 :
									Assert.assertEquals( 900., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_6", ev.getPersonId().toString() );
									break ;
								case 7 :
									Assert.assertEquals( 1200., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_7", ev.getPersonId().toString() );
									break ;
								case 8 :
									Assert.assertEquals( 1500., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_8", ev.getPersonId().toString() );
									break ;
								case 9 :
									Assert.assertEquals( 1800., event.getTime(), Double.MIN_VALUE );
									Assert.assertEquals( "passenger_9", ev.getPersonId().toString() );
									break ;
							}
							cnt++ ;
						} else if ( event instanceof PassengerRequestScheduledEvent ) {
							PassengerRequestScheduledEvent ev = (PassengerRequestScheduledEvent) event;
							log.warn("cnt2=" + cnt2 + "; event=" + event) ;
							cnt2++ ;
						}
					}
				} );
			}
		} ) ;



		// run simulation
		controler.run();
	}
}
