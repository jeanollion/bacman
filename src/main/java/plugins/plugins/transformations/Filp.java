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
package plugins.plugins.transformations;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;
import processing.ImageTransformation.Axis;

/**
 *
 * @author jollion
 */
public class Filp implements TransformationTimeIndependent {
    
    ChoiceParameter direction = new ChoiceParameter("Flip Axis Direction", new String[]{Axis.X.toString(), Axis.Y.toString(), Axis.Z.toString()}, Axis.Y.toString(), false);
    Parameter[] p = new Parameter[]{direction};
    public Filp() {}
    
    public Filp(Axis axis) {
        direction.setSelectedItem(axis.toString());
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        Axis axis;
        switch (direction.getSelectedIndex()) {
            case 0:
                axis=Axis.X;
                break;
            case 1: 
            default: 
                axis=Axis.Y;
                break;
            case 2: 
                axis = Axis.Z;
                break;
        }
        ImageTransformation.filp(image, axis);
        return image;
    }

    public boolean isTimeDependent() {
        return false;
    }

    public Parameter[] getParameters() {
        return p;
    }
    
    public Parameter[] getConfigurationData() {
        return null;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        
    }
    
}
