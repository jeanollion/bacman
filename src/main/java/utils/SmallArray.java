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
package utils;

import java.util.ArrayList;

/**
 *
 * @author jollion
 */
public class SmallArray<T> {
    Object[] array;
    public SmallArray(){}
    public SmallArray(int bucketSize){array=new Object[bucketSize];}
    
    public T get(int idx) {
        if (array==null) return null;
        else if (array.length<=idx) return null;
        else return (T)array[idx];
    }
    public T getAndExtend(int idx) {
        if (array==null) {
            array=new Object[idx+1];
            return null;
        }
        else if (array.length<=idx) {
            Object[] newArray=new Object[idx+1];
            System.arraycopy(array, 0, newArray, 0, array.length);
            array=newArray;
            return null;
        }
        else return (T)array[idx];
    }
    public T getQuick(int idx) {return (T)array[idx];}
    public void set(T element, int idx) {
        if (array==null) array=new Object[idx+1];
        else if (array.length<=idx) {
            Object[] newArray=new Object[idx+1];
            System.arraycopy(array, 0, newArray, 0, array.length);
            array=newArray;
        }
        array[idx]=element;
    }
    public void setQuick(T element, int idx) {array[idx]=element;}
    public int getBucketSize() {
        if (array==null) return 0;
        else return array.length;
    }
    public int getSize() {
        if (array==null) return 0;
        else {
            int count = 0;
            for (int i = 0; i<array.length; ++i) if (array[i]!=null) ++count;
            return count;
        }
    }
    
    public ArrayList<T> getObjects() {
        if (array==null) return new ArrayList<T>(0);
        ArrayList<T> res = new ArrayList<T>(getSize());
        for (int i = 0; i<array.length; ++i) if (array[i]!=null) res.add((T)array[i]);
        return res;
    }
    public ArrayList<T> getObjectsQuick() {
        if (array==null) return new ArrayList<T>(0);
        ArrayList<T> res = new ArrayList<T>();
        for (int i = 0; i<array.length; ++i) if (array[i]!=null) res.add((T)array[i]);
        return res;
    }
    public T getAmongNonNull(int idx) {
        for (int i = 0; i<array.length; ++i) {
            if (array[i]!=null) {
                if (idx==0) return (T)array[i];
                else --idx;
            }
        }
        return null;
    }
    public int indexOf(T object) {
        if (object==null) return -1;
        for (int i = 0; i<array.length; ++i) if (object.equals(array[i])) return i;
        return -1;
    }
}