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
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.BooleanParameter;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Measurements;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.Voxel;
import boa.image.BoundingBox;
import boa.image.Image;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import org.apache.commons.math3.linear.RealMatrix;
import boa.plugins.Measurement;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class BacteriaPhaseMeasurements implements Measurement {
    protected StructureParameter bacteria = new StructureParameter("Bacteria Structure", 1, false, false);
    protected Parameter[] parameters = new Parameter[]{bacteria};
    
    public BacteriaPhaseMeasurements(){}
    
    public BacteriaPhaseMeasurements(int bacteriaStructureIdx){
        this.bacteria.setSelectedIndex(bacteriaStructureIdx);
    }
    @Override
    public int getCallStructure() {
        return bacteria.getSelectedStructureIdx();
    }
    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }
    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = bacteria.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject("BacteriaCenterX", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaCenterY", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaLength", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaArea", structureIdx));
        res.add(new MeasurementKeyObject("BacteriaWidth", structureIdx));

        // from tracking
        res.add(new MeasurementKeyObject(StructureObject.TRACK_ERROR_PREV, structureIdx));
        res.add(new MeasurementKeyObject(StructureObject.TRACK_ERROR_NEXT, structureIdx));
        res.add(new MeasurementKeyObject("SizeRatio", structureIdx));
        res.add(new MeasurementKeyObject("TrackErrorSizeRatio", structureIdx));
        res.add(new MeasurementKeyObject("EndOfChannelContact", structureIdx));
        res.add(new MeasurementKeyObject("TruncatedDivision", structureIdx));
        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        Region bactObject = object.getRegion();
        BoundingBox parentOffset = object.getParent().getBounds();
        Point center=bactObject.getGeomCenter(false);
        center.translateRev(parentOffset);
        center.multiplyDim(object.getScaleXY(), 0);
        center.multiplyDim(object.getScaleXY(), 1);
        //if (object.getTimePoint()==0) logger.debug("object: {} center: {}, parentOffset: {}, objectoffset: {} bactImageOffset: {}, mutImageOffset: {}", object, center, parentOffset, object.getBounds(), bactImage.getBoundingBox(), mutImage.getBoundingBox());
        Measurements m = object.getMeasurements();
        m.setValue("BacteriaCenterX", center.get(0));
        m.setValue("BacteriaCenterY", center.get(1));
        double scale = object.getScaleXY();
        m.setValue("BacteriaLength", scale*GeometricalMeasurements.getFeretMax(bactObject));
        m.setValue("BacteriaArea", GeometricalMeasurements.getVolumeUnit(bactObject));
        m.setValue("BacteriaWidth", scale*GeometricalMeasurements.getThickness(bactObject));
        if (object.hasTrackLinkError(false, true)) m.setValue(StructureObject.TRACK_ERROR_NEXT, true);
        else m.setValue(StructureObject.TRACK_ERROR_NEXT, null); // remove
        if (object.hasTrackLinkError(true, false)) m.setValue(StructureObject.TRACK_ERROR_PREV, true);
        else m.setValue(StructureObject.TRACK_ERROR_PREV, null); // remove
        if (object.getAttribute("EndOfChannelContact")!=null) m.setValue("EndOfChannelContact", object.getAttribute("EndOfChannelContact", Double.NaN));
        else m.setValue("EndOfChannelContact", null); // remove
        if (object.getAttribute("TruncatedDivision")!=null) m.setValue("TruncatedDivision", object.getAttribute("TruncatedDivision", false));
        else m.setValue("TruncatedDivision", null); // remove
        if (object.getAttributes().get("SizeRatio")!=null)  m.setValue("SizeRatio", object.getAttribute("SizeRatio", Double.NaN));
        else m.setValue("SizeRatio", null); // remove
        if (Boolean.TRUE.equals(object.getAttribute("TrackErrorSizeRatio"))) m.setValue("TrackErrorSizeRatio", true);
        else m.setValue("TrackErrorSizeRatio", null); // remove
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
