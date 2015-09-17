/*
 * Copyright (C) 2015 nasique
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
package boa.gui.imageInteraction;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;
import image.IJImageWrapper;
import image.Image;
import static boa.gui.GUI.logger;

/**
 *
 * @author jollion
 */
public class IJImageDisplayer implements ImageDisplayer<ImagePlus> {
    public void showImage(Image image) {
        if (IJ.getInstance()==null) new ImageJ();
        ImagePlus ip = IJImageWrapper.getImagePlus(image);
        float[] minAndMax = image.getMinAndMax(null);
        ip.setDisplayRange(minAndMax[0], minAndMax[1]);
        ip.show();
    }

    public void showImage(ImagePlus image) {
        if (IJ.getInstance()==null) new ImageJ();
        StackStatistics s = new StackStatistics(image);
        logger.trace("display range: min: {} max: {}", s.min, s.max);
        image.setDisplayRange(s.min, s.max);
        image.show();
    }
}