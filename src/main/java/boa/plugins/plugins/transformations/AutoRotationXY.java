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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.*;
import boa.gui.image_interaction.IJImageDisplayer;
import boa.gui.image_interaction.ImageWindowManagerFactory;

import static boa.core.Processor.logger;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImages;
import static boa.data_structure.input_image.InputImages.getAverageFrame;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.InterpolationScheme;
import boa.image.processing.RadonProjection;
import static boa.image.processing.RadonProjection.getAngleArray;
import static boa.image.processing.RadonProjection.radonProject;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.MultichannelTransformation;
import boa.plugins.ToolTip;
import boa.utils.ArrayUtil;
import boa.utils.Utils;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public class AutoRotationXY implements MultichannelTransformation, ConfigurableTransformation, ToolTip {
    NumberParameter minAngle = new BoundedNumberParameter("Minimal Angle for search", 2, -10, -90, 90);
    NumberParameter maxAngle = new BoundedNumberParameter("Maximal Angle for search", 2, 10, -90, 90);
    NumberParameter precision1 = new BoundedNumberParameter("Angular Precision of first search", 2, 1, 0, null);
    NumberParameter precision2 = new BoundedNumberParameter("Angular Precision", 2, 0.1, 0, 1);
    EnumChoiceParameter<InterpolationScheme> interpolation = new EnumChoiceParameter<>("Interpolation", ImageTransformation.InterpolationScheme.values(), ImageTransformation.InterpolationScheme.BSPLINE5, false);
    ChoiceParameter searchMethod = new ChoiceParameter("Search method", SearchMethod.getValues(), SearchMethod.MAXVAR.getName(), false).setToolTipText("<ul><li><b>"+SearchMethod.MAXVAR.getName()+"</b>: Search for the angle that yields in maximal dispersion of projected values</li><li><b>"+SearchMethod.MAXARTEFACT.getName()+": Search for the angle that yields in the maximal value, reached when optical aberration in phase-contrast images (located at interface with main channel) is perpendicular to the axis</b></li></ul>");
    NumberParameter frameNumber = new BoundedNumberParameter("Number of frame", 0, 10, 0, null).setToolTipText("Number of frames on which the angle should be computed. Resulting angle is the median value of all the angles");
    BooleanParameter removeIncompleteRowsAndColumns = new BooleanParameter("Remove Incomplete rows and columns", true).setToolTipText("If this option is not selected the frame of the image will be enlarged to fit the whole rotated image and filled with zeros");
    FilterSequence prefilters = new FilterSequence("Pre-Filters");
    BooleanParameter maintainMaximum = new BooleanParameter("Maintain Maximum Value", false).setToolTipText("When interpolation with polynomial of degree>1, higher values than maximal value can be created, which can be an issue in case of a saturated image if the saturated value should be preserved. <br />This option will saturate the rotated image to the old maximal value");
    Parameter[] parameters = new Parameter[]{searchMethod, minAngle, maxAngle, precision1, precision2, interpolation, frameNumber, removeIncompleteRowsAndColumns, maintainMaximum, prefilters}; //  
    double rotationAngle = Double.NaN;
    public boolean testMode = false;
    public AutoRotationXY(double minAngle, double maxAngle, double precision1, double precision2, InterpolationScheme interpolation, SearchMethod method) {
        this.minAngle.setValue(minAngle);
        this.maxAngle.setValue(maxAngle);
        this.precision1.setValue(precision1);
        this.precision2.setValue(precision2);
        if (interpolation!=null) this.interpolation.setSelectedEnum(interpolation);
        this.searchMethod.setSelectedItem(method.getName());
        //this.backgroundSubtractionRadius.setValue(backgroundSubtractionRadius);
    }
    
    public AutoRotationXY setPrefilters(boa.plugins.Filter... filters) {
        prefilters.add(filters);
        return this;
    }
    public AutoRotationXY setFrameNumber(int frameNumber) {
        this.frameNumber.setValue(frameNumber);
        return this;
    }
    public AutoRotationXY setRemoveIncompleteRowsAndColumns(boolean remove) {
        this.removeIncompleteRowsAndColumns.setSelected(remove);
        return this;
    }
    public AutoRotationXY setMaintainMaximum(boolean maintain) {
        this.maintainMaximum.setSelected(maintain);
        return this;
    }
    public AutoRotationXY() {}
    
    public boolean isTimeDependent() {
        return false;
    }
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    @Override
    public OUTPUT_SELECTION_MODE getOutputChannelSelectionMode() {
        return OUTPUT_SELECTION_MODE.ALL;
    }

    public double getAngle(Image image) {
        double angle=0;
        if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXVAR.getName())) { 
            angle = computeRotationAngleXY(image, image.sizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), true, false, 0);
        } else if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXARTEFACT.getName())) {
            angle = computeRotationAngleXY(image, image.sizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), false, true, 0);
        }
        return angle;
    }
    public Image rotate(Image image) {
        return ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), getAngle(image), interpolation.getSelectedEnum(), removeIncompleteRowsAndColumns.getSelected());
    }
    List<Image> sinogram1Test, sinogram2Test;
    
    @Override 
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {     
        if (testMode) {
            sinogram1Test = new ArrayList<>();
            sinogram2Test = new ArrayList<>();
        }
        int fn = Math.min(frameNumber.getValue().intValue(), inputImages.getFrameNumber());
        List<Integer> frames;
        if (fn<=1) frames = new ArrayList<Integer>(1){{add(inputImages.getDefaultTimePoint());}};
        else frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, fn); // TODO not necessary for phase contrast
        
        List<Double> angles = frames.stream().map(f -> {
            Image<? extends Image> image = inputImages.getImage(channelIdx, f);
            image = prefilters.filter(image);
            if (image.sizeZ()>1) {
                int plane = inputImages.getBestFocusPlane(f);
                if (plane<0) throw new RuntimeException("Autorotation can only be run on 2D images AND no autofocus algorithm was set");
                image = image.splitZPlanes().get(plane);
            }
            return getAngle(image);
        }).collect(Collectors.toList());
        if (testMode) {
            ImageWindowManagerFactory.showImage(Image.mergeZPlanes(sinogram1Test).setName("Sinogram: first search"));
            ImageWindowManagerFactory.showImage(Image.mergeZPlanes(sinogram2Test).setName("Sinogram: second search"));
            sinogram1Test.clear();
            sinogram2Test.clear();
        }
        rotationAngle = ArrayUtil.median(angles);
        logger.info("AutoRotationXY: median angle: {} among: {}", rotationAngle, Utils.toStringList(Utils.toList(ArrayUtil.generateIntegerArray(fn)), i->"f:"+frames.get(i)+"->"+angles.get(i)));
    }
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (Double.isNaN(rotationAngle)) throw new RuntimeException("Autorotation not configured");
        Image res = ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), rotationAngle, interpolation.getSelectedEnum(), removeIncompleteRowsAndColumns.getSelected());
        if (maintainMaximum.getSelected() && interpolation.getSelectedIndex()>1) {
            double oldMax = image.getMinAndMax(null)[1];
            SaturateHistogram.saturate(oldMax, oldMax, res);
        }
        return res;
    }
    @Override
    public boolean isConfigured(int totalChannelNumber, int totalTimePointNumber) {
        return !Double.isNaN(rotationAngle);
    }
    
    public double[] computeRotationAngleXY(Image image, int z, double ang1, double ang2, double stepSize, float[] proj, boolean var, double filterScale) {

        // initial search
        double[] angles = getAngleArray(ang1, ang2, stepSize);
        double[] angleMax=new double[]{angles[0], angles[0]};
        double max=-1;
        ImageFloat sinogram = null;
        if (testMode) sinogram = new ImageFloat("sinogram search angles: ["+ang1+";"+ang2+"]", angles.length, proj.length, 1);
        for (int angleIdx = 0; angleIdx<angles.length; ++angleIdx) { 
            radonProject(image, z, angles[angleIdx]+90, proj, true);
            if (testMode) paste(proj, sinogram, angleIdx);
            //if (filterScale>0) filter(filterScale, proj);
            double tempMax = var?RadonProjection.var(proj):RadonProjection.max(proj);
            if (tempMax > max) {
                max = tempMax;
                angleMax[0] = angles[angleIdx];
                angleMax[1] = angles[angleIdx];
            } else if (tempMax==max) {
                angleMax[1] = angles[angleIdx];
            }
            //logger.trace("radon projection: computeRotationAngleXY: {}", angleMax);
        }
        if (testMode) {
            if (sinogram1Test.size()<=sinogram2Test.size()) sinogram1Test.add(sinogram);
            else sinogram2Test.add(sinogram);
        }
        angleMax[0] = - angleMax[0];
        angleMax[1] = - angleMax[1];
        return angleMax;
    }
    
    public double computeRotationAngleXY(Image image, int z , double ang1, double ang2, double stepsize1, double stepsize2, boolean var, boolean rotate90, double filterScale) {
        // first search:
        //float[] proj = new float[(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())];
        float[] proj = new float[Math.min(image.sizeX(),image.sizeY())];
        double inc = rotate90?90:0;
        double[] firstSearch = computeRotationAngleXY(image, z, ang1+inc, ang2+inc, stepsize1, proj, var, filterScale);
        double[] secondSearch = computeRotationAngleXY(image, z, -firstSearch[0]-2*stepsize1, -firstSearch[1]+2*stepsize1, stepsize2, proj, var, filterScale);
        logger.debug("radon rotation search: first: {} second: {}", firstSearch, secondSearch);
        return (secondSearch[0]+secondSearch[1])/2d+inc;
    }
    
    private static void paste(float[] proj, ImageFloat image, int x) {
        for (int y = 0; y<proj.length; ++y) image.setPixel(x, y, 0, proj[y]);
    }

    @Override
    public String getToolTipText() {
        return "Align Microchannel sides along Y-axis";
    }
    
    public enum SearchMethod {
        MAXVAR("Fluo Microchannel"),
        MAXARTEFACT("Phase Microchannel Artifact");
        private final String name;
        SearchMethod(String name){this.name=name;}
        public static String[] getValues() {
            String[] values = new String[values().length]; int idx = 0;
            for (SearchMethod s : values()) values[idx++]=s.name;
            return values;
        }

        public String getName() {
            return name;
        }
        
    }
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
