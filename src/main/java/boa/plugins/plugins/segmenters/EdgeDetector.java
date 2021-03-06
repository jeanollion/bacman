/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.segmenters;

import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.Voxel;
import ij.process.AutoThresholder;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageLabeller;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import boa.measurement.BasicMeasurements;
import boa.plugins.PreFilter;
import boa.plugins.Segmenter;
import boa.plugins.Thresholder;
import boa.plugins.plugins.pre_filters.ImageFeature;
import boa.plugins.plugins.thresholders.BackgroundThresholder;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import boa.image.processing.Filters;
import boa.image.processing.ImageFeatures;
import boa.image.processing.split_merge.SplitAndMergeHessian;
import boa.image.processing.watershed.WatershedTransform;
import boa.image.processing.watershed.WatershedTransform.WatershedConfiguration;
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.plugins.SimpleThresholder;
import boa.utils.Utils;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
/**
 *
 * @author Jean Ollion
 */
public class EdgeDetector implements Segmenter, ToolTip {
    public static enum THLD_METHOD {
        INTENSITY_MAP("Intensity Map"), VALUE_MAP("Value Map"), NO_THRESHOLDING("No thresholding");
        String name;
        private THLD_METHOD(String name) {
            this.name = name;
        }
        public String getName() {return name;}
        public static THLD_METHOD getValue(String name) {
            try {
                return Arrays.stream(THLD_METHOD.values()).filter(e->e.getName().equals(name)).findAny().get();
            } catch (Exception e) {
                throw new IllegalArgumentException("Method not found");
            }
        }
    }
    protected PreFilterSequence watershedMap = new PreFilterSequence("Watershed Map").add(new ImageFeature().setFeature(ImageFeature.Feature.StructureMax).setScale(1.5).setSmoothScale(2)).setToolTipText("Watershed map, separation between regions are at area of maximal intensity of this map");
    public PluginParameter<SimpleThresholder> threshold = new PluginParameter<>("Threshold", SimpleThresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false).setToolTipText("Threshold method used to remove background regions");
    ChoiceParameter thresholdMethod = new ChoiceParameter("Remove background method", Utils.transform(THLD_METHOD.values(), i->new String[i], e->e.getName()), THLD_METHOD.VALUE_MAP.getName(), false).setToolTipText("<html>Intensity Map: compute threshold on raw intensity map and removes regions whose median value is under the threhsold<br />Value Map: same as Intensity map but threshold is computed on an image where all pixels values are replaced by the median value of each region<br /><pre>Secondary Map: This method is designed to robustly threshold foreground objects and regions located between foreground objects. Does only work in case forground objects are of comparable intensities<br />1) Ostus's method is applied on on the image where pixels values are replaced by median value of eache region. <br />2) Ostus's method is applied on on the image where pixels values are replaced by median value of secondary map of each region. Typically using Hessian Max this allows to select regions in between two foreground objects or directly connected to foreground <br />3) A map with regions that are under threshold in 1) and over threshold in 2) ie regions that are not foreground but are either in between two objects or connected to one objects. The histogram of this map is computed and threshold is set in the middle of the largest histogram zone without objects</pre> </html>");
    ConditionalParameter thresholdCond = new ConditionalParameter(thresholdMethod).setDefaultParameters(new Parameter[]{threshold});
    NumberParameter seedRadius = new BoundedNumberParameter("Seed Radius", 1, 1.5, 1, null);
    NumberParameter minSizePropagation = new BoundedNumberParameter("Min Size Propagation", 0, 0, 0, null);
    BooleanParameter darkBackground = new BooleanParameter("Dark Background", true);
    
    Consumer<Image> addTestImage;
    
    // variables
    Image wsMap;
    ImageInteger seedMap;
    Image watershedPriorityMap;
    String toolTip = "<html>Segment region at maximal values of the watershed map; <br />"
            + "1) Partition of the whole image using classical watershed seeded on all regional minima of the watershed map. <br />"
            + "2) Suppression of background regions depending on the selected metohd; <br />"
            + "</html>";
    
    @Override
    public String getToolTipText() {return toolTip;}

    public void setTestMode(Consumer<Image> addTestImage) {
        this.addTestImage = addTestImage;
    }
    
    public PreFilterSequence getWSMapSequence() {
        return this.watershedMap;
    }
    
    public Image getWsMap(Image input, ImageMask mask) {
        if (wsMap==null) wsMap = watershedMap.filter(input, mask);
        return wsMap;
    }

    public ImageInteger getSeedMap(Image input,  ImageMask mask) {
        if (seedMap==null) seedMap = Filters.localExtrema(getWsMap(input, mask), null, false, mask, Filters.getNeighborhood(seedRadius.getValue().doubleValue(), getWsMap(input, mask)));
        return seedMap;
    }
    public EdgeDetector setSeedRadius(double radius) {
        this.seedRadius.setValue(radius);
        return this;
    }
    public EdgeDetector setMinSizePropagation(int minSize) {
        this.minSizePropagation.setValue(minSize);
        return this;
    }
    public EdgeDetector setIsDarkBackground(boolean dark) {
        this.darkBackground.setSelected(dark);
        return this;
    }
    public EdgeDetector setWsMap(Image wsMap) {
        this.wsMap = wsMap;
        return this;
    }
    public EdgeDetector setWsPriorityMap(Image map) {
        this.watershedPriorityMap = map;
        return this;
    }
    public EdgeDetector setSeedMap(ImageInteger seedMap) {
        this.seedMap = seedMap;
        return this;
    }
    
    public Image getWsPriorityMap(Image input, StructureObjectProcessing parent) {
        if (this.watershedPriorityMap==null) watershedPriorityMap = ImageFeatures.gaussianSmooth(input, 2, false); // TODO parameter?
        return watershedPriorityMap;
    }
    public EdgeDetector setPreFilters(List<PreFilter> prefilters) {
        this.watershedMap.removeAllElements();
        this.watershedMap.add(prefilters);
        return this;
    }
    public EdgeDetector setPreFilters(PreFilter... prefilters) {
        this.watershedMap.removeAllElements();
        this.watershedMap.add(prefilters);
        return this;
    }
    public EdgeDetector setThresholder(SimpleThresholder thlder) {
        this.threshold.setPlugin(thlder);
        return this;
    }
    public EdgeDetector setThrehsoldingMethod(THLD_METHOD method) {
        this.thresholdMethod.setSelectedItem(method.getName());
        return this;
    }
    @Override
    public RegionPopulation runSegmenter(Image input, int structureIdx, StructureObjectProcessing parent) {
        return run(input, parent.getMask());
    }
    public RegionPopulation partitionImage(Image input, ImageMask mask) {
        int minSizePropagation = this.minSizePropagation.getValue().intValue();
        WatershedConfiguration config = new WatershedConfiguration().lowConectivity(false);
        if (minSizePropagation>0) config.fusionCriterion(new WatershedTransform.SizeFusionCriterion(minSizePropagation));
        //config.propagation(WatershedTransform.PropagationType.DIRECT);
        RegionPopulation res =  WatershedTransform.watershed(getWsMap(input, mask), mask, Arrays.asList(ImageLabeller.labelImage(getSeedMap(input, mask))), config);
        if (addTestImage!=null) {
            addTestImage.accept(res.getLabelMap().duplicate("EdgeDetector: Segmented Regions"));
            addTestImage.accept(seedMap.setName("EdgeDetector: Seeds"));
            addTestImage.accept(wsMap.setName("EdgeDetector: Watershed Map"));
        }
        return res;
    }
    public RegionPopulation run(Image input, ImageMask mask) {
        RegionPopulation allRegions = partitionImage(input, mask);
        filterRegions(allRegions, input, mask);
        return allRegions;
    }
    
    public void filterRegions(RegionPopulation pop, Image input, ImageMask mask) {
        switch (THLD_METHOD.getValue(this.thresholdMethod.getSelectedItem())) {
            case INTENSITY_MAP: {
                    double thld = threshold.instanciatePlugin().runSimpleThresholder(input, mask);
                    if (addTestImage!=null) addTestImage.accept(generateRegionValueMap(pop, input).setName("Intensity value Map"));
                    pop.filter(new RegionPopulation.MedianIntensity(thld, darkBackground.getSelected(), input));
                    return;
            } case VALUE_MAP:
            default: {
                    Map<Region, Double> values = pop.getRegions().stream().collect(Collectors.toMap(o->o, valueFunction(input)));
                    Image valueMap = generateRegionValueMap(input, values);
                    double thld = threshold.instanciatePlugin().runSimpleThresholder(valueMap , mask);
                    if (addTestImage!=null) addTestImage.accept(valueMap.setName("EdgeDetector: Intensity value Map"));
                    if (darkBackground.getSelected()) values.entrySet().removeIf(e->e.getValue()>=thld);
                    else values.entrySet().removeIf(e->e.getValue()<=thld);
                    pop.getRegions().removeAll(values.keySet());
                    pop.relabel(true);
                    return;
            } case NO_THRESHOLDING: { 
                return;
            }
        }
    } 

    public static Image generateRegionValueMap(RegionPopulation pop, Image image) {
        Map<Region, Double> objectValues = pop.getRegions().stream().collect(Collectors.toMap(o->o, valueFunction(image)));
        return generateRegionValueMap(image, objectValues);
    }

    public static Image generateRegionValueMap(ImageProperties image, Map<Region, Double> objectValues) {
        Image valueMap = new ImageFloat("Value per region", image);
        for (Map.Entry<Region, Double> e : objectValues.entrySet()) {
            for (Voxel v : e.getKey().getVoxels()) valueMap.setPixel(v.x, v.y, v.z, e.getValue());
        }
        return valueMap;
    }

    protected static Function<Region, Double> valueFunction(Image image) { // default: median value
        return r->BasicMeasurements.getQuantileValue(r, image, 0.5)[0];
    }
    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{watershedMap, threshold, thresholdCond};
    }
    
}
