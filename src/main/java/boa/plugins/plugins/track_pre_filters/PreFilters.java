/*
 * Copyright (C) 2018 jollion
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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.plugins.MultiThreaded;
import boa.plugins.PreFilter;
import boa.plugins.TrackPreFilter;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

/**
 *
 * @author jollion
 */
public class PreFilters implements TrackPreFilter, MultiThreaded {
    PreFilterSequence preFilters = new PreFilterSequence("Pre-Filters");
    
    ExecutorService executor;
    @Override public void setExecutor(ExecutorService executor) {
        this.executor=executor;
    }
    
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) {
        if (preFilters.isEmpty()) return;
        Collection<Map.Entry<StructureObject, Image>> col = preFilteredImages.entrySet();
        ThreadRunner.ThreadAction<Map.Entry<StructureObject, Image>> ta = (Map.Entry<StructureObject, Image> e, int idx) -> {
            e.setValue(preFilters.filter(e.getValue(), e.getKey().getMask()));
        };
        ThreadRunner.execute(col, false, ta, executor, null);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{preFilters};
    }
    public PreFilters removeAll() {
        preFilters.removeAllElements();
        return this;
    }
    public PreFilters add(PreFilterSequence sequence) {     
        preFilters.add(sequence.get());
        return this;
    }
    public PreFilters add(PreFilter... instances) {
        preFilters.add(instances);
        return this;
    }
    
    public PreFilters add(Collection<PreFilter> instances) {
        preFilters.add(instances);
        return this;
    }
}