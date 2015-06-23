/*
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
package configuration.dataStructure;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.ContainerParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.parameters.SimpleParameter;
import configuration.parameters.ui.ParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 * @param <Structure>
 */

@Entity(value = "Experiment", noClassnameStored = true)
public class Experiment implements ContainerParameter, TreeModelContainer {
    @Id protected String name;
    public ChoiceParameter choice;
    StructureList structures;
    @Transient ConfigurationTreeModel model;
    @Transient protected ArrayList<Parameter> children;
    public String test;
    public Experiment(String name) {
        this.name=name;
        choice = new ChoiceParameter("Choice name", new String[]{"choice 1", "choice2"}, "choice 1");
        structures = new StructureList(1);
        structures.insert(structures.createChildInstance("Channels"));
        structures.insert(structures.createChildInstance("Bacteries"));
        initChildren();
    }
    
    public StructureList getStructures() {return structures;}
    
    public String[] getStructuresAsString() {return structures.getStructuresAsString();}
    
    @Override
    public ConfigurationTreeModel getModel() {
        return model;
    }

    @Override
    public void setModel(ConfigurationTreeModel model) {
        this.model=model;
    }
    
    protected void initChildren() {
        children = new ArrayList<Parameter>(2);
        children.add(choice);
        children.add(structures);
        for (Parameter p : children) p.setParent(this);
    }
    
    @Override
    public ArrayList<Parameter> getPath() {
        return SimpleParameter.getPath(this);
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}
    
    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override public void setUserObject(Object object) {this.name=object.toString();}

    @Override
    public void removeFromParent() { }

    @Override
    public void setParent(MutableTreeNode newParent) {}

    @Override
    public TreeNode getParent() {
        return null;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
    
    @Override
    public String toString() {return name;}
    
    @Override
    public ParameterUI getUI() {
        return null;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public int getIndex(TreeNode node) {
        return children.indexOf((Parameter)node);
    }

    @Override
    public Enumeration children() {
        return Collections.enumeration(children);
    }
    
    
    // morphia
    private Experiment(){}
    
    @PostLoad void postLoad() {initChildren();}

    
    
}
