package boa.image;

import boa.image.processing.neighborhood.Neighborhood;
import boa.utils.ArrayUtil;
import boa.utils.StreamConcatenation;
import boa.utils.Utils;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class ImageInt extends ImageInteger {

    private int[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageInt(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new int[sizeZ][sizeXY];
    }
    
    public ImageInt(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new int[sizeZ][sizeX*sizeY];
    }
    
    public ImageInt(String name, int sizeX, int[][] pixels) {
        super(name, sizeX, sizeX>0?pixels[0].length/sizeX:0, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageInt(String name, int sizeX, int[] pixels) {
        super(name, sizeX, sizeX>0?pixels.length/sizeX:0, 1);
        this.pixels=new int[][]{pixels};
    }
    
    @Override
    public ImageInt getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageInt res = new ImageInt(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    @Override public DoubleStream streamPlane(int z) {
        return ArrayUtil.stream(pixels[z]);
    }
    @Override public DoubleStream streamPlane(int z, ImageMask mask, boolean maskHasAbsoluteOffset) {
        if (maskHasAbsoluteOffset) {
            if (z<0 || z>=sizeZ || z+offsetZ-mask.getOffsetZ()<0 || z+offsetZ-mask.getOffsetZ()>=mask.getSizeZ()) return DoubleStream.empty();
            BoundingBox inter = getBoundingBox().getIntersection2D(mask.getBoundingBox());
            if (inter.getSizeXY()==0) return DoubleStream.empty();
            if (inter.equals(this) && inter.equals(mask)) {
                if (mask instanceof BlankMask) return this.streamPlane(z);
                else return IntStream.range(0,sizeXY).mapToDouble(i->mask.insideMask(i, z)?pixels[z][i]:Double.NaN).filter(v->!Double.isNaN(v));
            }
            else { // loop within intersection
                int sX = inter.getSizeX();
                int offX = inter.getxMin();
                int offY = inter.getyMin();
                return IntStream.range(0,inter.getSizeXY()).mapToDouble(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z+offsetZ)?pixels[z][x+y*sizeX-offsetXY]:Double.NaN;}
                ).filter(v->!Double.isNaN(v));
            }
        }
        else { // masks is relative to image
            if (z<0 || z>=sizeZ || z-mask.getOffsetZ()<0 || z-mask.getOffsetZ()>mask.getSizeZ()) return DoubleStream.empty();
            BoundingBox inter = getBoundingBox().translateToOrigin().getIntersection2D(mask.getBoundingBox());
            if (inter.getSizeXY()==0) return DoubleStream.empty();
            if (inter.equals(mask) && inter.sameDimensions(this)) {
                if (mask instanceof BlankMask) return this.streamPlane(z);
                else return IntStream.range(0, sizeXY).mapToDouble(i->mask.insideMask(i, z)?pixels[z][i]:Double.NaN).filter(v->!Double.isNaN(v));
            }
            else {
                int sX = inter.getSizeX();
                int offX = inter.getxMin();
                int offY = inter.getyMin();
                return IntStream.range(0,inter.getSizeXY()).mapToDouble(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z)?pixels[z][x+y*sizeX]:Double.NaN;}
                ).filter(v->!Double.isNaN(v));
            }
        }
    }
    @Override public IntStream streamIntPlane(int z) {
        return ArrayUtil.streamInt(pixels[z]);
    }
    @Override public IntStream streamIntPlane(int z, ImageMask mask, boolean maskHasAbsoluteOffset) {
        if (maskHasAbsoluteOffset) {
            if (z<0 || z>=sizeZ || z+offsetZ-mask.getOffsetZ()<0 || z+offsetZ-mask.getOffsetZ()>=mask.getSizeZ()) return IntStream.empty();
            BoundingBox inter = getBoundingBox().getIntersection2D(mask.getBoundingBox());
            if (inter.getSizeXY()==0) return IntStream.empty();
            if (inter.equals(this) && inter.equals(mask)) {
                if (mask instanceof BlankMask) return this.streamIntPlane(z);
                else return IntStream.range(0,sizeXY).map(i->mask.insideMask(i, z)?pixels[z][i]:Integer.MAX_VALUE).filter(v->v!=Integer.MAX_VALUE);
            }
            else { // loop within intersection
                int sX = inter.getSizeX();
                int offX = inter.getxMin();
                int offY = inter.getyMin();
                return IntStream.range(0,inter.getSizeXY()).map(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z+offsetZ)?pixels[z][x+y*sizeX-offsetXY]:Integer.MAX_VALUE;}
                ).filter(v->v!=Integer.MAX_VALUE);
            }
        }
        else { // masks is relative to image
            if (z<0 || z>=sizeZ || z-mask.getOffsetZ()<0 || z-mask.getOffsetZ()>mask.getSizeZ()) return IntStream.empty();
            BoundingBox inter = getBoundingBox().translateToOrigin().getIntersection2D(mask.getBoundingBox());
            if (inter.getSizeXY()==0) return IntStream.empty();
            if (inter.equals(mask) && inter.sameDimensions(this)) {
                if (mask instanceof BlankMask) return this.streamIntPlane(z);
                else return IntStream.range(0, sizeXY).map(i->mask.insideMask(i, z)?pixels[z][i]:Integer.MAX_VALUE).filter(v->v!=Integer.MAX_VALUE);
            }
            else {
                int sX = inter.getSizeX();
                int offX = inter.getxMin();
                int offY = inter.getyMin();
                return IntStream.range(0,inter.getSizeXY()).map(i->{
                        int x = i%sX+offX;
                        int y = i/sX+offY;
                        return mask.insideMaskWithOffset(x, y, z)?pixels[z][x+y*sizeX]:Integer.MAX_VALUE;}
                ).filter(v->v!=Integer.MAX_VALUE);
            }
        }
    }
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX];
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy];
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy]);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX]);
    }
    
    
    @Override
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        if (dx==0) return (float) (pixels[z][x + y * sizeX]);
        return (float) ((pixels[z][x + y * sizeX]) * (1-dx) + dx * (pixels[z][x + 1 + y * sizeX]));
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = value;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x + y * sizeX] = (int)value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = (int)value;
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy] = (int)value;
    }

    @Override
    public int getPixelIntWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX];
    }

    @Override
    public int getPixelIntWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ];
    }

    @Override
    public void setPixelWithOffset(int xy, int z, int value) {
        pixels[z-offsetZ][xy - offsetXY] = value;
    }

    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX];
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ];
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
        pixels[z-offsetZ][xy - offsetXY] = (int) value;
    }
    
    @Override
    public ImageInt duplicate(String name) {
        int[][] newPixels = new int[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageInt(name, sizeX, newPixels).setCalibration(this).addOffset(this);
    }

    public boolean insideMask(int x, int y, int z) {
        return pixels[z][x+y*sizeX]!=0;
    }

    public boolean insideMask(int xy, int z) {
        return pixels[z][xy]!=0;
    }
    
    @Override public int count() {
        int count = 0;
        for (int z = 0; z< sizeZ; ++z) {
            for (int xy=0; xy<sizeXY; ++xy) {
                if (pixels[z][xy]!=0) ++count;
            }
        }
        return count;
    }
    
    public boolean insideMaskWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x+y*sizeX-offsetXY]!=0;
    }

    public boolean insideMaskWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy-offsetXY]!=0;
    }
    
    @Override
    public int[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageInt newImage(String name, ImageProperties properties) {
        return new ImageInt(name, properties);
    }
    
    @Override
    public ImageInt crop(BoundingBox bounds) {
        return (ImageInt) cropI(bounds);
    }
    
    @Override
    public ImageInt cropWithOffset(BoundingBox bounds) {
        return (ImageInt) cropIWithOffset(bounds);
    }
    
    @Override
    public void invert() {
        double[] minAndMax = this.getMinAndMax(null);
        int off = (int) (minAndMax[1] + minAndMax[0]);
        for (int z = 0; z < sizeZ; z++) {
            for (int xy = 0; xy<sizeXY; ++xy) {
                pixels[z][xy] = off - pixels[z][xy];
            }
        }
    }
    
    public void appendBinaryMasks(int startLabel, ImageMask... masks) {
        if (masks == null || masks.length==0) return;
        if (startLabel==-1) startLabel = (int)this.getMinAndMax(null)[1]+1;
        //if (startLabel<0) startLabel=1;
        for (int idx = 0; idx < masks.length; ++idx) {
            int label = idx+startLabel;
            ImageMask currentImage = masks[idx];
            for (int z = 0; z < currentImage.getSizeZ(); ++z) {
                for (int y = 0; y < currentImage.getSizeY(); ++y) {
                    for (int x = 0; x < currentImage.getSizeX(); ++x) {
                        if (currentImage.insideMask(x, y, z)) {
                            int xx = x + currentImage.getOffsetX();
                            int yy = y + currentImage.getOffsetY();
                            int zz = z + currentImage.getOffsetZ();
                            if (contains(xx, yy, zz)) {
                                pixels[zz][xx + yy * sizeX] = label;
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override 
    public Histogram getHisto256(ImageMask mask, BoundingBox limits) {
        if (mask==null) mask=new BlankMask("", this);
        double[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask, limits);
    }
    @Override public Histogram getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {
        if (mask == null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        double coeff = 256d / (max - min);
        int[] histo = new int[256];
        int idx;
        for (int z = limits.zMin; z <= limits.zMax; z++) {
            for (int y = limits.yMin; y<=limits.yMax; ++y) {
                for (int x = limits.xMin; x <= limits.xMax; ++x) {
                    if (mask.insideMask(x, y, z)) {
                    idx = (int) ((getPixel(x, y, z) - min) * coeff);
                    histo[idx>=256?255:idx]++;
                    }
                }
            }
        }
        return new Histogram(histo, false, new double[]{min, max});
    }
    @Override public int getBitDepth() {return 64;}
}
