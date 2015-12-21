/*
 * Copyright (C) 2015 jollion
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

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import image.BoundingBox;
import image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;

/**
 *
 * @author jollion
 */
public class ObjectInclusionCount implements Measurement {
    protected StructureParameter structureContainer = new StructureParameter("Containing Structure", -1, false, false);
    protected StructureParameter structureToCount = new StructureParameter("Structure to count", -1, false, false);
    protected BooleanParameter onlyTrackHeads = new BooleanParameter("Count Only TrackHeads", false);
    protected BoundedNumberParameter percentageInclusion = new BoundedNumberParameter("Minimum percentage of inclusion", 0, 100, 0, 100);
    protected TextParameter inclusionText = new TextParameter("Inclusion Key Name", "ObjectNumber", false);
    protected Parameter[] parameters = new Parameter[]{structureContainer, structureToCount, percentageInclusion, onlyTrackHeads, inclusionText};
    
    public ObjectInclusionCount() {}
    
    public ObjectInclusionCount(int containingStructure, int structureToCount, double minPercentageInclusion) {
        this.structureContainer.setSelectedIndex(containingStructure);
        this.structureToCount.setSelectedIndex(structureToCount);
        this.percentageInclusion.setValue(minPercentageInclusion);
    }
    
    @Override
    public int getCallStructure() {
        return structureContainer.getFirstCommonParentStructureIdx(structureToCount.getSelectedIndex());
    }
    // TODO: only track heads
    @Override
    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects) {
        double p = percentageInclusion.getValue().doubleValue()/100d;
        if (object.getStructureIdx()==structureContainer.getSelectedIndex()) {
            object.getMeasurements().setValue(inclusionText.getValue(), count(object, structureToCount.getSelectedIndex(), p, onlyTrackHeads.getSelected()));
            modifiedObjects.add(object);
        } else {
            ArrayList<StructureObject> containers = object.getChildren(structureContainer.getSelectedIndex());
            ArrayList<StructureObject> toCount = object.getChildren(structureToCount.getSelectedIndex());
            for (StructureObject c : containers) {
                c.getMeasurements().setValue(inclusionText.getValue(), count(object, c, toCount, p, onlyTrackHeads.getSelected()));
                modifiedObjects.add(c);
            }
        }
        
        
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject(inclusionText.getValue(), structureContainer.getSelectedIndex()));
        return res;
    }
    
    public static int count(StructureObject commonParent, StructureObject container, List<StructureObject> toCount, double proportionInclusion, boolean onlyTrackHeads) {
        if (toCount==null || toCount.isEmpty()) return 0;
        int count = 0;
        Object3D containerObject = container.getObject();
        // structureToCount coordinates should be expressed in container's reference
        BoundingBox offsetC;
        if (commonParent!=container) offsetC = container.getParent().getRelativeBoundingBox(commonParent);
        else {
            offsetC = container.getBounds();
            offsetC = new BoundingBox(-offsetC.getxMin(), -offsetC.getyMin(), -offsetC.getzMin());
        }
        for (StructureObject o : toCount) {
            if (onlyTrackHeads && !o.isTrackHead()) continue;
            BoundingBox offsetP = o.getParent().getRelativeBoundingBox(commonParent).translate(-offsetC.getxMin(), -offsetC.getyMin(), -offsetC.getzMin());
            o.getObject().addOffset(offsetP.getxMin(), offsetP.getyMin(), offsetP.getzMin());
            //logger.debug("add offset: {}, offsetC: {}, offsetP: {}", offsetP, offsetC, o.getParent().getRelativeBoundingBox(commonParent));
            if (o.getBounds().hasIntersection(containerObject.getBounds())) {
                if (proportionInclusion==0) ++count;
                else {
                    if (o.getObject().getVoxels().isEmpty()) continue;
                    double incl = (double)o.getObject().getIntersection(containerObject).size() / (double)o.getObject().getVoxels().size();
                    //logger.debug("inclusion: {}, threshold: {}, container: {}, parent:{}", incl, percentageInclusion, container, o.getParent());
                    if (incl>=proportionInclusion) ++count;
                }
            }
            o.getObject().resetOffset();
        }
        //logger.debug("inclusion count: commont parent: {} container: {}, toTest: {}, result: {}", commonParent, container, toCount.size(), count);
        return count;
    }

    public static int count(StructureObject container, int structureToCount, double proportionInclusion, boolean onlyTrackHeads) {
        if (structureToCount==container.getStructureIdx()) return 1;
        int common = container.getExperiment().getFirstCommonParentStructureIdx(container.getStructureIdx(), structureToCount);
        StructureObject commonParent = container.getParent(common);

        ArrayList<StructureObject> toCount = commonParent.getChildren(structureToCount);
        return count(commonParent, container, toCount, proportionInclusion, onlyTrackHeads);
        
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }
    
}