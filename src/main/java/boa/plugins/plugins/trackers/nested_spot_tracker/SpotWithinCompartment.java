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
package boa.plugins.plugins.trackers.nested_spot_tracker;

import boa.gui.imageInteraction.ImageObjectInterface;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import fiji.plugin.trackmate.Spot;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.TextRoi;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.SimpleBoundingBox;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static boa.plugins.Plugin.logger;
import static boa.plugins.plugins.trackers.nested_spot_tracker.NestedSpotRoiModifier.displayPoles;
import boa.utils.Utils;
import boa.utils.geom.Point;

/**
 *
 * @author jollion
 */
public class SpotWithinCompartment extends SpotWithQuality {
    public static double poleDistanceFactor = 0; 
    protected Region object;
    public final SpotCompartiment compartiment;
    public final Localization localization;
    public final int frame;
    boolean isLinkable=true;
    public boolean lowQuality=false;
    protected final DistanceComputationParameters distanceParameters;
    
    public SpotWithinCompartment(Region object, int timePoint, SpotCompartiment compartiment, Point scaledCenter, DistanceComputationParameters distanceParameters) {
        super(scaledCenter, 1, 1);
        //logger.debug("create spot: F={}, Idx={}, center={}", timePoint, object.getLabel()-1, Utils.toStringArray(scaledCenter));
        getFeatures().put(Spot.FRAME, (double)compartiment.object.getFrame());
        getFeatures().put(Spot.QUALITY, object.getQuality());
        this.compartiment=compartiment;
        this.object=object;
        this.frame=timePoint;
        if (scaledCenter.get(1)<(compartiment.middleYLimits[0])) localization = Localization.UP;
        else if (scaledCenter.get(1)>=compartiment.middleYLimits[0] && scaledCenter.get(1)<=compartiment.middleYLimits[1]) localization = Localization.UPPER_MIDDLE;
        else if (scaledCenter.get(1)>compartiment.middleYLimits[1] && scaledCenter.get(1)<compartiment.middleYLimits[2]) localization = Localization.MIDDLE;
        else if (scaledCenter.get(1)>compartiment.middleYLimits[3]) localization = Localization.LOW;
        else localization = Localization.LOWER_MIDDLE;
        this.distanceParameters=distanceParameters;
        this.isLinkable=object.getQuality()>=distanceParameters.qualityThreshold;
        this.lowQuality=!isLinkable;
        //if (displayPoles) displayOnOverlay();
    }
    
    public TextRoi getLocalizationRoi(BoundingBox offset) {
        
        //logger.debug("get loc Roi for {}: offset: {}, compartiment offset: {}", this, offset.toStringOffset(), compartiment.object.getParent().getBounds().toStringOffset());
        //offset.duplicate().translate(this.compartiment.object.getBounds().duplicate().reverseOffset());
        
        return new TextRoi(offset.xMax(), offset.yMax(), localization.toString());
    }
    
    public int compareSpots(Spot other) {
        if (other instanceof SpotWithinCompartment) {
            SpotWithinCompartment otherS = (SpotWithinCompartment)other;
            int c1 = Integer.compare(this.frame, otherS.frame);
            if (c1!=0) return c1;
            int c2 = Integer.compare(this.object.getLabel(), otherS.object.getLabel());
            if (c2!=0) return c2;
            return super.compareTo(other);
        } else return super.compareTo(other);
    }
    
    public SpotWithinCompartment duplicate() {
        Point scaledCenter =  new Point(getFeature(Spot.POSITION_X).floatValue(), getFeature(Spot.POSITION_Y).floatValue(),  getFeature(Spot.POSITION_Z).floatValue());
        SpotWithinCompartment res = new SpotWithinCompartment(object, frame, compartiment, scaledCenter, distanceParameters);
        res.getFeatures().put(Spot.QUALITY, getFeature(Spot.QUALITY));
        res.getFeatures().put(Spot.RADIUS, getFeature(Spot.RADIUS));
        return res;
    }
    @Override 
    public boolean isLowQuality() {
        return this.lowQuality;
    }
    @Override 
    public int frame() {
        return this.frame;
    }
    @Override 
    public StructureObject parent() {
        return this.compartiment.object;
    }
   
    public void setRadius() {
        double radius = !object.is2D() ? Math.pow(3 * object.size() / (4 * Math.PI) , 1d/3d) : Math.sqrt(object.size() / (2 * Math.PI)) ;
        getFeatures().put(Spot.RADIUS, radius);
    }
    
    private int[] getCenterInVoxels() {
        int[] center =  new int[]{(int)Math.round(getFeature(Spot.POSITION_X)/object.getScaleXY()), (int)Math.round(getFeature(Spot.POSITION_Y)/object.getScaleXY()), 0};
        if (object.getScaleZ()!=0) center[2] = (int)Math.round(getFeature(Spot.POSITION_Z)/object.getScaleZ());
        return center;
    }
    
    public Region getObject() {return object;}
    
    @Override
    public double normalizeDiffTo( final Spot s, final String feature ) {
        if (s instanceof SpotWithinCompartment) {
            final double a = getFeature( feature );
            final double b = s.getFeature( feature );
            if ( a == -b ) return 0d;
            else return Math.abs( a - b ) / ( ( a + b ) / 2 );
        } else return super.normalizeDiffTo(s, feature);
    }
    
    @Override
    public double squareDistanceTo( final Spot s ) {
        if (s instanceof SpotWithinCompartment) {
            SpotWithinCompartment ss = (SpotWithinCompartment)s;
            if (!distanceParameters.includeLQ && (lowQuality || ss.lowQuality)) return Double.POSITIVE_INFINITY;
            //if (!isLinkable && !ss.isLinkable) return Double.POSITIVE_INFINITY; // no link allowed between to spots that are not linkable
            if (this.compartiment.object.getFrame()>ss.compartiment.object.getFrame()) return ss.squareDistanceTo(this);
            else {
                if (compartiment.sameTrackOrDirectChildren(ss.compartiment)) { // spot in the same track or separated by one division at max
                    //if (ss.compartiment.previousDivisionTime>=compartiment.object.getTimePoint()) return getSquareDistanceDivision(ss);
                    if (this.compartiment.nextDivisionTimePoint>=0 && this.compartiment.nextDivisionTimePoint<=ss.compartiment.object.getFrame()) return getSquareDistanceDivision(ss);
                    else return getSquareDistanceCompartiments(ss);
                } else return Double.POSITIVE_INFINITY; // spots in different tracks -> no link possible
            }
        } else return super.squareDistanceTo(s);
    }
    
    protected double getSquareDistanceCompartiments(SpotWithinCompartment s) {
        if (this.compartiment.truncated || s.compartiment.truncated) {
            double d=  getSquareDistanceTruncated(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            if (displayPoles) logger.debug("truncated distance");
            if (displayPoles) displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp, d);
            return d;
        }
        Localization offsetType = this.localization.getOffsetType(s.localization);
        if (offsetType==null) return Double.POSITIVE_INFINITY;
        else if (Localization.UP.equals(offsetType)) {
            double d=  getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp, d);
            return d;
        } else if (Localization.LOW.equals(offsetType)) {
            double d =  getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown, d);
            return d;
        } else if (Localization.MIDDLE.equals(offsetType)) {
           double d=  getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d);
            return d;
        }  else if (Localization.UPPER_MIDDLE.equals(offsetType)) {
            double d1 = getSquareDistance(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp);
            double d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (NestedSpotRoiModifier.displayPoles) {
                if (d1>d2) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d2);
                else displayOffsets(this, this.compartiment.offsetUp, s, s.compartiment.offsetUp, d1);
            }
            return Math.min(d1, d2);
        } else { // LOWER_MIDDLE
            double d1 = getSquareDistance(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown);
            double d2 = getSquareDistance(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle);
            if (NestedSpotRoiModifier.displayPoles) {
                if (d1>d2) displayOffsets(this, this.compartiment.offsetDivisionMiddle, s, s.compartiment.offsetDivisionMiddle, d2);
                else displayOffsets(this, this.compartiment.offsetDown, s, s.compartiment.offsetDown, d1);
            }
            return Math.min(d1, d2);
        }
    }
    protected double getSquareDistanceDivision(SpotWithinCompartment sAfterDivision) {
        Localization[] offsetType = localization.getOffsetTypeDivision(sAfterDivision.localization, sAfterDivision.compartiment.upperDaughterCell);
        if (displayPoles) logger.debug("distance division offsets: {} (upper daughter cells ? : {})", offsetType==null? "null" : Utils.toStringArray(offsetType, o->o.toString()), sAfterDivision.compartiment.upperDaughterCell);
        if (offsetType==null) return Double.POSITIVE_INFINITY;
        if (offsetType.length==2) {
            Point off1  = this.compartiment.getOffset(offsetType[0]);
            Point off2  = sAfterDivision.compartiment.getOffset(offsetType[1]);
            if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, off1, sAfterDivision, off2, getSquareDistance(this, off1, sAfterDivision, off2));
            return getSquareDistance(this, off1, sAfterDivision, off2);
        } else {
            Point off1  = this.compartiment.getOffset(offsetType[0]);
            Point off2  = sAfterDivision.compartiment.getOffset(offsetType[1]);
            double d1 = getSquareDistance(this, off1, sAfterDivision, off2);
            Point off12  = this.compartiment.getOffset(offsetType[2]);
            Point off22  = sAfterDivision.compartiment.getOffset(offsetType[3]);
            double d2 = getSquareDistance(this, off12, sAfterDivision, off22);
            if (d1>d2) {
                if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, off12, sAfterDivision, off22, d2);
                return d2;
            } else {
                if (NestedSpotRoiModifier.displayPoles) displayOffsets(this, off1, sAfterDivision, off2, d1);
                return d1;
            }
        }
        
    }
    /*protected static double getSquareDistance(Spot s1, double[] offset1, Spot s2, double[] offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        return Math.pow((s1.getFeature(POSITION_X)-offset1.get(0) - s2.getFeature(POSITION_X)+offset2.get(0)), 2) +
            Math.pow((s1.getFeature(POSITION_Y)-offset1.get(1) - s2.getFeature(POSITION_Y)+offset2.get(1)), 2) + 
            Math.pow((s1.getFeature(POSITION_Z)-offset1.get(2) - s2.getFeature(POSITION_Z)+offset2.get(2)), 2);
    }*/
    
    @Override public String toString() {
        return "{"+frame+"-"+(object.getLabel()-1)+"}"; //+"["+getFeature(POSITION_X)+";"+getFeature(POSITION_Y)+"]}";
        //return "{F="+frame+"|Idx="+(object.getLabel()-1)+"|"+localization+"|LQ="+lowQuality+"C=["+getFeature(POSITION_X)+";"+getFeature(POSITION_Y)+"]}";
    }
    protected static double getSquareDistanceTruncated(SpotWithinCompartment s1, Point offset1, SpotWithinCompartment s2, Point offset2) {
        double nextFactorY = Double.isNaN(s1.compartiment.sizeIncrement) ? 1 : s1.compartiment.sizeIncrement;
        double x1 = s1.getFeature(POSITION_X);
        double x2 = s2.getFeature(POSITION_X);
        double y1 = s1.getFeature(POSITION_Y);
        double y2 = s2.getFeature(POSITION_Y);
        double z1 = s1.getFeature(POSITION_Z);
        double z2 = s2.getFeature(POSITION_Z);
        double d = Math.pow((x1-offset1.get(0) - x2+offset2.get(0)), 2) + Math.pow((y1-offset1.get(1) - (y2-offset2.get(1)) / nextFactorY), 2) +  Math.pow((z1-offset1.get(2) - z2+offset2.get(2)), 2);
        d+= s1.distanceParameters.getSquareDistancePenalty(d, s1, s2);
        return d;
    }
    protected static double getSquareDistance(SpotWithinCompartment s1, Point offset1, SpotWithinCompartment s2, Point offset2) {
        if (offset1==null || offset2==null) return Double.POSITIVE_INFINITY; //TODO fix bug -> when reach test's limit timePoint, offset are null
        double x1 = s1.getFeature(POSITION_X);
        double x2 = s2.getFeature(POSITION_X);
        double y1 = s1.getFeature(POSITION_Y);
        double y2 = s2.getFeature(POSITION_Y);
        double z1 = s1.getFeature(POSITION_Z);
        double z2 = s2.getFeature(POSITION_Z);
        double d = Math.pow((x1-offset1.get(0) - x2+offset2.get(0)), 2) + Math.pow((y1-offset1.get(1) - y2+offset2.get(1)), 2) +  Math.pow((z1-offset1.get(2) - z2+offset2.get(2)), 2);
        // correction to favorize a specific direction towards a pole
        //double dPole1 = Math.pow((x1-offset1.get(0)), 2) + Math.pow((y1-offset1.get(1)), 2) +  Math.pow((z1-offset1.get(2)), 2);
        //double dPole2 = Math.pow((x2-offset2.get(0)), 2) + Math.pow((y2-offset2.get(1)), 2) +  Math.pow((z2-offset2.get(2)), 2);
        //if (dPole2>dPole1) d+=poleDistanceFactor*(Math.pow(Math.sqrt(dPole2)-Math.sqrt(dPole1), 2));
        /*double dPole1 = Math.abs(y1-offset1.get(1));
        double dPole2 = Math.abs(y2-offset2.get(1));
        if (dPole2>dPole1) d+=poleDistanceFactor * (dPole2-dPole1);*/
        // additional gap penalty
        d+= s1.distanceParameters.getSquareDistancePenalty(d, s1, s2);
        return d;
    }
    public static List<Roi> rois;
    public static BoundingBox offsetS1, offsetS2;
    private static void displayOffsets(SpotWithinCompartment s1, Point offset1, SpotWithinCompartment s2, Point offset2, double distance ) {
        if (rois!=null) {
            //if (distance>NestedSpotRoiModifier.displayDistanceThreshold) return;
            //BoundingBox off1 = bacteria.getObjectOffset(s1.compartiment.object).duplicate().translate(s1.compartiment.object.getBounds().duplicate().reverseOffset());
            //BoundingBox off2 = bacteria.getObjectOffset(s2.compartiment.object).duplicate().translate(s2.compartiment.object.getBounds().duplicate().reverseOffset());
            
            int[] c1 = s1.getCenterInVoxels();
            int[] c2 = s2.getCenterInVoxels();
            
            BoundingBox off1 = new SimpleBoundingBox(offsetS1).translate(new SimpleBoundingBox(s1.getObject().getBounds()).reverseOffset());
            BoundingBox off2 = new SimpleBoundingBox(offsetS2).translate(new SimpleBoundingBox(s2.getObject().getBounds()).reverseOffset());
            
            int[] cOff1 = new int[]{(int) Math.round(offset1.get(0) / s1.object.getScaleXY()), (int) Math.round(offset1.get(1) / s1.object.getScaleXY())};
            int[] cOff2 = new int[]{(int) Math.round(offset2.get(0) / s1.object.getScaleXY()), (int) Math.round(offset2.get(1) / s1.object.getScaleXY())};
            c1[0]+=off1.xMin()+0.5;
            c1[1]+=off1.yMin()+0.5;
            c2[0]+=off2.xMin()+0.5;
            c2[1]+=off2.yMin()+0.5;
            cOff1[0]+=off1.xMin()+0.5;
            cOff1[1]+=off1.yMin()+0.5;
            cOff2[0]+=off2.xMin()+0.5;
            cOff2[1]+=off2.yMin()+0.5;
            Line l1 = new Line(c1[0], c1[1], cOff1[0], cOff1[1]);
            Line l2 = new Line(c2[0], c2[1], cOff2[0], cOff2[1]);
            Line l12 = new Line((c1[0]+cOff1[0])/2d, (c1[1]+cOff1[1])/2d, (c2[0]+cOff2[0])/2d, (c2[1]+cOff2[1]) /2d );
            
            rois.add(l1);
            rois.add(l2);
            rois.add(l12);
            TextRoi position  = new TextRoi((c1[0]+cOff1[0]+c2[0]+cOff2[0])/4d, (c1[1]+cOff1[1]+c2[1]+cOff2[1])/4d, Utils.formatDoubleScientific(1, Math.sqrt(distance)));
            rois.add(position);
        }
    }
    public static enum Localization {
        UP, UPPER_MIDDLE, MIDDLE, LOWER_MIDDLE, LOW;
        public Localization getOffsetType(Localization other) {
            if (UP.equals(this)) {
                if (UP.equals(other) || UPPER_MIDDLE.equals(other) || MIDDLE.equals(other)) return UP;
                else return null;
            } else if (MIDDLE.equals(this)) {
                if (MIDDLE.equals(other) || UPPER_MIDDLE.equals(other) || LOWER_MIDDLE.equals(other)) return MIDDLE;
                else if (UP.equals(other)) return UP;
                else if (LOW.equals(other)) return LOW;
                else return null;
            } else if (LOW.equals(this)) {
                if (LOW.equals(other) || LOWER_MIDDLE.equals(other) || MIDDLE.equals(other)) return LOW;
                else return null;
            } else if (UPPER_MIDDLE.equals(this)) {
                if (UP.equals(other)) return UP;
                else if (MIDDLE.equals(other)) return MIDDLE;
                else if (UPPER_MIDDLE.equals(other)) return UPPER_MIDDLE;
                else return null;
            } else if (LOWER_MIDDLE.equals(this)) {
                if (LOW.equals(other)) return LOW;
                else if (MIDDLE.equals(other)) return MIDDLE;
                else if (LOWER_MIDDLE.equals(other)) return LOWER_MIDDLE;
                else return null;
            }
            return null;
        }
        public Localization[] getOffsetTypeDivision(Localization otherAfterDivision, boolean upperDaughterCell) {
            if (upperDaughterCell) {
                if (MIDDLE.equals(this)) {
                    if (LOW.equals(otherAfterDivision) || LOWER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, LOW};
                    else if (UPPER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, LOW, UP, UP};
                    else return null;
                } else if (UPPER_MIDDLE.equals(this)) {
                    if (MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP, MIDDLE, LOW};
                    if (UPPER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP};
                    if (LOWER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, LOW};
                    else return null;
                } else if (UP.equals(this)) {
                    if (UP.equals(otherAfterDivision) || UPPER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{UP, UP};
                    else return null;
                } else return null;
            } else {
                if (MIDDLE.equals(this)) {
                    if (UP.equals(otherAfterDivision) || UPPER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP};
                    else if (LOWER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP, LOW, LOW};
                    else return null;
                } else if (LOWER_MIDDLE.equals(this)) {
                    if (MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP, LOW, LOW};
                    if (UPPER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{MIDDLE, UP};
                    if (LOWER_MIDDLE.equals(otherAfterDivision)) return new Localization[]{LOW, LOW};
                    else return null;
                } else if (LOW.equals(this)) {
                    if (LOW.equals(otherAfterDivision) || LOWER_MIDDLE.equals(otherAfterDivision) || MIDDLE.equals(otherAfterDivision)) return new Localization[]{LOW, LOW};
                    else return null;
                } else return null;
            }
        }
    };
    
}