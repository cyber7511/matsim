/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2016 by the members listed in the COPYING,       *
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
package playground.johannes.synpop.sim;

import org.matsim.contrib.common.stats.Discretizer;
import playground.johannes.synpop.analysis.NumericAttributeProvider;
import playground.johannes.synpop.data.Segment;

/**
 * @author jillenberger
 */
public class LegAttributeHistogramBuilder extends LegHistogramBuilder {

    public LegAttributeHistogramBuilder(String key, Discretizer discretizer) {
        super(new NumericAttributeProvider<Segment>(key), discretizer);
    }
}