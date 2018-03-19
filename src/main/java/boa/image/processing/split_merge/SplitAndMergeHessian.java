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
package boa.image.processing.split_merge;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Voxel;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.processing.ImageFeatures;
import boa.image.processing.watershed.WatershedTransform;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import static boa.plugins.Plugin.logger;
import boa.plugins.plugins.trackers.ObjectIdxTracker;
import boa.image.processing.clustering.ClusterCollection;
import boa.image.processing.clustering.InterfaceRegionImpl;
import boa.image.processing.clustering.RegionCluster;
import boa.image.processing.split_merge.SplitAndMergeHessian.Interface;
import boa.utils.ArrayUtil;
import java.util.stream.Stream;

/**
 *
 * @author jollion
 */
public class SplitAndMergeHessian extends SplitAndMerge<Interface> {
    Image hessian, seedCreationMap, watershedMap;
    
    public final double splitThresholdValue, hessianScale;
    Function<Interface, Double> interfaceValue;

    public SplitAndMergeHessian(Image input, double splitThreshold, double hessianScale) {
        super(input);
        splitThresholdValue=splitThreshold;
        this.hessianScale=hessianScale;
        interfaceValue = i->{
            if (i.getVoxels().isEmpty()) {
                return Double.NaN;
            } else {
                double[] sum = new double[2];
                Stream.concat(i.voxels.stream(), i.duplicatedVoxels.stream()).forEach(v->{
                    sum[0]+=hessian.getPixel(v.x, v.y, v.z);
                    sum[1]+=intensityMap.getPixel(v.x, v.y, v.z);
                });
                return sum[0] / sum[1];
            }
        };
    }
    public SplitAndMergeHessian setInterfaceValue(Function<Interface, Double> interfaceValue) {
        this.interfaceValue=interfaceValue;
        return this;
    }
    public SplitAndMergeHessian setHessian(Image hessian) {
        this.hessian = hessian;
        return this;
    }
    
    public SplitAndMergeHessian setWatershedMap(Image wsMap, boolean isEdgeMap) {
        this.watershedMap = wsMap;
        this.wsMapIsEdgeMap=isEdgeMap;
        return this;
    }
    public Image getHessian() {
        if (hessian ==null) {
            synchronized(this) {
                if (hessian==null) hessian=ImageFeatures.getHessian(intensityMap, hessianScale, false)[0].setName("hessian");
            }
        }
        return hessian;
    }
    @Override public Image getWatershedMap() {
        return watershedMap!=null ? watershedMap : getHessian();
    }
    @Override public Image getSeedCreationMap() {
        return seedCreationMap!=null?seedCreationMap:getWatershedMap();
    }
    public SplitAndMergeHessian setSeedCreationMap(Image seedCreationMap, boolean localMin) {
        this.seedCreationMap = seedCreationMap;
        this.localMinOnSeedMap=localMin;
        return this;
    }
    
    @Override
    protected ClusterCollection.InterfaceFactory<Region, Interface> createFactory() {
        getHessian(); // ensure hessian creation, as hessian is needed for interface value computation
        return (Region e1, Region e2) -> new Interface(e1, e2);
    }
    
    
    public class Interface extends InterfaceRegionImpl<Interface> implements RegionCluster.InterfaceVoxels<Interface> {
        public double value;
        Set<Voxel> voxels;
        Set<Voxel> duplicatedVoxels;
        public Interface(Region e1, Region e2) {
            super(e1, e2);
            voxels = new HashSet<>();
            duplicatedVoxels = new HashSet<>();
        }

        @Override public void updateInterface() {
            value = interfaceValue.apply(this);
        }

        @Override 
        public void fusionInterface(Interface otherInterface, Comparator<? super Region> elementComparator) {
            //fusionInterfaceSetElements(otherInterface, elementComparator);
            Interface other = otherInterface;
            voxels.addAll(other.voxels); 
            duplicatedVoxels.addAll(other.duplicatedVoxels);
            value = Double.NaN;// updateSortValue will be called afterwards
        }

        @Override
        public boolean checkFusion() {
            // criterion = - hessian @ border / intensity @ border < threshold
            if (testMode) logger.debug("check fusion: {}+{}, size: {}, value: {}, threhsold: {}, fusion: {}", e1.getLabel(), e2.getLabel(), voxels.size(), value, splitThresholdValue, value<splitThresholdValue);
            return value<splitThresholdValue;
        }

        @Override
        public void addPair(Voxel v1, Voxel v2) {
            if (foregroundMask==null || !foregroundMask.contains(v1.x, v1.y, v1.z)) duplicatedVoxels.add(v2);
            else  voxels.add(v1);
            voxels.add(v2);
        }

        @Override
        public int compareTo(Interface t) {
            int c = Double.compare(value, t.value); // increasing values
            if (c==0) return super.compareElements(t, RegionCluster.regionComparator);
            else return c;
        }
        @Override
        public Collection<Voxel> getVoxels() {
            return voxels;
        }

        @Override
        public String toString() {
            return "Interface: " + e1.getLabel()+"+"+e2.getLabel()+ " sortValue: "+value;
        } 
    }
}
