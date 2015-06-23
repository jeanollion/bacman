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
package configuration.parameters;

import configuration.parameters.ui.ParameterUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
@Embedded
public abstract class SimpleContainerParameter implements ContainerParameter {
    protected String name;
    @Transient protected ContainerParameter parent;
    @Transient protected ArrayList<Parameter> children;
    
    protected SimpleContainerParameter(){}
    
    public SimpleContainerParameter(String name, Parameter... parameters) {
        this.name=name;
        initChildren(parameters);
    }
    
    protected void initChildren(Parameter... parameters) {
        children = new ArrayList<Parameter>(parameters.length);
        children.addAll(Arrays.asList(parameters));
        for (Parameter p : parameters) p.setParent(this);
    }
    
    @Override
    public String getName(){
        return name;
    }
    
    @Override
    public void setName(String name) {
        this.name=name;
    }
    
    @Override
    public ArrayList<Parameter> getPath() {
        return SimpleParameter.getPath(this);
    }
    
    @Override
    public boolean sameContent(Parameter other) { // ne check pas le nom ..
        if (other instanceof ContainerParameter) {
            ContainerParameter otherLP = (ContainerParameter)other;
            if (otherLP.getChildCount()==this.getChildCount()) {
                for (int i = 0; i<getChildCount(); i++) {
                    if (!((Parameter)this.getChildAt(i)).sameContent((Parameter)otherLP.getChildAt(i))) return false;
                }
                return true;
            } else return false;
        } else return false;
    }

    @Override
    public void insert(MutableTreeNode child, int index) {}
    
    @Override
    public void remove(int index) {}

    @Override
    public void remove(MutableTreeNode node) {}

    @Override public void setUserObject(Object object) {this.name=object.toString();}

    @Override
    public void removeFromParent() {
        parent.remove(this);
    }

    @Override
    public void setParent(MutableTreeNode newParent) {
        this.parent=(ContainerParameter)newParent;
    }

    @Override
    public TreeNode getParent() {
        return parent;
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
    
}
