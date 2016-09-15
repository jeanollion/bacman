/*
 * Copyright (C) 2016 jollion
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
package processing.neighborhood;

import dataStructure.objects.Voxel;
import image.Image;
import image.ImageInteger;
import image.ImageProperties;
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
public class EllipsoidalNeighborhoodCompartment extends EllipsoidalNeighborhood {
    final ImageInteger compartimentMap;
    public EllipsoidalNeighborhoodCompartment(double radius, boolean excludeCenter, ImageInteger compartimentMap) {
        super(radius, excludeCenter);
        this.compartimentMap=compartimentMap;
    }

    public EllipsoidalNeighborhoodCompartment(double radius, double radiusZ, boolean excludeCenter, ImageInteger compartimentMap) {
        super(radius, radiusZ, excludeCenter);
        this.compartimentMap = compartimentMap;
    }
    
    @Override
    public void addVoxels(Voxel v, ImageProperties p, ArrayList<Voxel> res) {
        int xx, yy;
        int label = compartimentMap.getPixelInt(v.x, v.y, v.z);
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=v.x+dx[i];
                yy=v.y+dy[i];
                zz=v.z+dz[i];
                if (p.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) res.add(new Voxel(xx, yy, zz));
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=v.x+dx[i];
                yy=v.y+dy[i];
                if (p.contains(xx, yy, v.z) && compartimentMap.getPixelInt(xx, yy, v.z)==label) res.add(new Voxel(xx, yy, v.z));
            }
        }
    }
    @Override public void setPixels(int x, int y, int z, Image image) {
        valueCount=0;
        int label = compartimentMap.getPixelInt(x, y, z);
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) values[valueCount++]=image.getPixel(xx, yy, zz);
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, z)==label) values[valueCount++]=image.getPixel(xx, yy, z);
            }
        }
    }
    @Override public void setPixelsByIndex(int x, int y, int z, Image image) {
        valueCount=0;
        int label = compartimentMap.getPixelInt(x, y, z);
        int xx, yy;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) {
                    values[i]=image.getPixel(xx, yy, zz);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, 0)==label) {
                    values[i]=image.getPixel(xx, yy, z);
                    valueCount++;
                } else values[i]=Float.NaN;
            }
        }
    }
    @Override public float getMin(int x, int y, int z, Image image, float... outOfBoundValue) {
        int xx, yy;
        int label = compartimentMap.getPixelInt(x, y, z);
        float min = Float.MAX_VALUE;
        boolean returnOutOfBoundValue = outOfBoundValue.length>=1;
        float ofbv = returnOutOfBoundValue? outOfBoundValue[0] : 0;
        float temp;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) {
                    temp=image.getPixel(xx, yy, zz);
                    if (temp<min) min=temp;
                } else if (returnOutOfBoundValue) return ofbv;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, z)==label) {
                    temp=image.getPixel(xx, yy, z);
                    if (temp<min) min=temp;
                } else if (returnOutOfBoundValue) return ofbv;
            }
        }
        if (min==Float.MAX_VALUE) min = Float.NaN;
        return min;
    }

    @Override public float getMax(int x, int y, int z, Image image) {
        int xx, yy;
        int label = compartimentMap.getPixelInt(x, y, z);
        float max = -Float.MAX_VALUE;
        float temp;
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) {
                    temp=image.getPixel(xx, yy, zz);
                    if (temp>max) max=temp;
                }
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, z)==label) {
                    temp=image.getPixel(xx, yy, z);
                    if (temp>max) max=temp;
                }
            }
        }
        if (max==Float.MIN_VALUE) max = Float.NaN;
        return max;
    }
    @Override public boolean hasNonNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNonNull) {
        int xx, yy;
        int label = compartimentMap.getPixelInt(x, y, z);
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) {
                    if (image.getPixel(xx, yy, zz)!=0) return true;
                } else if (outOfBoundIsNonNull) return true;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, z)==label) {
                    if (image.getPixel(xx, yy, z)!=0) return true;
                } else if (outOfBoundIsNonNull) return true;
            }
        }
        return false;
    }
    @Override public boolean hasNullValue(int x, int y, int z, Image image, boolean outOfBoundIsNull) {
        int xx, yy;
        int label = compartimentMap.getPixelInt(x, y, z);
        if (is3D) { 
            int zz;
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                zz=z+dz[i];
                if (image.contains(xx, yy, zz) && compartimentMap.getPixelInt(xx, yy, zz)==label) {
                    if (image.getPixel(xx, yy, zz)==0) return true;
                } else if (outOfBoundIsNull) return true;
            }
        } else {
            for (int i = 0; i<dx.length; ++i) {
                xx=x+dx[i];
                yy=y+dy[i];
                if (image.contains(xx, yy, z) && compartimentMap.getPixelInt(xx, yy, z)==label) {
                    if (image.getPixel(xx, yy, z)==0) return true;
                } else if (outOfBoundIsNull) return true;
            }
        }
        return false;
    }
}