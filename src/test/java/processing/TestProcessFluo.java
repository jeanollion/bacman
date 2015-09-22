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

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import static core.Processor.preProcessImages;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import image.Image;
import image.ImageFormat;
import image.ImageWriter;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import plugins.PluginFactory;
import plugins.plugins.preFilter.IJSubtractBackground;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannels2D;
import plugins.plugins.transformations.Flip;
import plugins.plugins.transformations.ImageStabilizerXY;
import processing.ImageTransformation.InterpolationScheme;
import static utils.Utils.deleteDirectory;

/**
 *
 * @author jollion
 */
public class TestProcessFluo {
    Experiment xp;
    
    public static void main(String[] args) {
        //new TestProcessFluo().testRotation();
        
        new TestProcessFluo().testStabilizer();
        
    }
    @Test
    public void testImport() {
        PluginFactory.findPlugins("plugins.plugins");
        xp = new Experiment("testXP", new Structure("structure"));
        xp.setImportImageMethod(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        xp.getChannelImages().insert(new ChannelImage("trans", "_REF"), new ChannelImage("fluo", ""));
        xp.setOutputImageDirectory("/data/Images/Fluo/OutputTest");
        File f =  new File("/data/Images/Fluo/OutputTest");f.mkdirs();deleteDirectory(f);
        String[] files = new String[]{"/data/Images/Fluo/test"}; //       /data/Images/Fluo/me121r-1-9-15-lbiptg100x
        Processor.importFiles(files, xp);
        //assertEquals("number of fields detected", 1, xp.getMicroscopyFields().getChildCount());
        logger.info("imported field: name: {} image: timepoint: {} scale xy: {} scale z: {}", xp.getMicroscopyField(0).getName(), xp.getMicroscopyField(0).getTimePointNumber(), xp.getMicroscopyField(0).getScaleXY(), xp.getMicroscopyField(0).getScaleZ());
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 0));
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 1));
    }
    
    private void subsetTimePoints(int tnb) {
        if (xp==null)testImport();
        Image[][] imageTC = new Image[tnb][1];
        for (int i = 0; i<tnb; ++i) imageTC[i][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, i);
        ImageWriter.writeToFile("/data/Images/Fluo/testsub/", "imagesTest_REF", ImageFormat.OMETIF, imageTC);
        for (int i = 0; i<tnb; ++i) imageTC[i][0] = xp.getMicroscopyField(0).getInputImages().getImage(1, i);
        ImageWriter.writeToFile("/data/Images/Fluo/testsub/", "imagesTest", ImageFormat.OMETIF, imageTC);
    }
    
    public void testRotation() {
        testImport();
        ImageDisplayer disp = new IJImageDisplayer();
        for (int i =0; i<xp.getMicrocopyFieldNB(); ++i) {
            xp.getMicroscopyField(i).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
            xp.getMicroscopyField(i).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));

            disp.showImage(xp.getMicroscopyField(i).getInputImages().getImage(0, 0).duplicate("input:"+xp.getMicroscopyField(i).getName()));
            Processor.setTransformationsAndComputeConfigurationData(xp.getMicroscopyField(i));
            disp.showImage(xp.getMicroscopyField(i).getInputImages().getImage(0, 0).duplicate("output:"+xp.getMicroscopyField(i).getName()));
        }
    }
    
    //@Test 
    public void testStabilizer() {
        testImport();
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        xp.getMicroscopyField(0).getPreProcessingChain().addTransformation(0, null, new ImageStabilizerXY().setReferenceTimePoint(0));
        
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        
        Processor.preProcessImages(xp);
        ImageDisplayer disp = new IJImageDisplayer();
        Image[][] imageOutputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        for (int t = 0; t<imageOutputTC.length; ++t) imageOutputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        //disp.showImage5D("input", imageInputTC);
        disp.showImage5D("output", imageOutputTC);
    }
    
    public void testCrop() {
        testImport();
        //List<Integer> flip = Arrays.asList(new Integer[]{0});
        for (int i =0; i<xp.getMicrocopyFieldNB(); ++i) testCrop(i, true); //flip.contains(new Integer(i))
    }
    
    public void testCrop(int fieldIdx, boolean flip) {
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new IJSubtractBackground(20, true, false, true, false));
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new AutoRotationXY(-10, 10, 0.5, 0.05, null, AutoRotationXY.SearchMethod.MAXVAR));
        if (flip) xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new Flip(ImageTransformation.Axis.Y));
        xp.getMicroscopyField(fieldIdx).getPreProcessingChain().addTransformation(0, null, new CropMicroChannels2D());
        //Image[][] imageInputTC = new Image[xp.getMicroscopyField(0).getInputImages().getTimePointNumber()][1];
        //for (int t = 0; t<imageInputTC.length; ++t) imageInputTC[t][0] = xp.getMicroscopyField(0).getInputImages().getImage(0, t);
        ImageDisplayer disp = new IJImageDisplayer();
        disp.showImage(xp.getMicroscopyField(fieldIdx).getInputImages().getImage(0, 0).duplicate("input:"+fieldIdx));
        Processor.setTransformationsAndComputeConfigurationData(xp.getMicroscopyField(fieldIdx));
        disp.showImage(xp.getMicroscopyField(fieldIdx).getInputImages().getImage(0, 0).duplicate("output:"+fieldIdx));
    }
}
