package image;

import java.util.TreeMap;

public abstract class ImageInteger extends Image implements ImageMask {

    protected ImageInteger(String name, int sizeX, int sizeY, int sizeZ, int offsetX, int offsetY, int offsetZ, float scaleXY, float scaleZ) {
        super(name, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, scaleXY, scaleZ);
    }
    
    protected ImageInteger(String name, int sizeX, int sizeY, int sizeZ) {
        super(name, sizeX, sizeY, sizeZ);
    }
    
    protected ImageInteger(String name, ImageProperties properties) {
        super(name, properties);
    } 
    @Override public abstract ImageInteger duplicate(String name);
    public abstract int getPixelInt(int x, int y, int z);
    public abstract int getPixelInt(int xy, int z);
    public abstract void setPixel(int x, int y, int z, int value);
    public abstract void setPixel(int xy, int z, int value);
    
    /**
     * 
     * @param addBorder
     * @return TreeMap with Key (Integer) = label of the object / Value Bounding Box of the object
     * @see BoundingBox
     */
    public TreeMap<Integer, BoundingBox> getBounds(boolean addBorder) {
        TreeMap<Integer, BoundingBox> bounds = new TreeMap<Integer, BoundingBox>();
        for (int z = 0; z < sizeZ; ++z) {
            for (int y = 0; y < sizeY; ++y) {
                for (int x = 0; x < sizeX; ++x) {
                    int value = getPixelInt(x + y * sizeX, z);
                    if (value != 0) {
                        BoundingBox bds = bounds.get(value);
                        if (bds != null) {
                            bds.expandX(x);
                            bds.expandY(y);
                            bds.expandZ(z);
                            bds.addToCounter();
                        } else {
                            bds= new BoundingBox(x, y, z);
                            bounds.put(value, bds);
                        }
                    }
                }
            }
        }
        if (addBorder) {
            for (BoundingBox bds : bounds.values()) {
                bds.addBorder();
                //bds.trimToImage(this);
            }
        }
        return bounds;
    }

    public ImageByte cropLabel(int label, BoundingBox bounds) {
        //bounds.trimToImage(this);
        ImageByte res = new ImageByte(name, bounds.getImageProperties("", scaleXY, scaleZ));
        byte[][] pixels = res.getPixelArray();
        res.setCalibration(this);
        int x_min = bounds.getxMin();
        int y_min = bounds.getyMin();
        int z_min = bounds.getzMin();
        int x_max = bounds.getxMax();
        int y_max = bounds.getyMax();
        int z_max = bounds.getzMax();
        res.setOffset(bounds);
        int sX = res.getSizeX();
        int oZ = -z_min;
        int oY_i = 0;
        int oX = 0;
        oX=-x_min;
        if (x_min <= -1) {
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
        for (int z = z_min; z <= z_max; ++z) {
            int oY = oY_i;
            for (int y = y_min; y <= y_max; ++y) {
                for (int x = x_min; x<=x_max; ++x) {
                    if (getPixelInt(x, y, z) == label) {
                        pixels[z + oZ][oY + x + oX] = (byte) 1;
                    }
                }
                oY += sX;
            }
        }
        return res;
    }
}