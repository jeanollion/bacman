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
package processing.dataGeneration;

import boa.gui.imageInteraction.IJImageDisplayer;
import core.Processor;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import plugins.PluginFactory;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.SimpleRotationXY;
import processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class TestPreProcess {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String dbName= "fluo151130_OutputNewScaling";
        //testTransformation("fluo151130_OutputNewScaling", 9, 1, 0);
        //testPreProcessing(, 24, 0, 0, 50, 70);
        testCrop(dbName, 24, 50, true);
    }
    
    public static void testTransformation(String dbName, int fieldIdx, int channelIdx, int time) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        //Processor.setTransformations(f, true);
        InputImagesImpl images = f.getInputImages();
        Image im = images.getImage(channelIdx, time);
        SimpleRotationXY rot = new SimpleRotationXY(5);
        Image res = rot.applyTransformation(channelIdx, time, im);
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(im.setName("input"));
        disp.showImage(res.setName("rotated5"));
    }
    
    public static void testCrop(String dbName, int fieldIdx, int time, boolean flip) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        f.getPreProcessingChain().removeAllTransformations();
        f.getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        f.getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR, 0));
        if (flip) f.getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        f.getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        CropMicroChannels2D.debug=true;
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("input:"+fieldIdx));
        Processor.setTransformations(f, true);
        disp.showImage(f.getInputImages().getImage(0, time).duplicate("output:"+fieldIdx));
    }
    
    
    public static void testPreProcessing(String dbName, int fieldIdx, int channelIdx, int time, int tStart, int tEnd) {
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        MicroscopyField f = db.getExperiment().getMicroscopyField(fieldIdx);
        InputImagesImpl images = f.getInputImages();
        if (time>=tStart) time -=tStart;
        images.subSetTimePoints(tStart, tEnd);
        Image input = images.getImage(channelIdx, time).duplicate("input");
        Processor.setTransformations(f, true);
        Image output = images.getImage(channelIdx, time).setName("output");
        IJImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(input);
        disp.showImage(output);
    }
}
