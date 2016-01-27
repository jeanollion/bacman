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
package processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import static core.Processor.logger;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Voxel;
import image.BlankMask;
import image.Image;
import image.ImageInteger;
import image.ImageLabeller;
import image.ImageMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import processing.neighborhood.EllipsoidalNeighborhood;
import processing.neighborhood.Neighborhood;

/**
 *
 * @author jollion
 */
public class MultiScaleWatershedTransform {
    final protected TreeSet<Voxel> heap;
    final protected Spot[] spots; // map label -> spot (spots[0]==null)
    protected int spotNumber;
    final protected Image[] watershedMaps;
    final protected ImageInteger segmentedMap;
    final protected ImageMask mask;
    final boolean is3D;
    final boolean decreasingPropagation;
    PropagationCriterion propagationCriterion;
    FusionCriterion fusionCriterion;
    
    public static ObjectPopulation watershed(Image[] watershedMaps, ImageMask mask, List<Object3D>[] regionalExtrema, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion) {
        MultiScaleWatershedTransform wt = new MultiScaleWatershedTransform(watershedMaps, mask, regionalExtrema, decreasingPropagation, propagationCriterion, fusionCriterion);
        wt.run();
        return wt.getObjectPopulation();
    }
    
    public static ObjectPopulation watershed(Image[] watershedMaps, ImageMask mask, ImageMask[] seeds, boolean decreasingPropagation) {
        if (watershedMaps.length!=seeds.length) throw new IllegalArgumentException("seeds and watershed maps should be within same scale-space");
        return watershed(watershedMaps, mask, getRegionalExtrema(seeds), decreasingPropagation, null, null);
    }
    public static ObjectPopulation watershed(Image[] watershedMaps, ImageMask mask, ImageMask[] seeds, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion) {
        return watershed(watershedMaps, mask, getRegionalExtrema(seeds), decreasingPropagation, propagationCriterion, fusionCriterion);
    }
    
    private static List<Object3D>[] getRegionalExtrema(ImageMask[] seeds) {
        List[] res = new List[seeds.length];
        for (int i = 0; i<res.length; ++i) res[i] = ImageLabeller.labelImageList(seeds[i]);
        return (List<Object3D>[]) res;
    }
    
    public MultiScaleWatershedTransform(Image[] watershedMaps, ImageMask mask, List<Object3D>[] regionalExtrema, boolean decreasingPropagation, PropagationCriterion propagationCriterion, FusionCriterion fusionCriterion) {
        if (mask==null) mask=new BlankMask("", watershedMaps[0]);
        this.decreasingPropagation = decreasingPropagation;
        heap = decreasingPropagation ? new TreeSet<Voxel>(Voxel.getInvertedComparator()) : new TreeSet<Voxel>();
        this.mask=mask;
        this.watershedMaps=watershedMaps;
        spotNumber = 0;
        for (List<Object3D> l : regionalExtrema) spotNumber += l.size();
        spots = new Spot[spotNumber+1];
        segmentedMap = ImageInteger.createEmptyLabelImage("segmentationMap", spots.length, mask);
        int spotIdx = 1;
        for (int i = 0; i<regionalExtrema.length; ++i) {
            for (Object3D o : regionalExtrema[i]) {
                spots[spotIdx] = new Spot(spotIdx, i, o.getVoxels());
                ++spotIdx;
            }
        }
        logger.trace("watershed transform: number of seeds: {} segmented map type: {}", spotNumber, segmentedMap.getClass().getSimpleName());
        is3D=mask.getSizeZ()>1;   
        if (propagationCriterion==null) setPropagationCriterion(new DefaultPropagationCriterion());
        else setPropagationCriterion(propagationCriterion);
        if (fusionCriterion==null) setFusionCriterion(new DefaultFusionCriterion());
        else setFusionCriterion(fusionCriterion);
    }
    
    public MultiScaleWatershedTransform setFusionCriterion(FusionCriterion fusionCriterion) {
        this.fusionCriterion=fusionCriterion;
        fusionCriterion.setUp(this);
        return this;
    }
    
    public MultiScaleWatershedTransform setPropagationCriterion(PropagationCriterion propagationCriterion) {
        this.propagationCriterion=propagationCriterion;
        propagationCriterion.setUp(this);
        return this;
    }
    
    public void run() {
        for (Spot s : spots) {
            if (s!=null) for (Voxel v : s.voxels) heap.add(v);
        }
        EllipsoidalNeighborhood neigh = mask.getSizeZ()>1?new EllipsoidalNeighborhood(1.5, 1.5, true) : new EllipsoidalNeighborhood(1.5, true);
        while (!heap.isEmpty()) {
            Voxel v = heap.pollFirst();
            Spot currentSpot = spots[segmentedMap.getPixelInt(v.x, v.y, v.z)];
            Voxel next;
            for (int i = 0; i<neigh.getSize(); ++i) {
                next = new Voxel(v.x+neigh.dx[i], v.y+neigh.dy[i], v.z+neigh.dz[i]);
                //logger.trace("voxel: {} next: {}, mask contains: {}, insideMask: {}",v, next, mask.contains(next.x, next.y, next.getZ()) , mask.insideMask(next.x, next.y, next.getZ()));
                if (mask.contains(next.x, next.y, next.z) && mask.insideMask(next.x, next.y, next.z)) currentSpot=propagate(currentSpot,v, next);
            }
        }
    }
    
    public ImageInteger getLabelImage() {return segmentedMap;}
    
    public ObjectPopulation getObjectPopulation() {
        //int nb = 0;
        //for (Spot s : wt.spots) if (s!=null) nb++;
        ArrayList<Object3D> res = new ArrayList<Object3D>(spotNumber);
        int label = 1;
        for (Spot s : spots) if (s!=null) res.add(s.toObject3D(label++));
        return new ObjectPopulation(res, mask);
    }
    
    protected Spot propagate(Spot currentSpot, Voxel currentVoxel, Voxel nextVox) { /// nextVox.value = 0 at this step
        int label = segmentedMap.getPixelInt(nextVox.x, nextVox.y, nextVox.z);
        if (label!=0) {
            if (label!=currentSpot.label) {
                Spot s2 = spots[label];
                if (fusionCriterion.checkFusionCriteria(currentSpot, s2, currentVoxel)) return currentSpot.fusion(s2);
                else heap.remove(nextVox); // FIXME ??et dans les autres directions?
            }
        } else {
            nextVox.value=watershedMaps[currentSpot.scale].getPixel(nextVox.x, nextVox.y, nextVox.z);
            if (propagationCriterion.continuePropagation(currentVoxel, nextVox)) {
                currentSpot.addVox(nextVox);
                heap.add(nextVox);
            }
        }
        return currentSpot;
    }
    
    protected class Spot {
        public ArrayList<Voxel> voxels;
        int label;
        int scale;
        //Voxel seed;
        /*public Spot(int label, Voxel seed) {
            this.label=label;
            this.voxels=new ArrayList<Voxel>();
            voxels.add(seed);
            seed.value=watershedMaps.getPixel(seed.x, seed.y, seed.getZ());
            heap.add(seed);
            this.seed=seed;
            segmentedMap.setPixel(seed.x, seed.y, seed.getZ(), label);
        }*/
        public Spot(int label, int scale, ArrayList<Voxel> voxels) {
            this.label=label;
            this.scale=scale;
            this.voxels=voxels;
            for (Voxel v :voxels) {
                v.value=watershedMaps[scale].getPixel(v.x, v.y, v.z);
                heap.add(v);
                segmentedMap.setPixel(v.x, v.y, v.z, label);
            }
            //this.seed=seeds.get(0);
            //logger.debug("spot: {} seed size: {} seed {}",label, seeds.size(), seed);
            
        }
        
        public void setLabel(int label) {
            this.label=label;
            for (Voxel v : voxels) segmentedMap.setPixel(v.x, v.y, v.z, label);
        }

        public Spot fusion(Spot spot) {
            if (spot.label<label) return spot.fusion(this);
            spots[spot.label]=null;
            spotNumber--;
            spot.setLabel(label);
            this.voxels.addAll(spot.voxels); // pas besoin de check si voxels.contains(v) car les spots ne se recouvrent pas            //update seed: lowest seedIntensity
            //if (watershedMaps.getPixel(seed.x, seed.y, seed.getZ())>watershedMaps.getPixel(spot.seed.x, spot.seed.y, spot.seed.getZ())) seed=spot.seed;
            return this;
        }
        
        public void addVox(Voxel v) {
            if (!voxels.contains(v)) {
                voxels.add(v);
                segmentedMap.setPixel(v.x, v.y, v.z, label);
            }
        }
        
        public Object3D toObject3D(int label) {
            return new Object3D(voxels, label, watershedMaps[scale].getScaleXY(), watershedMaps[scale].getScaleZ());
        }
        
    }
    public interface PropagationCriterion {
        public void setUp(MultiScaleWatershedTransform instance);
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox);
    }
    public static class DefaultPropagationCriterion implements PropagationCriterion {
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return true;
        }
        @Override public void setUp(MultiScaleWatershedTransform instance) {}
    }
    public static class MonotonalPropagation implements PropagationCriterion {
        boolean decreasingPropagation;
        @Override public void setUp(MultiScaleWatershedTransform instance) {
            setPropagationDirection(instance.decreasingPropagation);
        }
        public MonotonalPropagation setPropagationDirection(boolean decreasingPropagation) {
            this.decreasingPropagation=decreasingPropagation;
            return this;
        }
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            if (decreasingPropagation) return (nextVox.value<=currentVox.value);
            else return (nextVox.value>=currentVox.value);
        }
    }
    public static class ThresholdPropagation implements PropagationCriterion {
        Image image;
        double threshold;
        boolean stopWhenInferior;
        public ThresholdPropagation(Image image, double threshold, boolean stopWhenInferior) {
            this.image=image;
            this.threshold=threshold;
            this.stopWhenInferior=stopWhenInferior;
        }
        @Override public void setUp(MultiScaleWatershedTransform instance) {}
        @Override public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return stopWhenInferior?image.getPixel(nextVox.x, nextVox.y, nextVox.z)>threshold:image.getPixel(nextVox.x, nextVox.y, nextVox.z)<threshold;
        }
    }
    public static class ThresholdPropagationOnWatershedMap implements PropagationCriterion {
        boolean stopWhenInferior;
        double threshold;
        @Override public void setUp(MultiScaleWatershedTransform instance) {
            setStopWhenInferior(instance.decreasingPropagation);
        }
        public ThresholdPropagationOnWatershedMap setStopWhenInferior(boolean stopWhenInferior) {
            this.stopWhenInferior=stopWhenInferior;
            return this;
        }
        public ThresholdPropagationOnWatershedMap(double threshold) {
            this.threshold=threshold;
        }
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            return stopWhenInferior?nextVox.value>threshold:nextVox.value<threshold;
        }
        
    }
    public static class MultiplePropagationCriteria implements PropagationCriterion {
        PropagationCriterion[] criteria;
        public MultiplePropagationCriteria(PropagationCriterion... criteria) {
            this.criteria=criteria;
        } 
        @Override public void setUp(MultiScaleWatershedTransform instance) {
            for (PropagationCriterion c : criteria) c.setUp(instance);
        }
        public boolean continuePropagation(Voxel currentVox, Voxel nextVox) {
            for (PropagationCriterion p : criteria) if (!p.continuePropagation(currentVox, nextVox)) return false;
            return true;
        }
    }
    
    public interface FusionCriterion {
        public void setUp(MultiScaleWatershedTransform instance);
        public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel);
    }
    public static class DefaultFusionCriterion implements FusionCriterion {
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return false;
        }
        @Override public void setUp(MultiScaleWatershedTransform instance) {}
    }
    public static class SizeFusionCriterion implements FusionCriterion {
        int minimumSize;
        public SizeFusionCriterion(int minimumSize) {
            this.minimumSize=minimumSize;
        }
        @Override public void setUp(MultiScaleWatershedTransform instance) {}
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return s1.voxels.size()<minimumSize || s2.voxels.size()<minimumSize;
        }
    }
    public static class NumberFusionCriterion implements FusionCriterion {
        int numberOfSpots;
        MultiScaleWatershedTransform instance;
        public NumberFusionCriterion(int minNumberOfSpots) {
            this.numberOfSpots=minNumberOfSpots;
        }
        @Override public void setUp(MultiScaleWatershedTransform instance) {this.instance=instance;}
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            return instance.spotNumber>numberOfSpots;
        }
    }
    public static class MultipleFusionCriteria implements FusionCriterion {
        FusionCriterion[] criteria;
        public MultipleFusionCriteria(FusionCriterion... criteria) {
            this.criteria=criteria;
        } 
        @Override public void setUp(MultiScaleWatershedTransform instance) {
            for (FusionCriterion f : criteria) f.setUp(instance);
        }
        @Override public boolean checkFusionCriteria(Spot s1, Spot s2, Voxel currentVoxel) {
            if (criteria.length==0) return false;
            for (FusionCriterion c : criteria) if (!c.checkFusionCriteria(s1, s2, currentVoxel)) return false;
            return true;
        }
    }
}