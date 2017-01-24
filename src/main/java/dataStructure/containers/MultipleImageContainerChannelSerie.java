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
package dataStructure.containers;

import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;
import image.Image;
import static image.Image.logger;
import image.ImageIOCoordinates;
import image.ImageReader;

/**
 *
 * @author jollion
 */

public class MultipleImageContainerChannelSerie extends MultipleImageContainer { //one file per channel and serie
    String[] filePathC;
    String name;
    int timePointNumber;
    int[] sizeZC;
    BoundingBox bounds;
    @Transient private ImageReader reader[];
    
    public MultipleImageContainerChannelSerie(String name, String[] imagePathC, int timePointNumber, int[] sizeZC, double scaleXY, double scaleZ) {
        super(scaleXY, scaleZ);
        this.name = name;
        filePathC = imagePathC;
        this.timePointNumber = timePointNumber;
        this.reader=new ImageReader[imagePathC.length];
        this.sizeZC= sizeZC;
    }
    
    public MultipleImageContainerChannelSerie duplicate() {
        return new MultipleImageContainerChannelSerie(name, filePathC, timePointNumber, sizeZC, scaleXY, scaleZ);
    }
    
    @Override public double getCalibratedTimePoint(int t, int c, int z) {
        return getReader(c).getTimePoint(0, t, z);
    }
    
    public void setImagePath(String[] path) {
        this.filePathC=path;
        this.reader=new ImageReader[filePathC.length];
    }
    
    public String[] getFilePath(){return filePathC;}
    
    public String getName(){return name;}

    public int getTimePointNumber() {
        return timePointNumber;
    }

    public int getChannelNumber() {
        return filePathC!=null?filePathC.length:0;
    }
    
    @Override
    public int getSizeZ(int channelNumber) {
        if (sizeZC==null) sizeZC = new int[filePathC.length]; // temporary, for retrocompatibility
        if (sizeZC[channelNumber]==0) sizeZC[channelNumber] = getReader(channelNumber).getSTCXYZNumbers()[0][4]; // temporary, for retrocompatibility
        return sizeZC[channelNumber];
    }
    
    protected ImageIOCoordinates getImageIOCoordinates(int timePoint) {
        return new ImageIOCoordinates(0, 0, timePoint);
    }
    
    protected ImageReader getReader(int channelIdx) {
        if (getImageReaders()[channelIdx]==null) reader[channelIdx] = new ImageReader(filePathC[channelIdx]);
        return reader[channelIdx];
    }
    
    protected ImageReader[] getImageReaders() {
        if (reader==null) reader=new ImageReader[filePathC.length];
        return reader;
    }
    
    @Override
    public synchronized Image getImage(int timePoint, int channel) {
        //logger.debug("getImage calling thread: {}", Thread.currentThread());
        if (this.timePointNumber==1) timePoint=0;
        ImageIOCoordinates ioCoordinates = getImageIOCoordinates(timePoint);
        if (bounds!=null) ioCoordinates.setBounds(bounds);
        Image image = getReader(channel).openImage(ioCoordinates);
        /*if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }*/
        return image;
    }
    
    @Override
    public synchronized Image getImage(int timePoint, int channel, BoundingBox bounds) {
        
        if (this.timePointNumber==1) timePoint=0;
        ImageIOCoordinates ioCoordinates = getImageIOCoordinates(timePoint);
        ImageIOCoordinates ioCoords = ioCoordinates.duplicate();
        ioCoords.setBounds(bounds);
        Image image = getReader(channel).openImage(ioCoordinates);
        /*if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }*/
        return image;
    }
    
    @Override
    public void close() {
        for (int i = 0; i<this.getChannelNumber(); ++i) {
            if (getImageReaders()[i]!=null) reader[i].closeReader();
            reader [i] = null;
        }
    }
}
