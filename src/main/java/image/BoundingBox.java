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
package image;

import dataStructure.objects.Voxel3D;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;

/**
 *
 * @author jollion
 */
@Embedded
public class BoundingBox {

    int xMin, xMax, yMin, yMax, zMin, zMax;
    @Transient int count;
    public BoundingBox(){
        xMin=Integer.MAX_VALUE;
        yMin=Integer.MAX_VALUE;
        zMin=Integer.MAX_VALUE;
        xMin=Integer.MIN_VALUE;
        yMin=Integer.MIN_VALUE;
        zMin=Integer.MIN_VALUE;
    }
    
    public BoundingBox(int xMin, int xMax, int yMin, int yMax, int zMin, int zMax) {
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.zMin = zMin;
        this.zMax = zMax;
    }
    /**
     * creates a new bounding box containing the voxel of coordinates {@param x}, {@param y}, {@param z}
     * @param x coordinate in the X-Axis
     * @param y coordinate in the Y-Axis
     * @param z coordinate in the Z-Axis
     */
    public BoundingBox(int x, int y, int z) {
        xMin=x;
        xMax=x;
        yMin=y;
        yMax=y;
        zMin=z;
        zMax=z;
        count=1;
    }
    /**
     * 
     * @param image
     * @param useOffset 
     */
    public BoundingBox(Image image, boolean useOffset) {
        if (useOffset) {
            xMin=image.getOffsetX();
            yMin=image.getOffsetY();
            zMin=image.getOffsetZ();
        }
        xMax=xMin+image.getSizeX()-1;
        yMax=yMin+image.getSizeY()-1;
        zMax=zMin+image.getSizeZ()-1;
    }
    
    /**
     * Modify the bounds so that is contains the {@param x} coordinate
     * @param x coordinate in the X-Axis
     */
    public void expandX(int x) {
        if (x < xMin) {
            xMin = x;
        } else if (x > xMax) {
            xMax = x;
        }
    }
    /**
     * Modify the bounds so that is contains the {@param y} coordinate
     * @param y coordinate in the X-Axis
     */
    public void expandY(int y) {
        if (y < yMin) {
            yMin = y;
        } else if (y > yMax) {
            yMax = y;
        }
    }
    /**
     * Modify the bounds so that is contains the {@param z} coordinate
     * @param z coordinate in the X-Axis
     */
    public void expandZ(int z) {
        if (z < zMin) {
            zMin = z;
        } else if (z > zMax) {
            zMax = z;
        }
    }
    
    public void expand(int x, int y, int z) {
        expandX(x);
        expandY(y);
        expandZ(z);
    }
    
    public void expand(Voxel3D v) {
        expandX(v.x);
        expandY(v.y);
        expandZ(v.z);
    }
    
    public void addToCounter() {
        count++;
    }
    public void setCounter(int count) {
        this.count = count;
    }
    /**
     * add {@param border} value in each direction and both ways
     * @param border value of the border
     */
    public void addBorder(int border) {
        xMin-=border;
        xMax+=border;
        yMin-=border;
        yMax+=border;
        zMin-=border;
        zMax+=border;
    }
    /**
     * adds a border of 1 pixel in each directions and both ways
     */
    public void addBorder() {
        xMin--;
        xMax++;
        yMin--;
        yMax++;
        zMin--;
        zMax++;
    }
    /**
     * ensures the bounds are included in the bounds of the {@param properties} object
     * @param properties 
     */
    public void trimToImage(ImageProperties properties) {
        if (xMin<0) xMin=0;
        if (yMin<0) yMin=0;
        if (zMin<0) zMin=0;
        if (xMax>=properties.getSizeX()) xMax=properties.getSizeX()-1;
        if (yMax>=properties.getSizeY()) yMax=properties.getSizeY()-1;
        if (zMax>=properties.getSizeZ()) zMax=properties.getSizeZ()-1;
    }
    
    public boolean sameBounds(ImageProperties properties) {
        return xMin==0 && yMin==0 && zMin==0 && xMax==(properties.getSizeX()-1) && yMax==(properties.getSizeY()-1) && zMax==(properties.getSizeZ()-1);
    }
    /**
     * Translate the bounding box in the 3 axes
     * @param dX translation in the X-Axis in pixels
     * @param dY translation in the Y-Axis in pixels
     * @param dZ translation in the X-Axis in pixels
     * @return the same instance of boundgin box, after the translation operation
     */
    public BoundingBox translate(int dX, int dY, int dZ) {
        xMin+=dX; xMax+=dX; yMin+=dY; yMax+=dY; zMin+=dZ; zMax+=dZ;
        return this;
    }
    
    public int getxMin() {
        return xMin;
    }

    public int getxMax() {
        return xMax;
    }

    public int getyMin() {
        return yMin;
    }

    public int getyMax() {
        return yMax;
    }

    public int getzMin() {
        return zMin;
    }

    public int getzMax() {
        return zMax;
    }

    public int getCount() {
        return count;
    }
    
    public int getSizeX() {
        return xMax-xMin+1;
    }
    
    public int getSizeY() {
        return yMax-yMin+1;
    }
    
    public int getSizeZ() {
        return zMax-zMin+1;
    }
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof BoundingBox) {
            BoundingBox otherBB = (BoundingBox) other;
            return xMin==otherBB.getxMin() && yMin==otherBB.getyMin() && zMin==otherBB.getzMin() && xMax==otherBB.getxMax() && yMax==otherBB.getyMax() && zMax==otherBB.getzMax();
        } else if (other instanceof ImageProperties) {
            return this.sameBounds((ImageProperties)other);
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + this.xMin;
        hash = 29 * hash + this.xMax;
        hash = 29 * hash + this.yMin;
        hash = 29 * hash + this.yMax;
        hash = 29 * hash + this.zMin;
        hash = 29 * hash + this.zMax;
        return hash;
    }
    
    public BlankMask getImageProperties(String name, float scaleXY, float scaleZ) {
        return new BlankMask(name, this, scaleXY, scaleZ);
    }
    
    public ImageProperties getImageProperties() {
        return new BlankMask("", this, 1, 1);
    }
    
    public BoundingBox duplicate() {
        return new BoundingBox(xMin, xMax, yMin, yMax, zMin, zMax);
    }
    
    public BoundingBox addOffset(BoundingBox other) {
        this.translate(other.xMin, other.yMin, other.zMin);
        return this;
    }
}
