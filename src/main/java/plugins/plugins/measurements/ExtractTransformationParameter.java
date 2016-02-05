/*
 * Copyright (C) 2016 jollion
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package plugins.plugins.measurements;

import configuration.parameters.Parameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import configuration.parameters.TransformationPluginParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.List;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import plugins.Transformation;
import plugins.plugins.transformations.ScaleHistogramSignalExclusion;

/**
 *
 * @author jollion
 */
public class ExtractTransformationParameter implements Measurement{
    protected StructureParameter structure = new StructureParameter("Strore Structure", 0, false, false);
    TransformationPluginParameter<Transformation> transformation = new TransformationPluginParameter<Transformation>("Transformation", Transformation.class, false);
    protected TextParameter keyName = new TextParameter("Measurement Name", "TransformationParameter", false);
    protected Parameter[] parameters = new Parameter[]{structure, transformation, keyName};
    
    public int getCallStructure() {
        return -1;
    }

    public boolean callOnlyOnTrackHeads() {
        return true;
    }

    public List<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject(keyName.getValue()+"_mean", structure.getSelectedStructureIdx()));
        res.add(new MeasurementKeyObject(keyName.getValue()+"_sd", structure.getSelectedStructureIdx()));
        return res;
    }

    public void performMeasurement(StructureObject root, List<StructureObject> modifiedObjects) {
        ArrayList data=null;
        Transformation t=null;
        List<TransformationPluginParameter<Transformation>> l = root.getExperiment().getMicroscopyField(root.getFieldName()).getPreProcessingChain().getTransformations();
        for (TransformationPluginParameter<Transformation> tpp : l) {
            if (tpp.getPluginName()!=null && tpp.getPluginName().equals(transformation.getPluginName())) {
                t = tpp.instanciatePlugin();
                if (t!=null) data = t.getConfigurationData();
                break;
            }
        }
        if (data!=null) {
            List<StructureObject> rootTrack = StructureObjectUtils.getTrack(root);
            if (data.size()==rootTrack.size()) {
                for (int i = 0 ;i<rootTrack.size(); ++i) assignData(t, data.get(i), rootTrack.get(i), structure.getSelectedStructureIdx(), keyName.getValue(), modifiedObjects);
            } else if (data.size()==1) assignData(t, data.get(0), root, structure.getSelectedStructureIdx(), keyName.getValue(), modifiedObjects);
            else assignData(t, data, root, structure.getSelectedStructureIdx(), keyName.getValue(), modifiedObjects);
        }
    }
    
    private static void assignData(Transformation t, Object o, StructureObject root, int structureIdx, String key, List<StructureObject> modifiedObjects) {
        List<StructureObject> children = root.getChildren(structureIdx);
        if (o instanceof Number) for (StructureObject object : children)  {
            object.getMeasurements().setValue(key, (Number)o);
            modifiedObjects.add(object);
        }
        else if (o instanceof String) for (StructureObject object : children) {
            object.getMeasurements().setValue(key, (String)o);
            modifiedObjects.add(object);
        }
        else if (o instanceof double[]) for (StructureObject object : children) {
            object.getMeasurements().setValue(key, (double[])o);
            modifiedObjects.add(object);
        }
        else {
            if (t instanceof ScaleHistogramSignalExclusion) {
                ArrayList<Double> d = (ArrayList<Double>) o;
                for (StructureObject object : children) {
                    object.getMeasurements().setValue(key+"_mean", d.get(0));
                    object.getMeasurements().setValue(key+"_sd", d.get(1));
                    modifiedObjects.add(object);
                }
            }
        }
    }

    public Parameter[] getParameters() {
        return parameters;
    }
    
}