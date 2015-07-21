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

import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.ui.NameEditorUI;
import configuration.parameters.ui.ParameterUI;

/**
 *
 * @author jollion
 */
public class ChannelImage extends SimpleContainerParameter {
    NameEditorUI ui;
    
    public ChannelImage(String name) {
        super(name);
    }
    
    @Override
    protected void initChildList() {
        
    }

    public Parameter duplicate() {
        ChannelImage dup = new ChannelImage(name);
        dup.setContentFrom(this);
        return dup;
    }
    
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
    
    // morphia
    public ChannelImage(){super(); initChildList();} // mettre dans la clase abstraite SimpleContainerParameter?
}