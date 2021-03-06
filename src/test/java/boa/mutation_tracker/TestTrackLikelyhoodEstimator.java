/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.mutation_tracker;

import static boa.test_utils.TestUtils.logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import static org.junit.Assert.assertArrayEquals;
import org.junit.Before;
import org.junit.Test;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.DistancePenaltyScoreFunction;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator.DistributionFunction;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator.HarmonicScoreFunction;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator.LinearTrimmedFunction;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator.ScoreFunction;
import boa.plugins.plugins.trackers.nested_spot_tracker.post_processing.TrackLikelyhoodEstimator.Track;

/**
 *
 * @author Jean Ollion
 */
public class TestTrackLikelyhoodEstimator {
    TrackLikelyhoodEstimator estimator;
    static double distance = 0.012;
    @Before
    public void setUp() {
        //estimator = new TrackLikelyhoodEstimator(new NormalDistribution(11.97, 1.76), new BetaDistribution(0.735, 12.69), 7); squareDistance
        //estimator = new TrackLikelyhoodEstimator(new NormalDistribution(11.97, 1.76), new BetaDistribution(1.94, 7.66), 7);
        ScoreFunction sf = new DistancePenaltyScoreFunction(new NormalDistribution(11.97, 1.76), new BetaDistribution(1.94, 7.66), 0.2, 0.02);
        //ScoreFunction sf = new DistancePenaltyScoreFunction(new NormalDistribution(11.97, 1.76), 0.2, 0.75, 100000);
        logger.debug("distance function: 0={} 0.3={}, 0.4={}, 0.5={}, 0.6={}, 0.7={}, 1={}", sf.getDistanceFunction().y(0), sf.getDistanceFunction().y(0.3), sf.getDistanceFunction().y(0.4), sf.getDistanceFunction().y(0.5), sf.getDistanceFunction().y(0.6), sf.getDistanceFunction().y(0.7), sf.getDistanceFunction().y(1));
        estimator = new TrackLikelyhoodEstimator(sf, 5, 3);
    }

    //@Test
    public void testNoSplit() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        int[][] expected = new int[][]{frames};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitSymetrical() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, new int[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitSymetricalWithGaps() {
        int[] frames = new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
        int[][] expected = new int[][]{new int[]{0, 2, 4, 6, 8, 10}, new int[]{12, 14, 16, 18, 20, 22}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test2SplitSymetrical() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new int[]{12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23}, new int[]{24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test2SplitSymetricalWithGaps() {
        int[] frames = new int[]{0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34};
        int[][] expected = new int[][]{new int[]{0, 2, 4, 6, 8, 10}, new int[]{12, 14, 16, 18, 20, 22}, new int[]{24, 26, 28, 30, 32, 34}};
        assertMatrix(frames, expected);
    }
    //@Test
    public void test1SplitAsymetricalDistance() {
        int[] frames = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21};
        double[] distances = getDistanceArray(distance, frames.length);
        distances[11]=0.5d;
        distances[10]=0.35d;
        int[][] expected = new int[][]{new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11}, new int[]{12, 13, 14, 15, 16, 17, 18, 19, 20, 21}};
        assertMatrix(frames, distances, expected);
    }
    @Test
    public void test1SplitAsymetricalDistanceRealCase() {
        int[] frames = new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 17, 18, 19, 20, 21, 22, 23, 24};
        double[] distances = new double[]{0.14972023128400522, 0.3734449163793408, 0.170631287572104, 0.1458823084692441, 0.20525949513820224, 0.20617831380148485, 0.3977923896408545, 0.2727064445970444, 0.5026323104168298, 0.3037294360266413, 0.8275628367273787, 0.34628698315987144, 0.11777534350912602, 0.3691055058007658, 0.051833935557077326, 0.1004869683371068, 0.3701944992342838, 0.47172129827427517};
        int[][] expected = new int[][]{new int[]{4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15}, new int[]{17, 18, 19, 20, 21, 22, 23, 24}};
        assertMatrix(frames, distances, expected);
    }
    
    private void assertMatrix(int[] frames, int[][] expected) {
        assertMatrix(frames, getDistanceArray(distance, frames.length), expected);
    }
    
    private void assertMatrix(int[] frames, double[] distances, int[][] expected) {
        if (frames.length!=distances.length+1) throw new IllegalArgumentException("invalid distances/frames couple");
        int[][] observed = toMatrix(estimator.splitTrack(new Track(frames, distances)).splitTrack(toList(frames)));
        assertEquals("same division number:", expected.length, observed.length);
        for (int i = 0; i<expected.length; ++i) {
            logger.debug("observed array: {}", observed[i]);
            assertArrayEquals(" frames @"+i, expected[i], observed[i]);
        }
    }
    
    private static double[] getDistanceArray(double d, int frameNumber) {
        double[] res= new double[frameNumber-1];
        Arrays.fill(res, d);
        return res;
    }
    private List<Integer> toList(int[] array) {
        List<Integer> res = new ArrayList<Integer>(array.length);
        for (int i : array) res.add(i);
        return res;
    }
    private int[] toPrimitive(List<Integer> list) {
        int[] res = new int[list.size()];
        for (int i = 0; i<res.length; ++i) res[i] = list.get(i);
        return res;
    }
    private int[][] toMatrix(List<List<Integer>> list) {
        int[][] res = new int[list.size()][];
        for (int i = 0; i<list.size(); ++i) {
            res[i] = toPrimitive(list.get(i));
        }
        return res;
    }
}
