package boa.image;

import ij.process.StackStatistics;
import boa.image.processing.neighborhood.Neighborhood;

public class ImageShort extends ImageInteger {

    private short[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageShort(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new short[sizeZ][sizeXY];
    }
    
    public ImageShort(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new short[sizeZ][sizeX*sizeY];
    }
    
    public ImageShort(String name, int sizeX, short[][] pixels) {
        super(name, sizeX, sizeX>0?pixels[0].length/sizeX:0, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageShort(String name, int sizeX, short[] pixels) {
        super(name, sizeX, sizeX>0?pixels.length/sizeX:0, 1);
        this.pixels=new short[][]{pixels};
    }
    
    @Override
    public ImageShort getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageShort res = new ImageShort(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    
    @Override
    public int getPixelInt(int x, int y, int z) {
        return pixels[z][x + y * sizeX] & 0xffff;
    }

    @Override
    public int getPixelInt(int xy, int z) {
        return pixels[z][xy] & 0xffff;
    }
    
    @Override
    public float getPixel(int xy, int z) {
        return (float) (pixels[z][xy] & 0xffff);
    }

    @Override
    public float getPixel(int x, int y, int z) {
        return (float) (pixels[z][x + y * sizeX] & 0xffff);
    }
    
    
    @Override
    public float getPixelLinInterX(int x, int y, int z, float dx) {
        if (dx==0) return (float) (pixels[z][x + y * sizeX] & 0xffff);
        return (float) ((pixels[z][x + y * sizeX] & 0xffff) * (1-dx) + dx * (pixels[z][x + 1 + y * sizeX] & 0xffff));
    }

    @Override
    public void setPixel(int x, int y, int z, int value) {
        pixels[z][x + y * sizeX] = (short) value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, int value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = (short)value;
    }

    @Override
    public void setPixel(int xy, int z, int value) {
        pixels[z][xy] = (short) value;
    }

    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x + y * sizeX] = value<0?0:(value>65535?(short)65535:(short)value);
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetXY + y * sizeX] = value<0?0:(value>65535?(short)65535:(short)value);
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy] = value<0?0:(value>65535?(short)65535:(short)value);
    }
    
    @Override
    public int getPixelIntWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX] & 0xffff;
    }

    @Override
    public int getPixelIntWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ] & 0xffff;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, int value) {
        pixels[z-offsetZ][xy - offsetXY] = value<0?0:(value>65535?(short)65535:(short)value);
    }

    @Override
    public float getPixelWithOffset(int x, int y, int z) {
        return pixels[z-offsetZ][x-offsetXY + y * sizeX] & 0xffff;
    }

    @Override
    public float getPixelWithOffset(int xy, int z) {
        return pixels[z-offsetZ][xy - offsetXY ] & 0xffff;
    }

    @Override
    public void setPixelWithOffset(int xy, int z, double value) {
        pixels[z-offsetZ][xy - offsetXY] = value<0?0:(value>65535?(short)65535:(short)value);
    }

    @Override
    public ImageShort duplicate(String name) {
        short[][] newPixels = new short[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageShort(name, sizeX, newPixels).setCalibration(this).addOffset(this);
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
    public short[][] getPixelArray() {
        return pixels;
    }
    
    @Override
    public ImageShort newImage(String name, ImageProperties properties) {
        return new ImageShort(name, properties);
    }
    
    @Override
    public ImageShort crop(BoundingBox bounds) {
        return (ImageShort) cropI(bounds);
    }
    
    @Override
    public ImageShort cropWithOffset(BoundingBox bounds) {
        return (ImageShort) cropIWithOffset(bounds);
    }
    
    @Override
    public void invert() {
        double[] minAndMax = this.getMinAndMax(null);
        int off = (int) (minAndMax[1] + minAndMax[0]);
        for (int z = 0; z < sizeZ; z++) {
            for (int xy = 0; xy<sizeXY; ++xy) {
                pixels[z][xy] = (short) (off - pixels[z][xy]&0xffff);
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
                                pixels[zz][xx + yy * sizeX] = (short)label;
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
    @Override public int getBitDepth() {return 16;}
}