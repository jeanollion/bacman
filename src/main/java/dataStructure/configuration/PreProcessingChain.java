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
package dataStructure.configuration;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.MultipleChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.ParameterUtils;
import configuration.parameters.PluginParameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.TimePointParameter;
import configuration.parameters.TransformationPluginParameter;
import configuration.parameters.ui.MultipleChoiceParameterUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.SelectionMode;
import javax.swing.AbstractAction;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.plaf.basic.BasicMenuItemUI;
import plugins.Transformation;
import plugins.TransformationTimeIndependent;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class PreProcessingChain extends SimpleContainerParameter {
    BooleanParameter useImageScale;
    BoundedNumberParameter scaleXY;
    BoundedNumberParameter scaleZ;
    BoundedNumberParameter frameDuration;
    @Transient ConditionalParameter imageScaleCond;
    TimePointParameter trimFramesStart, trimFramesEnd;
    SimpleListParameter<TransformationPluginParameter<Transformation>> transformations;
    
    public PreProcessingChain(String name) {
        super(name);
        transformations = new SimpleListParameter<TransformationPluginParameter<Transformation>>("Transformations", new TransformationPluginParameter<Transformation>("Transformation", Transformation.class, false));
        //logger.debug("new PPC: {}", name);
        initScaleParam(true, true);
        initChildList();
    }

    private void initScaleParam(boolean initParams, boolean initCond) {
        if (initParams) {
            useImageScale = new BooleanParameter("Voxel Calibration", "Use Image Calibration", "Custom Calibration", true);
            scaleXY = new BoundedNumberParameter("Scale XY", 5, 1, 0.00001, null);
            scaleZ = new BoundedNumberParameter("Scale Z", 5, 1, 0.00001, null);
            scaleXY.setParent(this);
            scaleZ.setParent(this);
            useImageScale.setParent(this);
            frameDuration= new BoundedNumberParameter("Frame Duration", 4, 4, 0, null);
            frameDuration.setParent(this);
        }
        if (initCond) {
            imageScaleCond = new ConditionalParameter(useImageScale).setActionParameters("Custom Calibration", new Parameter[]{scaleXY, scaleZ});
            imageScaleCond.setParent(this);
        }
    }
    public PreProcessingChain setCustomScale(double scaleXY, double scaleZ) {
        if (Double.isNaN(scaleXY) || Double.isInfinite(scaleXY)) throw new IllegalArgumentException("Invalid scale value");
        if (scaleXY<=0) throw new IllegalArgumentException("Scale should be >=0");
        if (scaleZ<=0) scaleZ=1;
        useImageScale.setSelected(false); // custom calibration
        this.scaleXY.setValue(scaleXY);
        this.scaleZ.setValue(scaleZ);
        return this;
    }
    public boolean useCustomScale() {return !useImageScale.getSelected();}
    public double getScaleXY() {return scaleXY.getValue().doubleValue();}
    public double getScaleZ() {return scaleZ.getValue().doubleValue();}
    public double getFrameDuration() {return frameDuration.getValue().doubleValue();}
    @Override
    protected void initChildList() {
        //logger.debug("PreProc chain: {}, init list..", name);
        initScaleParam(useImageScale==null, imageScaleCond==null); //TODO for retrocompatibility
        if (trimFramesStart==null) trimFramesStart = new TimePointParameter("Trim Frames Start Position", 0, true);
        else trimFramesStart.setUseRawInputFrames(true); //avoid edless loop //TODO for retrocompatibility
        if (trimFramesEnd==null) trimFramesEnd = new TimePointParameter("Trim Frames Stop Position (0=no trimming)", 0, true);
        else trimFramesEnd.setUseRawInputFrames(true); //avoid edless loop //TODO for retrocompatibility
        if (frameDuration==null) frameDuration=new BoundedNumberParameter("Frame Duration", 4, 4, 0, null); //TODO for retrocompatibility
        super.initChildren(imageScaleCond, transformations, trimFramesStart, trimFramesEnd, frameDuration);
    }
    
    public List<TransformationPluginParameter<Transformation>> getTransformations(boolean onlyActivated) {
        return onlyActivated ? transformations.getActivatedChildren() : transformations.getChildren();
    }
    
    public void removeAllTransformations() {
        transformations.removeAllElements();
    }
    
    public void setTrimFrames(int startFrame, int endFrame) {
        this.trimFramesStart.setTimePoint(startFrame);
        this.trimFramesEnd.setTimePoint(endFrame);
    }
    
    /**
     * 
     * @param inputChannel channel on which compute transformation parameters
     * @param outputChannel channel(s) on which apply transformation (null = all channels or same channel, depending {@link TransformationTimeIndependent#getOutputChannelSelectionMode() })
     * @param transformation 
     */
    public TransformationPluginParameter<Transformation> addTransformation(int inputChannel, int[] outputChannel, Transformation transformation) {
        if (inputChannel<-1) throw new IllegalArgumentException("Input channel should be >=0");
        Experiment xp = ParameterUtils.getExperiment(this);
        if (xp!=null &&  inputChannel>=xp.getChannelImageCount()) throw new IllegalArgumentException("Input channel should be < channel image count ("+xp.getChannelImageCount()+")");
        TransformationPluginParameter<Transformation> tpp= new TransformationPluginParameter<Transformation>("Transformation", Transformation.class, false);
        transformations.insert(tpp);
        tpp.setPlugin(transformation);
        tpp.setInputChannel(inputChannel);
        if (outputChannel==null && (transformation.getOutputChannelSelectionMode()==Transformation.SelectionMode.MULTIPLE || transformation.getOutputChannelSelectionMode()==Transformation.SelectionMode.SINGLE) ) outputChannel = new int[]{inputChannel};
        tpp.setOutputChannel(outputChannel);
        return tpp;
    }
    
    @Override 
    public void postLoad() {
        if (!postLoaded) {
            initScaleParam(useImageScale==null, true);
            useImageScale.postLoad();
            super.postLoad();
        }
    }
    
    @Override public ParameterUI getUI() {
        return new PreProcessingChainUI(this);
    }
    
    public class PreProcessingChainUI implements ParameterUI {
        Object[] actions;
        MultipleChoiceParameter fields;
        MultipleChoiceParameterUI fieldUI;
        Experiment xp;
        PreProcessingChain ppc;
        public PreProcessingChainUI(PreProcessingChain ppc) {
            xp = ParameterUtils.getExperiment(ppc);
            this.ppc=ppc;
            fields = new MultipleChoiceParameter("Fields", xp.getPositionsAsString(), false);
        }
        public void addMenuListener(JPopupMenu menu, int X, int Y, Component parent) {
            ((MultipleChoiceParameterUI)fields.getUI()).addMenuListener(menu, X, Y, parent);
        }
        public Object[] getDisplayComponent() {
            fieldUI = (MultipleChoiceParameterUI)fields.getUI();
            actions = new Object[fieldUI.getDisplayComponent().length + 2];
            for (int i = 2; i < actions.length; i++) {
                actions[i] = fieldUI.getDisplayComponent()[i - 2];
                //if (i<actions.length-1) ((JMenuItem)actions[i]).setUI(new StayOpenMenuItemUI());
            }
            JMenuItem overide = new JMenuItem("Overide configuration on selected fields");
            overide.setAction(
                new AbstractAction("Overide configuration on selected fields") {
                    @Override
                    public void actionPerformed(ActionEvent ae) {
                        for (int f : fields.getSelectedItems()) {
                            //logger.debug("override pp on field: {}", f);
                            MicroscopyField field = xp.fields.getChildAt(f);
                            if (field.getPreProcessingChain()!=ppc) field.setPreProcessingChains(ppc);
                        }
                    }
                }
            );
            actions[0]=overide;
            actions[1]=new JSeparator();
            return actions;
        }
    }
    class StayOpenMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
        }
    }
}
