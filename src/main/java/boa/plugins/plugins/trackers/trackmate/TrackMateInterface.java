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
package boa.plugins.plugins.trackers.trackmate;

import boa.plugins.plugins.trackers.nested_spot_tracker.DistanceComputationParameters;
import boa.ui.GUI;
import com.google.common.collect.Sets;
import boa.configuration.parameters.Parameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import fiji.plugin.trackmate.Logger;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_GAP_CLOSING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALTERNATIVE_LINKING_COST_FACTOR;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_CUTOFF_PERCENTILE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_GAP_CLOSING_MAX_FRAME_GAP;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_LINKING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_MERGING_MAX_DISTANCE;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_SPLITTING_MAX_DISTANCE;
import java.util.HashMap;
import fiji.plugin.trackmate.tracking.sparselap.SparseLAPFrameToFrameTracker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.slf4j.LoggerFactory;
import static boa.plugins.Plugin.logger;
import boa.utils.Pair;
import boa.utils.StreamConcatenation;
import boa.utils.SymetricalPair;
import boa.utils.Utils;
import boa.utils.geom.Point;
/**
 *
 * @author Jean Ollion
 */
public class TrackMateInterface<S extends Spot> {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(TrackMateInterface.class);
    public final HashMap<Region, S>  objectSpotMap = new HashMap<>();
    public final HashMap<S, Region>  spotObjectMap = new HashMap<>();
    private final SpotCollection collection = new SpotCollection();
    private Logger internalLogger = Logger.VOID_LOGGER;
    int numThreads=1;
    public String errorMessage;
    private SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph;
    public final SpotFactory<S> factory;

    public TrackMateInterface(SpotFactory<S> factory) {
        this.factory = factory;
    }
    public void resetEdges() {
        graph=null;
    }
    public void removeObject(Region o, int frame) {
        S s = objectSpotMap.remove(o);
        if (s!=null) {
            if (graph!=null) graph.removeVertex(s);
            spotObjectMap.remove(s);
            collection.remove(s, frame);
        }
    }
    
    public void addObject(Region o, int frame) {
        S s = factory.toSpot(o, frame);
        if (s==null) return; // in case no parent or parent's spine could not be created
        objectSpotMap.put(o, s);
        spotObjectMap.put(s, o);
        collection.add(s, frame);
    }
    
    public void addObjects(Collection<Region> objects, int frame) {
        objects.forEach((o) -> addObject(o, frame));
    }
    public void addObjects(Map<Integer, List<StructureObject>> objectsF) {
        StreamConcatenation.concatNestedCollections(objectsF.values()).forEach(o->addObject(o.getRegion(), o.getFrame()));
    }
    
    public boolean processFTF(double distanceThreshold) {
        long t0 = System.currentTimeMillis();
        // Prepare settings object
        final Map< String, Object > ftfSettings = new HashMap<>();
        ftfSettings.put( KEY_LINKING_MAX_DISTANCE, distanceThreshold );
        ftfSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        
        final SparseLAPFrameToFrameTrackerFromExistingGraph frameToFrameLinker = new SparseLAPFrameToFrameTrackerFromExistingGraph(collection, ftfSettings, graph );
        frameToFrameLinker.setNumThreads( numThreads );
        final Logger.SlaveLogger ftfLogger = new Logger.SlaveLogger( internalLogger, 0, 0.5 );
        frameToFrameLinker.setLogger( ftfLogger );

        if ( !frameToFrameLinker.checkInput() || !frameToFrameLinker.process()) {
                errorMessage = frameToFrameLinker.getErrorMessage();
                logger.error(errorMessage);
                return false;
        }
        graph = frameToFrameLinker.getResult();
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after FTF step: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
        return true;
    }
    
    public boolean processGC(double distanceThreshold, int maxFrameGap, boolean allowSplitting, boolean allowMerging) {
        long t0 = System.currentTimeMillis();
        Set<S> unlinkedSpots;
        if (graph == null) {
            graph = new SimpleWeightedGraph<>( DefaultWeightedEdge.class );
            unlinkedSpots = new HashSet<>(spotObjectMap.keySet()); 
        } else {
            Set<Spot> linkedSpots = graph.vertexSet();
            unlinkedSpots = new HashSet<>(Sets.difference(spotObjectMap.keySet(), linkedSpots));
        }
        Map<Spot, Spot> clonedSpots = new HashMap<>();
        // duplicate unlinked spots to include them in the gap-closing part
        for (S s : unlinkedSpots) {
            Spot clone = factory.duplicate(s);
            graph.addVertex(s);
            graph.addVertex(clone);
            clonedSpots.put(clone, s);
            graph.addEdge(s,clone);
            //logger.debug("unlinked object: f={}, Idx={}", s.getFeature(Spot.FRAME), spotObjectMap.get(s).getLabel()-1);
        }
        // Prepare settings object
        final Map< String, Object > slSettings = new HashMap<>();

        slSettings.put( KEY_ALLOW_GAP_CLOSING, maxFrameGap>1 );
        //slSettings.put( KEY_GAP_CLOSING_FEATURE_PENALTIES, settings.get( KEY_GAP_CLOSING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_GAP_CLOSING_MAX_DISTANCE, distanceThreshold );
        slSettings.put( KEY_GAP_CLOSING_MAX_FRAME_GAP, maxFrameGap );

        slSettings.put( KEY_ALLOW_TRACK_SPLITTING, allowSplitting );
        //slSettings.put( KEY_SPLITTING_FEATURE_PENALTIES, settings.get( KEY_SPLITTING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_SPLITTING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALLOW_TRACK_MERGING, allowMerging );
        //slSettings.put( KEY_MERGING_FEATURE_PENALTIES, settings.get( KEY_MERGING_FEATURE_PENALTIES ) );
        slSettings.put( KEY_MERGING_MAX_DISTANCE, distanceThreshold );

        slSettings.put( KEY_ALTERNATIVE_LINKING_COST_FACTOR, 1.05 );
        slSettings.put( KEY_CUTOFF_PERCENTILE, 1d );
        // Solve.
        final SparseLAPSegmentTracker segmentLinker = new SparseLAPSegmentTracker( graph, slSettings, new DistanceComputationParameters().setAlternativeDistance(distanceThreshold*1.05));
        //final fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker segmentLinker = new fiji.plugin.trackmate.tracking.sparselap.SparseLAPSegmentTracker( graph, slSettings);
        segmentLinker.setNumThreads(numThreads);
        final Logger.SlaveLogger slLogger = new Logger.SlaveLogger( internalLogger, 0.5, 0.5 );
        segmentLinker.setLogger( slLogger );
        if ( !segmentLinker.checkInput() || !segmentLinker.process() ) {
            errorMessage = segmentLinker.getErrorMessage();
            logger.error(errorMessage);
            return false;
        }
        for (Map.Entry<Spot, Spot> e : clonedSpots.entrySet()) transferLinks(e.getKey(), e.getValue());
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after GC step: {}, nb of vertices: {} (unlinked: {}), processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), unlinkedSpots.size(), t1-t0);
        return true;
    }
    
    public void logGraphStatus(String step, long processingTime) {
        logger.debug("number of edges after {}: {}, nb of vertices: {}, processing time: {}", step, graph.edgeSet().size(), graph.vertexSet().size(),processingTime);
    }
    
    private void transferLinks(Spot from, Spot to) {
        List<DefaultWeightedEdge> edgeList = new ArrayList<>(graph.edgesOf(from));
        for (DefaultWeightedEdge e : edgeList) {
            Spot target = graph.getEdgeTarget(e);
            boolean isSource = true;
            if (target==from) {
                target = graph.getEdgeSource(e);
                isSource=false;
            }
            graph.removeEdge(e);
            if (target!=to) graph.addEdge(isSource?to : target, isSource ? target : to, e);          
        }
        graph.removeVertex(from);
    }
    public void setTrackLinks(Map<Integer, List<StructureObject>> objectsF) {
        setTrackLinks(objectsF, null);
    }
    public Set<DefaultWeightedEdge> getEdges() {
        return graph.edgeSet();
    }
    public DefaultWeightedEdge getEdge(S s, S t) {
        if (s==null || t==null) return null;
        return graph.getEdge(s, t);
    }
    public Set<SymetricalPair<DefaultWeightedEdge>> getCrossingLinks(double spatialTolerence, Set<S> involvedSpots) {
        if (graph==null) return Collections.EMPTY_SET;
        Set<SymetricalPair<DefaultWeightedEdge>> res = new HashSet<>();
        for (DefaultWeightedEdge e1 : graph.edgeSet()) {
            for (DefaultWeightedEdge e2 : graph.edgeSet()) {
                if (e1.equals(e2)) continue;
                if (intersect(e1, e2, spatialTolerence, involvedSpots)) {
                    res.add(new SymetricalPair<>(e1, e2));
                }
            }
        }
        return res;
    }
    /**
     * Removes edges from graph, and spots that not linked to any other spots
     * @param edges
     * @param spots can be null 
     */
    public void removeFromGraph(Collection<DefaultWeightedEdge> edges, Collection<S> spots, boolean removeUnlinkedVextices) {
        if (spots==null) {
            spots = new HashSet<S>();
            for (DefaultWeightedEdge e : edges) {
                spots.add((S)graph.getEdgeSource(e));
                spots.add((S)graph.getEdgeTarget(e));
            }
        }
        //logger.debug("edges to remove :{}", Utils.toStringList(edges, e->graph.getEdgeSource(e)+"->"+graph.getEdgeTarget(e)));
        graph.removeAllEdges(edges);
        //logger.debug("spots to remove candidates :{}", spots);
        if (removeUnlinkedVextices) {
            for (Spot s : spots) { // also remove vertex that are not linked anymore
                if (graph.edgesOf(s).isEmpty()) removeObject(spotObjectMap.get((S)s), (int)(double)s.getFeature(Spot.FRAME));
            }
        }
    }
    public void addEdge(S s, S t) {
        graph.addEdge(s, t);
    }
    public void removeFromGraph(DefaultWeightedEdge edge) {
        Spot v1 = graph.getEdgeSource(edge);
        Spot v2 = graph.getEdgeTarget(edge);
        graph.removeEdge(edge);
        try {
            if (v1!=null && graph.edgesOf(v1).isEmpty()) graph.removeVertex(v1);
        } catch (Exception e) {
            logger.debug("vertex: {} not found. contained? {}", v1, graph.containsVertex(v1));
        }
        try {
            if (v2!=null && graph.edgesOf(v2).isEmpty()) graph.removeVertex(v2);
        } catch (Exception e) {
            logger.debug("vertex: {} not found. contained? {}", v2, graph.containsVertex(v2));
        }
        
    }
    public void  removeCrossingLinksFromGraph(double spatialTolerence) {
        if (graph==null) return;
        long t0 = System.currentTimeMillis();
        Set<S> toRemSpot = new HashSet<>();
        Set<SymetricalPair<DefaultWeightedEdge>> toRemove = getCrossingLinks(spatialTolerence, toRemSpot);
        removeFromGraph(Pair.flatten(toRemove, null), toRemSpot, false);
        long t1 = System.currentTimeMillis();
        logger.debug("number of edges after removing intersecting links: {}, nb of vertices: {}, processing time: {}", graph.edgeSet().size(), graph.vertexSet().size(), t1-t0);
    }

    private boolean intersect(DefaultWeightedEdge e1, DefaultWeightedEdge e2, double spatialTolerence, Set<S> toRemSpot) {
        if (e1.equals(e2)) return false;
        S s1 = (S)graph.getEdgeSource(e1);
        S s2 = (S)graph.getEdgeSource(e2);
        S t1 = (S)graph.getEdgeTarget(e1);
        S t2 = (S)graph.getEdgeTarget(e2);
        //if (s1.getFeature(Spot.FRAME)>=t1.getFeature(Spot.FRAME)) logger.debug("error source after target {}->{}", s1, t1);
        if (s1.equals(t1) || s2.equals(t2) || s1.equals(s2) || t1.equals(t2)) return false;
        if (!overlapTime(s1.getFeature(Spot.FRAME), t1.getFeature(Spot.FRAME), s2.getFeature(Spot.FRAME), t2.getFeature(Spot.FRAME))) return false;
        for (String f : Spot.POSITION_FEATURES) {
            if (!intersect(s1.getFeature(f), t1.getFeature(f), s2.getFeature(f), t2.getFeature(f), spatialTolerence)) return false;
        }
        if (toRemSpot!=null) {
            toRemSpot.add(s1);
            toRemSpot.add(s2);
            toRemSpot.add(t1);
            toRemSpot.add(t2);
        }
        return true;
    }
    private static boolean intersect(double aPrev, double aNext, double bPrev, double bNext, double tolerance) {
        double d1 = aPrev - bPrev;
        double d2 = aNext - bNext;
        return d1*d2<=0 || Math.abs(d1)<=tolerance || Math.abs(d2)<=tolerance;
    }
    private static boolean overlapTime(double aPrev, double aNext, double bPrev, double bNext) {
        /*if (aPrev>aNext) {
            double t = aNext;
            aNext=aPrev;
            aPrev=t;
        }
        if (bPrev>bNext) {
            double t = bNext;
            bNext=bPrev;
            bPrev=t;
        }*/
        double min = Math.max(aPrev, bPrev);
        double max = Math.min(aNext, bNext);
        return max>min;
    }
    public void printLinks() {
        logger.debug("number of objects: {}", graph.vertexSet().size());
        List<Spot> sList = new ArrayList<>(graph.vertexSet());
        Collections.sort(sList);
        for (Spot s : sList) logger.debug("{}", s);
        logger.debug("number of links: {}", graph.edgeSet().size());
        List<DefaultWeightedEdge> eList = new ArrayList<>(graph.edgeSet());
        Collections.sort(eList, (e1, e2)->{
            int c1 = graph.getEdgeSource(e1).compareTo(graph.getEdgeSource(e2));
            if (c1!=0) return c1;
            return graph.getEdgeTarget(e1).compareTo(graph.getEdgeTarget(e2));
        });
        for (DefaultWeightedEdge e : eList) {
            Spot s = graph.getEdgeSource(e);
            Spot t = graph.getEdgeTarget(e);
            logger.debug("{}->{} sourceEdges: {}, targetEdges: {}", s, t, graph.edgesOf(s), graph.edgesOf(t));
        }
    }
    public void resetTrackLinks(Map<Integer, List<StructureObject>> objectsF, Collection<StructureObject> modifiedObjects) {
        List<StructureObject> objects = Utils.flattenMap(objectsF);
        int minF = objectsF.keySet().stream().min((i1, i2)->Integer.compare(i1, i2)).get();
        int maxF = objectsF.keySet().stream().max((i1, i2)->Integer.compare(i1, i2)).get();
        logger.debug("reset track links between {} & {}", minF, maxF);
        for (StructureObject o : objects) o.resetTrackLinks(o.getFrame()>minF, o.getFrame()<maxF, false, modifiedObjects);
    }
    public void setTrackLinks(Map<Integer, List<StructureObject>> objectsF, Collection<StructureObject> modifiedObjects) {
        if (objectsF==null || objectsF.isEmpty()) return;
        List<StructureObject> objects = Utils.flattenMap(objectsF);
        int minF = objectsF.keySet().stream().min((i1, i2)->Integer.compare(i1, i2)).get();
        int maxF = objectsF.keySet().stream().max((i1, i2)->Integer.compare(i1, i2)).get();
        for (StructureObject o : objects) o.resetTrackLinks(o.getFrame()>minF, o.getFrame()<maxF, false, modifiedObjects);
        if (graph==null) {
            logger.error("Graph not initialized!");
            return;
        }
        
        TreeSet<DefaultWeightedEdge> edgeBucket = new TreeSet(new Comparator<DefaultWeightedEdge>() {
            @Override public int compare(DefaultWeightedEdge arg0, DefaultWeightedEdge arg1) {
                return Double.compare(graph.getEdgeWeight(arg0), graph.getEdgeWeight(arg1));
            }
        });
        
        setEdges(objects, objectsF, false, edgeBucket, modifiedObjects);
        setEdges(objects, objectsF, true, edgeBucket, modifiedObjects);
    }
    private void setEdges(List<StructureObject> objects, Map<Integer, List<StructureObject>> objectsByF, boolean prev, TreeSet<DefaultWeightedEdge> edgesBucket, Collection<StructureObject> modifiedObjects) {
        for (StructureObject child : objects) {
            edgesBucket.clear();
            //logger.debug("settings links for: {}", child);
            S s = objectSpotMap.get(child.getRegion());
            getSortedEdgesOf(s, prev, edgesBucket);
            //logger.debug("set {} edge for: {}: links: {}: {}", prev?"prev":"next", s, edgesBucket.size(), edgesBucket);
            if (edgesBucket.size()==1) {
                DefaultWeightedEdge e = edgesBucket.first();
                S otherSpot = getOtherSpot(e, s);
                StructureObject other = getStructureObject(objectsByF.get(otherSpot.getFeature(Spot.FRAME).intValue()), otherSpot);
                if (other!=null) {
                    if (prev) {
                        if (child.getPrevious()!=null && !child.getPrevious().equals(other)) {
                            logger.warn("warning: {} has already a previous assigned: {}, cannot assign: {}", child, child.getPrevious(), other);
                        } else StructureObjectUtils.setTrackLinks(other, child, true, false, modifiedObjects);
                    } else {
                        if (child.getNext()!=null && !child.getNext().equals(other)) {
                            logger.warn("warning: {} has already a next assigned: {}, cannot assign: {}", child, child.getNext(), other);
                        } else StructureObjectUtils.setTrackLinks(child, other, false, true, modifiedObjects);
                    }
                }
                //else logger.warn("SpotWrapper: next: {}, next of {}, has already a previous assigned: {}", nextSo, child, nextSo.getPrevious());
            }
        }
    }
    private void getSortedEdgesOf(S spot, boolean backward, TreeSet<DefaultWeightedEdge> res) {
        if (!graph.containsVertex(spot)) return;
        Set<DefaultWeightedEdge> set = graph.edgesOf(spot);
        if (set.isEmpty()) return;
        // remove backward or foreward links
        double tp = spot.getFeature(Spot.FRAME);
        if (backward) {
            for (DefaultWeightedEdge e : set) {
                if (getOtherSpot(e, spot).getFeature(Spot.FRAME)<tp) res.add(e);
            }
        } else {
            for (DefaultWeightedEdge e : set) {
                if (getOtherSpot(e, spot).getFeature(Spot.FRAME)>tp) res.add(e);
            }
        }
    }
    
    private S getOtherSpot(DefaultWeightedEdge e, S spot) {
        S s = (S)graph.getEdgeTarget(e);
        if (spot.equals(s)) return (S)graph.getEdgeSource(e);
        else return s;
    }
    public void switchLinks(DefaultWeightedEdge e1, DefaultWeightedEdge e2) {
        S s1 = (S)graph.getEdgeSource(e1);
        S t1 = (S)graph.getEdgeTarget(e1);
        S s2 = (S)graph.getEdgeSource(e2);
        S t2 = (S)graph.getEdgeTarget(e2);
        graph.removeEdge(e1);
        graph.removeEdge(e2);
        graph.addEdge(s1, t2);
        graph.addEdge(s2, t1);
    }
    /**
     * If allow merge / split -> unpredictible results : return one possible track
     * @param e
     * @param after
     * @param before
     * @return 
     */
    public List<S> getTrack(S e, boolean next, boolean prev) {
        if (graph==null) return null;
        List<S> track = new ArrayList<>();
        track.add(e);
        if (next) {
            S n = getNext(e);
            while(n!=null) {
                track.add(n);
                n = getNext(n);
            }
        } 
        if (prev) {
            S p = getPrevious(e);
            while(p!=null) {
                track.add(p);
                p = getPrevious(p);
            }
        } 
        Collections.sort(track, (s1, s2)->Double.compare(s1.getFeature(Spot.FRAME), s2.getFeature(Spot.FRAME)));
        return track;
    }
    public S getPrevious(S t) {       
        for (DefaultWeightedEdge e : graph.edgesOf(t)) {
            Spot s = graph.getEdgeSource(e);
            if (s.equals(t)) continue;
            else return (S)s;
        }
        return null;
    }
    public S getNext(S s) {
        for (DefaultWeightedEdge e : graph.edgesOf(s)) {
            Spot t = graph.getEdgeTarget(e);
            if (s.equals(t)) continue;
            else return (S)t;
        }
        return null;
    }
    public S getObject(DefaultWeightedEdge e, boolean source) {
        return source ? (S)graph.getEdgeSource(e) : (S)graph.getEdgeTarget(e);
    }
    
    private StructureObject getStructureObject(List<StructureObject> candidates, S s) {
        if (candidates==null || candidates.isEmpty()) return null;
        Region o = spotObjectMap.get(s);
        for (StructureObject c : candidates) if (c.getRegion() == o) return c;
        return null;
    }
    
    public static DefaultRegionSpotFactory defaultFactory() {
        return new DefaultRegionSpotFactory();
    }
    public interface SpotFactory<S extends Spot> {
        public S toSpot(Region o, int frame);
        public S duplicate(S s);
    }

    public static class DefaultRegionSpotFactory implements SpotFactory<Spot> {
        @Override
        public Spot toSpot(Region o, int frame) {
            Point center = o.getCenter();
            if (center==null) center = o.getGeomCenter(true);
            Spot s = new Spot(center.get(0), center.get(1), center.getWithDimCheck(2), 1, 1);
            s.getFeatures().put(Spot.FRAME, (double)frame);
            return s;
        }
        @Override public Spot duplicate(Spot s) {
            Spot res =  new Spot(s.getFeature(Spot.POSITION_X), s.getFeature(Spot.POSITION_Y), s.getFeature(Spot.POSITION_Z), s.getFeature(Spot.RADIUS), s.getFeature(Spot.QUALITY));
            res.getFeatures().put(Spot.FRAME, s.getFeature(Spot.FRAME));
            return res;
        }
    }
}
