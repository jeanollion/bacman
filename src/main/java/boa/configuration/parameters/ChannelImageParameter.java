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
package boa.configuration.parameters;

/**
 *
 * @author Jean Ollion
 */
public class ChannelImageParameter extends ObjectClassOrChannelParameter<ChannelImageParameter> {
    
    public ChannelImageParameter() {
        this("");
    }
    
    public ChannelImageParameter(String name) {
        this(name, -1);
    }
    
    public ChannelImageParameter(String name, int selectedChannel) {
        super(name, selectedChannel, false, false);
    }
    
    public ChannelImageParameter(String name, int selectedChannel, boolean allowNoSelection) {
        super(name, selectedChannel, allowNoSelection, false);
    }
    
    public ChannelImageParameter(String name, int[] selectedChannels) {
        super(name, selectedChannels, false);
    }
    
    @Override
    public String[] getChoiceList() {
        if (getXP()!=null) {
            return getXP().getChannelImagesAsString();
        } else {
            return new String[]{"error: no xp found in tree"};
        }
    }

    @Override
    public String getNoSelectionString() {
        return "No selection";
    }
    
}
