/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.trackers.nested_spot_tracker;

import boa.image.processing.bacteria_spine.BacteriaSpineLocalizer.PROJECTION;
import boa.plugins.plugins.trackers.nested_spot_tracker.SpotWithinCompartment;

/**
 *
 * @author jollion
 */
public class DistanceComputationParameters {
        public double qualityThreshold = 0;
        public double gapDistancePenalty = 0;
        private double gapSquareDistancePenalty;
        public double alternativeDistance;
        public boolean includeLQ = true;
        public boolean allowGapBetweenLQ = false;
        int maxFrameDiff = 0;
        public PROJECTION projectionType=PROJECTION.NEAREST_POLE;
        public DistanceComputationParameters() {
            
        }
        public DistanceComputationParameters setMaxFrameDifference(int maxFrameGap) {
            this.maxFrameDiff = maxFrameGap;
            return this;
        }
        public DistanceComputationParameters setProjectionType(PROJECTION projectionType) {
            this.projectionType = projectionType;
            return this;
        }
        public DistanceComputationParameters setAllowGCBetweenLQ(boolean allow) {
            this.allowGapBetweenLQ = allow;
            return this;
        }
        public DistanceComputationParameters setQualityThreshold(double qualityThreshold) {
            this.qualityThreshold=qualityThreshold;
            return this;
        }
        public DistanceComputationParameters setGapDistancePenalty(double gapDistancePenalty) {
            this.gapSquareDistancePenalty=gapDistancePenalty*gapDistancePenalty;
            this.gapDistancePenalty=gapDistancePenalty;
            return this;
        }
        public DistanceComputationParameters setAlternativeDistance(double alternativeDistance) {
            this.alternativeDistance=alternativeDistance;
            return this;
        }
        public double getSquareDistancePenalty(double distance, SpotWithinCompartment s, SpotWithinCompartment t) {
            int delta = Math.abs(t.frame-s.frame);
            if (!allowGapBetweenLQ && delta>1 && s.lowQuality && t.lowQuality) return Double.POSITIVE_INFINITY; // no gap closing between LQ spots
            return delta*delta * (gapSquareDistancePenalty + 2*gapDistancePenalty*distance); // pow* -> working on square distances
        }
    }
