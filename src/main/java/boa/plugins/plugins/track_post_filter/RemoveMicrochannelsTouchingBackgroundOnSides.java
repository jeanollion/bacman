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
package boa.plugins.plugins.track_post_filter;

import boa.ui.ManualEdition;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.Voxel;
import boa.image.BlankMask;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageLabeller;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import boa.plugins.TrackPostFilter;

/**
 * When a rotation occurs > 0 filled background can be added, if background on the image is not centered, this can lead to arfifacts. 
 * This transformation is intended to remove microchannel track if they contain 0-filled background.
 * @author jollion
 */
public class RemoveMicrochannelsTouchingBackgroundOnSides implements TrackPostFilter {
    StructureParameter backgroundStructure = new StructureParameter("Background");
    NumberParameter XMargin = new BoundedNumberParameter("X margin", 0, 8, 0, null).setToolTipText("To avoid removing microchannels touching background from the upper or lower side, this will cut the upper and lower part of the microchannel. In pixels");
    public RemoveMicrochannelsTouchingBackgroundOnSides() {}
    public RemoveMicrochannelsTouchingBackgroundOnSides(int backgroundStructureIdx) {
        this.backgroundStructure.setSelectedStructureIdx(backgroundStructureIdx);
    }
    
    @Override
    public void filter(int structureIdx, List<StructureObject> parentTrack) {
        if (backgroundStructure.getSelectedStructureIdx()<0) throw new IllegalArgumentException("Background structure not configured");
        if (parentTrack.isEmpty()) return;
        Map<Integer, StructureObject> parentTrackByF = StructureObjectUtils.splitByFrame(parentTrack);
        Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(parentTrack, structureIdx);
        if (allTracks.isEmpty()) return;
        List<StructureObject> objectsToRemove = new ArrayList<>();
        // left-most
        StructureObject object = Collections.min(allTracks.keySet(), (o1, o2)->Double.compare(o1.getBounds().xMean(), o2.getBounds().xMean()));
        Image image = parentTrackByF.get(object.getFrame()).getRawImage(backgroundStructure.getSelectedStructureIdx());
        RegionPopulation bck = ImageLabeller.labelImage(Arrays.asList(new Voxel[]{new Voxel(0, 0, 0), new Voxel(0, image.sizeY()-1, 0 )}), image, true);
        //ImageWindowManagerFactory.showImage(bck.getLabelMap().duplicate("left background"));
        if (intersectWithBackground(object, bck)) objectsToRemove.addAll(allTracks.get(object));
        
        // right-most
        if (allTracks.size()>1) {
            object = Collections.max(allTracks.keySet(), (o1, o2)->Double.compare(o1.getBounds().xMean(), o2.getBounds().xMean()));
            image = parentTrackByF.get(object.getFrame()).getRawImage(backgroundStructure.getSelectedStructureIdx());
            bck = ImageLabeller.labelImage(Arrays.asList(new Voxel[]{new Voxel(image.sizeX()-1, 0, 0), new Voxel(image.sizeX()-1, image.sizeY()-1, 0 )}), image, true);
            //ImageWindowManagerFactory.showImage(bck.getLabelMap().duplicate("right background"));
            if (intersectWithBackground(object, bck)) objectsToRemove.addAll(allTracks.get(object));
        }
        if (!objectsToRemove.isEmpty()) ManualEdition.deleteObjects(null, objectsToRemove, ManualEdition.ALWAYS_MERGE, false);
    }
    private boolean intersectWithBackground(StructureObject object, RegionPopulation bck) {
        bck.filter(o->o.size()>10); // 
        Region cutObject =object.getRegion();
        int XMargin = this.XMargin.getValue().intValue();
        if (XMargin>0 && object.getBounds().sizeY()>2*XMargin) {
            BoundingBox bds = object.getBounds();
            cutObject = new Region(new BlankMask( bds.sizeX(), bds.sizeY()-2*XMargin, bds.sizeZ(), bds.xMin(), bds.yMin()+XMargin, bds.zMin(), object.getScaleXY(), object.getScaleZ()), cutObject.getLabel(), cutObject.is2D());
        }
        for (Region o : bck.getRegions()) {
            int inter = o.getOverlapMaskMask(cutObject, null, null);
            if (inter>0) {
                logger.debug("remove track: {} (object: {}), intersection with bck object: {}", object, cutObject.getBounds(), inter);
                return true;
            }
        }
        return false;
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{backgroundStructure, XMargin};
    }
    
}
