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
package configuration.parameters;

import configuration.parameters.ui.ChoiceParameterUI;
import static configuration.parameters.ui.ChoiceParameterUI.NO_SELECTION;
import configuration.parameters.ui.ParameterUI;
import core.Core;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.logging.Level;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.PostLoad;
import java.util.logging.Logger;
import plugins.Plugin;
import plugins.PluginFactory;

/**
 *
 * @author jollion
 * @param <T> class of plugin
 */
public class PluginParameter<T extends Plugin> extends SimpleContainerParameter implements Deactivatable, ChoosableParameter { //<T extends Plugin> // TODO generic quand supporté par morphia
    @Transient private static HashMap<Class<? extends Plugin>, ArrayList<String>> pluginNames=new HashMap<Class<? extends Plugin>, ArrayList<String>>();
    protected Parameter[] pluginParameters;
    protected String pluginName=NO_SELECTION;
    @Transient protected Class<T> pluginClass;
    protected String pluginClassName;
    protected boolean allowNoSelection;
    protected boolean activated=true;
    @Transient protected boolean pluginSet=false;
    
    public PluginParameter(String name, Class<T> pluginClass, boolean allowNoSelection) {
        super(name);
        this.pluginClass=pluginClass;
        this.pluginClassName=pluginClass.getName();
        this.allowNoSelection=allowNoSelection;
        super.initChildren();
    }
    
    public PluginParameter(String name, Class<T> pluginClass, boolean allowNoSelection, String defautlMethod) {
        this(name, pluginClass, allowNoSelection);
        this.pluginName=defautlMethod; // do not call setPlugin Method because plugins are no initiated at startup
    }
    
    @Override
    protected void initChildList() {
        if (pluginParameters!=null) super.initChildren(pluginParameters);
    }
    
    
    public String getPluginName() {
        return pluginName;
    }
    
    public boolean isOnePluginSet() {
        if (!pluginSet && !NO_SELECTION.equals(pluginName)) setPlugin(pluginName); // case of default plugin 
        return pluginSet;
        //return (pluginName!=null && !NOPLUGIN.equals(pluginName));
    }
    
    public void setPlugin(String pluginName) {
        if (NO_SELECTION.equals(pluginName)) {
            this.pluginParameters=null;
            this.pluginName=NO_SELECTION;
            this.pluginSet=false;
            
        } else if (!pluginSet || !pluginName.equals(this.pluginName)) {
            Plugin instance = PluginFactory.getPlugin(pluginClass, pluginName);
            if (instance==null) {
                Core.getLogger().log(Level.WARNING, "Couldn't find plugin: {0}", pluginName);
                this.pluginName=NO_SELECTION;
                this.pluginParameters=null;
                return;
            }
            pluginParameters=instance.getParameters();
            super.initChildren(pluginParameters);
            this.pluginName=pluginName;
            this.pluginSet=true;
        }
    }
    
    public Plugin getPlugin() {
        if (!isOnePluginSet()) return null;
        Plugin instance = PluginFactory.getPlugin(pluginClass, pluginName);
        if (instance==null) return null;
        Parameter[] params = instance.getParameters();
        if (params.length==this.pluginParameters.length) {
            for (int i = 0; i<params.length; i++) params[i].setContentFrom(pluginParameters[i]);
        } else {
            Core.getLogger().log(Level.WARNING, "Couldn't parametrize plugin: {0}", pluginName);
        }
        return instance;
    }
    
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof PluginParameter && ((PluginParameter)other).pluginClass.equals(pluginClass)) {
            PluginParameter otherPP = (PluginParameter) other;
            if (otherPP.pluginName != null && otherPP.pluginName.equals(this.pluginName) && pluginParameters!=null) {
                ParameterUtils.setContent(pluginParameters, otherPP.pluginParameters);
            } else {
                this.pluginName = otherPP.pluginName;
                if (otherPP.pluginParameters != null) {
                    this.pluginParameters = new Parameter[otherPP.pluginParameters.length];
                    for (int i = 0; i < pluginParameters.length; i++) {
                        pluginParameters[i] = otherPP.pluginParameters[i].duplicate();
                    }
                    initChildList();
                } else {
                    this.pluginParameters = null;
                }
            }
        } else throw new IllegalArgumentException("wrong parameter type");
    }

    @Override
    public PluginParameter duplicate() {
        PluginParameter res = new PluginParameter(name, pluginClass, allowNoSelection);
        res.setContentFrom(this);
        return res;
    }
    
    private static synchronized ArrayList<String> getPluginNames(Class<? extends Plugin> clazz) {
        ArrayList<String> res = pluginNames.get(clazz);
        if (res==null) {
            res=PluginFactory.getPluginNames(clazz);
            pluginNames.put(clazz, res);
            System.out.println("put :"+res.size()+ " plugins of type:"+clazz);
        }
        return res;
    }
    
    @Override
    public ChoiceParameterUI getUI(){
        return new ChoiceParameterUI(this);
    }
    
    @Override
    public String toString() {
        String res = name+ ": "+this.getPluginName();
        if (isActivated()) return res;
        else return "<HTML><S>"+res+"<HTML></S>";
    }
    
    // deactivatable interface
    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated=activated;
    }
    
    // choosable parameter interface
    public void setSelectedItem(String item) {
        this.setPlugin(item);
    }
    
    
    public ArrayList<String> getPluginNames() {
        return getPluginNames(pluginClass);
    }

    @Override
    public String[] getChoiceList() {
        ArrayList<String> res = getPluginNames(pluginClass);
        return res.toArray(new String[res.size()]);
    }

    public int getSelectedIndex() {
        String[] choices = getChoiceList();
        for (int i = 0; i<choices.length; ++i) {
            if (choices[i].equals(pluginName)) return i;
        }
        return -1;
    }

    public boolean isAllowNoSelection() {
        return allowNoSelection;
    }
    
    
    // morphia
    public PluginParameter(){
        super();
    }
    
    @Override
    @PostLoad public void postLoad() {
        super.postLoad();
        try {
            pluginClass = (Class<T>) Class.forName(pluginClassName);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(PluginParameter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
