package image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import processing.neighborhood.Neighborhood;


public abstract class Image implements ImageProperties {
    public final static Logger logger = LoggerFactory.getLogger(Image.class);
    protected String name;
    protected int sizeX;
    protected int sizeY;
    protected int sizeZ;
    protected int sizeXY;
    protected int sizeXYZ;
    protected int offsetX;
    protected int offsetY;
    protected int offsetZ;
    protected float scaleXY;
    protected float scaleZ;
    protected LUT lut=LUT.Grays;
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ=sizeZ>=1?sizeZ:1;
        this.sizeXY=sizeX*sizeY;
        this.sizeXYZ=sizeXY*sizeZ;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scaleXY = scaleXY;
        this.scaleZ = scaleZ;
    }
    
    protected Image(String name, int sizeX, int sizeY, int sizeZ) {
        this(name, sizeX, sizeY, sizeZ, 0, 0, 0, 1, 1);
    }
    
    protected Image(String name, ImageProperties properties) {
        this(name, properties.getSizeX(), properties.getSizeY(), properties.getSizeZ(), properties.getOffsetX(), properties.getOffsetY(), properties.getOffsetZ(), properties.getScaleXY(), properties.getScaleZ());
    }
    
    public <T extends Image> T setName(String name) {
        this.name=name;
        return (T)this;
    }
    
    public static <T> T createEmptyImage(String name, T imageType, ImageProperties properties) {
        if (imageType instanceof ImageByte) return (T)new ImageByte(name, properties);
        else if (imageType instanceof ImageShort) return (T)new ImageShort(name, properties);
        else if (imageType instanceof ImageInt) return (T)new ImageInt(name, properties);
        else if (imageType instanceof ImageFloat) return (T)new ImageFloat(name, properties);
        else return (T)new BlankMask(name, properties);
    }
    
    public abstract Image getZPlane(int idxZ);
    
    @Override
    public boolean sameSize(ImageProperties other) {
        return sizeX==other.getSizeX() && sizeY==other.getSizeY() && sizeZ==other.getSizeZ();
    }
    
    //public abstract float getPixel(float x, float y, float z); // interpolation
    public abstract float getPixel(int x, int y, int z);
    public abstract float getPixel(int xz, int z);
    public abstract void setPixel(int x, int y, int z, double value);
    public abstract void setPixelWithOffset(int x, int y, int z, double value);
    public abstract void setPixel(int xy, int z, double value);
    public abstract Object[] getPixelArray();
    public abstract <T extends Image> T duplicate(String name);
    public abstract Image newImage(String name, ImageProperties properties);
    public abstract <T extends Image> T crop(BoundingBox bounds);
    
    
    @Override
    public boolean contains(int x, int y, int z) {
        return (x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ);
    }

    @Override
    public boolean containsWithOffset(int x, int y, int z) {
        x-=offsetX; y-=offsetY; z-=offsetZ;
        return (x >= 0 && x < sizeX && y >= 0 && y-offsetY < sizeY && z >= 0 && z < sizeZ);
    }
    
    public <T extends Image> T resetOffset() {
        offsetX=offsetY=offsetZ=0;
        return (T)this;
    }
    
    public <T extends Image> T addOffset(ImageProperties properties) {
        this.offsetX+=properties.getOffsetX();
        this.offsetY+=properties.getOffsetY();
        this.offsetZ+=properties.getOffsetZ();
        return (T)this;
    }
    
    public <T extends Image> T addOffset(int offsetX, int offsetY, int offsetZ) {
        this.offsetX+=offsetX;
        this.offsetY+=offsetY;
        this.offsetZ+=offsetZ;
        return (T)this;
    }
    
    public <T extends Image> T addOffset(BoundingBox bounds) {
        this.offsetX=bounds.xMin;
        this.offsetY=bounds.yMin;
        this.offsetZ=bounds.zMin;
        return (T)this;
    }

    public <T extends Image> T setCalibration(ImageProperties properties) {
        this.scaleXY=properties.getScaleXY();
        this.scaleZ=properties.getScaleZ();
        return (T)this;
    }
    
    public <T extends Image> T setCalibration(float scaleXY, float scaleZ) {
        this.scaleXY=scaleXY;
        this.scaleZ=scaleZ;
        return (T)this;
    }
    
    public BoundingBox getBoundingBox() {
        BoundingBox res = new BoundingBox(0, sizeX-1, 0, sizeY-1, 0, sizeZ-1);
        res.translate(offsetX, offsetY, offsetZ);
        return res;
    }
    public float[] getMinAndMax(ImageMask mask) {
        return getMinAndMax(mask, null);
    }
    /**
     * 
     * @param mask min and max are computed within the mask, or within the whole image if mask==null 
     * @return float[]{min, max}
     */
    public float[] getMinAndMax(ImageMask mask, BoundingBox limits) {
        if (mask==null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (int z = limits.zMin; z <= limits.zMax; z++) {
            for (int y = limits.yMin; y<=limits.yMax; ++y) {
                for (int x = limits.xMin; x <= limits.xMax; ++x) {
                    if (mask.insideMask(x, y, z)) {
                        if (getPixel(x, y, z) > max) {
                            max = getPixel(x, y, z);
                        }
                        if (getPixel(x, y, z) < min) {
                            min = getPixel(x, y, z);
                        }
                    }
                }
            }
        }
        return new float[]{min, max};
    }
    public abstract int[] getHisto256(ImageMask mask, BoundingBox bounds);
    public int[] getHisto256(ImageMask mask) {return getHisto256(mask, null);}
    abstract int[] getHisto256(double min, double max, ImageMask mask, BoundingBox limit);
    
    
    
    
    protected Image cropI(BoundingBox bounds) {
        //bounds.trimToImage(this);
        Image res = newImage(name, bounds.getImageProperties("", scaleXY, scaleZ));
        res.setCalibration(this);
        res.resetOffset().addOffset(bounds);
        int x_min = bounds.getxMin();
        int y_min = bounds.getyMin();
        int z_min = bounds.getzMin();
        int x_max = bounds.getxMax();
        int y_max = bounds.getyMax();
        int z_max = bounds.getzMax();
        int sX = x_max - x_min + 1;
        int oZ = -z_min;
        int oY_i = 0;
        int oX = 0;
        if (x_min <= -1) {
            oX=-x_min;
            x_min = 0;
        }
        if (x_max >= sizeX) {
            x_max = sizeX - 1;
        }
        if (y_min <= -1) {
            oY_i = -sX * y_min;
            y_min = 0;
        }
        if (y_max >= sizeY) {
            y_max = sizeY - 1;
        }
        if (z_min <= -1) {
            z_min = 0;
        }
        if (z_max >= sizeZ) {
            z_max = sizeZ - 1;
        }
        int sXo = x_max - x_min + 1;
        for (int z = z_min; z <= z_max; ++z) {
            int offY = y_min * sizeX;
            int oY = oY_i;
            for (int y = y_min; y <= y_max; ++y) {
                System.arraycopy(getPixelArray()[z], offY + x_min, res.getPixelArray()[z + oZ], oY + oX, sXo);
                oY += sX;
                offY += sizeX;
            }
        }
        return res;
    }
    
    
    //public abstract Image[] crop(int[][] bounds);
    
    public int getSizeX() {
        return sizeX;
    }

    public int getSizeY() {
        return sizeY;
    }

    public int getSizeZ() {
        return sizeZ;
    }

    public int getSizeXY() {
        return sizeXY;
    }

    public int getSizeXYZ() {
        return sizeXYZ;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    public float getScaleXY() {
        return scaleXY;
    }

    public float getScaleZ() {
        return scaleZ;
    }
    
    public String getName() {
        return name;
    }
    
    
}
