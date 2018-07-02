/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements.objectFeatures.object_feature;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.image.MutableBoundingBox;
import boa.measurement.GeometricalMeasurements;
import boa.plugins.GeometricalFeature;
import boa.plugins.ObjectFeature;
import boa.plugins.ToolTip;
import static boa.plugins.plugins.measurements.objectFeatures.object_feature.Size.SCALED_TT;

/**
 *
 * @author jollion
 */
public class FeretMax implements GeometricalFeature, ToolTip {
    ChoiceParameter scaled = new ChoiceParameter("Scale", new String[]{"Pixel", "Unit"}, "Unit", false).setToolTipText(SCALED_TT);;
    public FeretMax setScale(boolean unit) {
        scaled.setSelectedIndex(unit?1:0);
        return this;
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{scaled};
    }

    @Override
    public ObjectFeature setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        return this;
    }

    @Override
    public double performMeasurement(Region object) {
        double feret = GeometricalMeasurements.getFeretMax(object);
        if (scaled.getSelectedIndex()==1) feret*=object.getScaleXY();
        return feret;
    }

    @Override
    public String getDefaultName() {
        return "Length";
    }
    @Override
    public String getToolTipText() {
        return "Maximal distance between two points of the contour";
    }
}