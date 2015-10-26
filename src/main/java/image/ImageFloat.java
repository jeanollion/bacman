package image;

import processing.neighborhood.Neighborhood;

public class ImageFloat extends Image {

    final private float[][] pixels;

    /**
     * Builds a new blank image with same properties as {@param properties}
     * @param name name of the new image
     * @param properties properties of the new image
     */
    public ImageFloat(String name, ImageProperties properties) {
        super(name, properties);
        this.pixels=new float[sizeZ][sizeXY];
    }
    
    public ImageFloat(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
        this.pixels=new float[sizeZ][sizeX*sizeY];
    }
    
    public ImageFloat(String name, int sizeX, float[][] pixels) {
        super(name, sizeX, pixels[0].length/sizeX, pixels.length);
        this.pixels=pixels;
    }
    
    public ImageFloat(String name, int sizeX, float[] pixels) {
        super(name, sizeX, pixels.length/sizeX, 1);
        this.pixels=new float[][]{pixels};
    }
    
    @Override
    public ImageFloat getZPlane(int idxZ) {
        if (idxZ>=sizeZ) throw new IllegalArgumentException("Z-plane cannot be superior to sizeZ");
        else {
            ImageFloat res = new ImageFloat(name, sizeX, pixels[idxZ]);
            res.setCalibration(this);
            res.addOffset(offsetX, offsetY, offsetZ+idxZ);
            return res;
        }
    }
    
    @Override
    public float getPixel(int x, int y, int z) {
        return pixels[z][x+y*sizeX];
    }

    @Override
    public float getPixel(int xy, int z) {
        return pixels[z][xy];
    }
    
    @Override
    public void setPixel(int x, int y, int z, double value) {
        pixels[z][x+y*sizeX]=(float)value;
    }

    @Override
    public void setPixel(int xy, int z, double value) {
        pixels[z][xy]=(float)value;
    }
    
    public void setPixel(int x, int y, int z, float value) {
        pixels[z][x+y*sizeX]=value;
    }
    
    public void setPixelWithOffset(int x, int y, int z, float value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = value;
    }
    
    @Override
    public void setPixelWithOffset(int x, int y, int z, double value) {
        pixels[z-offsetZ][x-offsetX + (y-offsetY) * sizeX] = (float)value;
    }

    public void setPixel(int xy, int z, float value) {
        pixels[z][xy]=value;
    }

    @Override
    public ImageFloat duplicate(String name) {
        float[][] newPixels = new float[sizeZ][sizeXY];
        for (int z = 0; z< sizeZ; ++z) System.arraycopy(pixels[z], 0, newPixels[z], 0, sizeXY);
        return new ImageFloat(name, sizeX, newPixels);
    }
    
    @Override
    public float[][] getPixelArray() {
        return pixels;
    }

    @Override
    public ImageFloat newImage(String name, ImageProperties properties) {
        return new ImageFloat(name, properties);
    }

    @Override
    public ImageFloat crop(BoundingBox bounds) {
        return (ImageFloat) cropI(bounds);
    }

    @Override
    public int[] getHisto256(ImageMask mask, BoundingBox limit) {
        if (mask == null) mask = new BlankMask("", this);
        float[] minAndMax = getMinAndMax(mask);
        return getHisto256(minAndMax[0], minAndMax[1], mask, limit);
    }
    @Override int[] getHisto256(double min, double max, ImageMask mask, BoundingBox limits) {
        if (mask == null) mask = new BlankMask("", this);
        if (limits==null) limits = mask.getBoundingBox().translateToOrigin();
        double coeff = 256d / (max - min);
        int idx;
        int[] histo = new int[256];
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
        return histo;
    }
}
