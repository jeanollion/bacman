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

import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageMask;
import boa.image.ImageProperties;
import java.util.Collection;
import boa.plugins.PreFilter;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class PreFilterSequence extends PluginParameterList<PreFilter, PreFilterSequence> {

    public PreFilterSequence(String name) {
        super(name, "Pre-Filter", PreFilter.class);
    }
    
    public Image filter(Image input, ImageMask mask) {
        ImageProperties prop = input.getProperties();
        for (PreFilter p : get()) {
            input = p.runPreFilter(input, mask);
            //logger.debug("prefilter: {}", p.getClass().getSimpleName());
        }
        input.setCalibration(prop);
        if (input.sameDimensions(prop)) input.resetOffset().translate(prop);
        return input;
    }
    @Override public PreFilterSequence removeAll() {
        this.removeAllElements();
        return this;
    }
    @Override public PreFilterSequence add(PreFilter... instances) {
        super.add(instances);
        return this;
    }
    
    @Override public PreFilterSequence add(Collection<PreFilter> instances) {
        super.add(instances);
        return this;
    }
    public String toStringElements() {
        return Utils.toStringList(children, p -> p.pluginName);
    }
    @Override 
    public PreFilterSequence setToolTipText(String txt){
        super.setToolTipText(txt);
        return this;
    }
    @Override
    public PreFilterSequence duplicate() {
        PreFilterSequence res = new PreFilterSequence(name);
        res.setContentFrom(this);
        return res;
    }
}
