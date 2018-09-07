/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import boa.plugins.Plugin;
import boa.plugins.PreFilter;
import boa.utils.Utils;
import java.util.function.Consumer;

/**
 *
 * @author jollion
 * @param <T>
 * @param <L>
 */
public abstract class PluginParameterList<T extends Plugin, L extends PluginParameterList<T, L>> extends ListParameterImpl<PluginParameter<T>, L> {
    String childLabel;
    public PluginParameterList(String name, String childLabel, Class<T> childClass) {
        super(name, -1, new PluginParameter<T>(childLabel, childClass, false));
    }
    
    private void add(T instance) {
        super.insert(super.createChildInstance(childLabel).setPlugin(instance));
    } 
    public L removeAll() {
        this.removeAllElements();
        return (L)this;
    }
    public L add(T... instances) {
        for (T t : instances) add(t);
        return (L)this;
    }
    public L addAtFirst(T... instances) {
        for (T t : instances) {
            PluginParameter<T> pp = super.createChildInstance(childLabel).setPlugin(t);
            pp.setParent(this);
            super.getChildren().add(0, pp);
        }
        return (L)this;
    }
    
    public L add(Collection<T> instances) {
        for (T t : instances) add(t);
        return (L)this;
    }
    
    public List<T> get() {
        List<T> res = new ArrayList<>(this.getChildCount());
        for (PluginParameter<T> pp : this.getActivatedChildren()) {
            T p = pp.instanciatePlugin();
            if (p!=null) res.add(p);
        }
        return res;
    }
    public boolean isEmpty() {
        for (PluginParameter<T> pp : this.children) if (pp.isActivated()) return false;
        return true;
    }
}
