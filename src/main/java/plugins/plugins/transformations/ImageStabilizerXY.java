/*
 * Copyright (C) 2015 nasique
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
package plugins.plugins.transformations;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.TimePointParameter;
import dataStructure.containers.InputImages;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import plugins.Registration;
import static plugins.plugins.transformations.ImageStabilizerCore.combine;
import processing.ImageTransformation;

/**
 *
 * @author nasique
 */
public class ImageStabilizerXY implements Registration {
    // TODO affine
    TimePointParameter ref = new TimePointParameter("Reference time point", 50, false, false);
    ChoiceParameter transformationType = new ChoiceParameter("Transformation", new String[]{"Translation"}, "Translation", false); //, "Affine"
    ChoiceParameter pyramidLevel = new ChoiceParameter("Pyramid Level", new String[]{"0", "1", "2", "3", "4"}, "4", false);
    NumberParameter alpha = new NumberParameter("Template Update Coefficient [0-1]", 2, 0.9);
    NumberParameter maxIter = new NumberParameter("Maximum Iterations", 0, 200);
    NumberParameter tol = new NumberParameter("Error Tolerance", 7, 1e-7);
    Parameter[] parameters = new Parameter[]{ref, transformationType, pyramidLevel, alpha, maxIter, tol};
    double[][] translationTXY;
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image imageRef = inputImages.getImage(channelIdx, ref.getSelectedIndex());
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        FloatProcessor ipFloatRef = getFloatProcessor(imageRef, true);
        FloatProcessor trans=null;
        if (alpha.getValue().doubleValue()<1) trans  = new FloatProcessor(imageRef.getSizeX(), imageRef.getSizeY());
        translationTXY = new double[inputImages.getTimePointNumber()][];
        double a = alpha.getValue().doubleValue();
        for (int t = 0; t<inputImages.getTimePointNumber(); ++t) {
            if (t==ref.getSelectedIndex()) {
                translationTXY[t] = new double[2];
            } else {
                FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
                double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, ipFloatRef, pyramids[0], pyramids[1], maxIter.getValue().intValue(), tol.getValue().doubleValue());
                translationTXY[t] = new double[]{wp[0][0], wp[1][0]};
                //update template 
                if (a<1) {
                    ImageStabilizerCore.warpTranslation(trans, currentTime, wp);
                    ImageStabilizerCore.combine(ipFloatRef, trans, a);
                }
            }
        }
    }
    
    private FloatProcessor getFloatProcessor(Image image, boolean duplicate) {
        if (image.getSizeZ()>1) image = image.getZPlane((int)(image.getSizeZ()/2.0+0.5)); //select middle slice only
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image);
        else if (duplicate) image = image.duplicate("");
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        return (FloatProcessor)impRef.getProcessor();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (translationTXY[timePoint][0]==0 && translationTXY[timePoint][1]==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image);
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        ImageStack is = impRef.getImageStack();
        float[][] outPixels = new float[image.getSizeZ()][];
        FloatProcessor temp = new FloatProcessor(image.getSizeX(), image.getSizeY());
        double[][] wp = new double[][]{{translationTXY[timePoint][0]}, {translationTXY[timePoint][1]}};
        for (int z = 0; z<image.getSizeZ(); ++z) {
            ImageStabilizerCore.warpTranslation(temp, is.getProcessor(z), wp);
            outPixels[z]=(float[])temp.getPixels();
            temp = (FloatProcessor)is.getProcessor(z);
        }
        return new ImageFloat(image.getName(), image.getSizeX(), outPixels);
    }

    public Object[] getConfigurationData() {
        return this.translationTXY;
    }

    public boolean isTimeDependent() {
        return true;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    
}
