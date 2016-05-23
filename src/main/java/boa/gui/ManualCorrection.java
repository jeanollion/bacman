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
package boa.gui;

import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageObjectInterfaceKey;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.objects.ObjectNode;
import boa.gui.objects.StructureNode;
import boa.gui.selection.SelectionUtils;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.TypeConverter;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import plugins.ManualSegmenter;
import plugins.ObjectSplitter;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ManualCorrection {
    public static void unlinkObject(StructureObject o, List<StructureObject> modifiedObjects) {
        if (o.getNext()!=null) o.getNext().setTrackHead(o.getNext(), true, true, modifiedObjects);
        o.resetTrackLinks();
        //logger.debug("unlinking: {}", o);
    }
    public static void unlinkObjects(StructureObject prev, StructureObject next, Collection<StructureObject> modifiedObjects) {
        if (next.getTimePoint()<prev.getTimePoint()) unlinkObjects(next, prev, modifiedObjects);
        else {
            next.setTrackHead(next, true, true, modifiedObjects);
            //logger.debug("unlinking.. previous: {}, previous's next: {}", sel.get(1).getPrevious(), sel.get(0).getNext());
            prev.setTrackFlag(StructureObject.TrackFlag.correctionSplit);
            next.setTrackFlag(StructureObject.TrackFlag.correctionSplit);
            if (modifiedObjects!=null) modifiedObjects.add(prev);
            //logger.debug("unlinking: {} to {}", sel.get(0), sel.get(1));
        }
        
    }
    public static void linkObjects(StructureObject prev, StructureObject next, Collection<StructureObject> modifiedObjects) {
        if (next.getTimePoint()<prev.getTimePoint()) linkObjects(next, prev, modifiedObjects);
        else {
            if (prev.getNext()==next && next.getPrevious()==prev)  {
                logger.warn("spots are already linked");
                return;
            } else if (prev.getNext()==next || next.getPrevious()==prev) {
                logger.warn("spots are already linked, s1: {}, th: {}, next: {}", prev, prev.getTrackHead(), prev.getNext());
                logger.warn("spots are already linked, s2: {}, th: {}, prev: {}", next, next.getTrackHead(), next.getPrevious());
                if (prev.getNext()==next && prev.getTrackHead()!=next.getTrackHead()) logger.error("Inconsitency: spots thrackHead differ: {} vs {}",  prev.getTrackHead(), next.getTrackHead());
                if (prev.getNext()==next && next.getPrevious()!=prev) {
                    logger.error("Data inconsitency: s2 is next of s1, but s1 is not prev of s2. Will be repaired");
                } else if (next.getPrevious()==prev && prev.getNext()!=next) {
                    logger.error("Data inconsitency: s2 is next of s1, but s1 is not prev of s2. {}", prev.getNext()==null?"will be repaired": "");
                    if (prev.getNext()!=null) return;
                }
            }
            boolean newTh = prev.getNext()!=null && prev.getNext()!=next;
            // unlinking each of the two spots
            if (next.getPrevious()!=null) unlinkObjects(next.getPrevious(), next, modifiedObjects);
            //if (prev.getNext()!=null) unlinkObjects(prev, prev.getNext(), modifiedTrackHeads);
            
            next.setPreviousInTrack(prev, newTh);
            if (!newTh) next.setTrackHead(prev.getTrackHead(), false, true, modifiedObjects);
            else next.setTrackHead(next, false, true, modifiedObjects);
            next.setTrackFlag(StructureObject.TrackFlag.correctionMerge);
            modifiedObjects.add(next);
            modifiedObjects.add(prev);
            //logger.debug("linking: {} to {}", prev, next);
        }
        
    }
    
    public static void modifyObjectLinks(MasterDAO db, List<StructureObject> objects, boolean unlink, boolean updateDisplay) {
        StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.size()<=1) return;
        
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(objects));
        
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        TreeMap<StructureObject, List<StructureObject>> objectsByParent = new TreeMap(StructureObjectUtils.splitByParent(objects)); // sorted by time point
        StructureObject prevParent = null;
        StructureObject prev = null;
        logger.debug("modify: unlink: {}, #objects: {}, #parents: {}", unlink, objects.size(), objectsByParent.keySet().size());
        for (StructureObject currentParent : objectsByParent.keySet()) {
            List<StructureObject> l = objectsByParent.get(currentParent);
            logger.debug("prevParent: {}, currentParent: {}, #objects: {}", prevParent, currentParent, l.size());
            if (l.size()==1 && (prevParent==null || prevParent.getTimePoint()<currentParent.getTimePoint())) {
                if (prev!=null) logger.debug("prev: {}, prevTh: {}, prevIsTh: {}, prevPrev: {}, prevNext: {}", prev, prev.getTrackHead(), prev.isTrackHead(), prev.getPrevious(), prev.getNext());
                if (prevParent!=null && prev!=null) {
                    StructureObject current = l.get(0);
                    logger.debug("current: {}, currentTh: {}, currentIsTh: {}, currentPrev: {}, currentNext: {}", current, current.getTrackHead(), current.isTrackHead(), current.getPrevious(), current.getNext());
                    if (unlink) {
                        if (current.getPrevious()==prev || prev.getNext()==current) { //unlink the 2 spots
                            ManualCorrection.unlinkObjects(prev, current, modifiedObjects);
                        } else { // reset links

                        }
                    } else ManualCorrection.linkObjects(prev, current, modifiedObjects);

                } //else if (unlink) ManualCorrection.unlinkObject(l.get(0), modifiedObjects);
                prevParent=currentParent;
                prev = l.get(0);
            } else {
                prev=null;
                prevParent=null;
                //if (unlink) for (StructureObject o : l) ManualCorrection.unlinkObject(o, modifiedObjects);
            }   
        }
        repairLinkInconsistencies(db, modifiedObjects, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getFieldName()).store(modifiedObjects, true);
        if (updateDisplay) {
            // reload track-tree and update selection list
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
            //List<List<StructureObject>> tracks = this.trackTreeController.getGeneratorS().get(structureIdx).getSelectedTracks(true);
            // get unique tracks to display
            Set<StructureObject> uniqueTh = new HashSet<StructureObject>();
            for (StructureObject o : modifiedObjects) uniqueTh.add(o.getTrackHead());
            List<List<StructureObject>> trackToDisp = new ArrayList<List<StructureObject>>();
            for (StructureObject o : uniqueTh) trackToDisp.add(StructureObjectUtils.getTrack(o, true));
            // update current image
            ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
            if (!trackToDisp.isEmpty()) iwm.displayTracks(null, null, trackToDisp, true);
        }
    }
    
    private static void repairLinkInconsistencies(MasterDAO db, Collection<StructureObject> objects, Collection<StructureObject> modifiedObjects) {
        Map<StructureObject, List<StructureObject>> objectsByParentTh = StructureObjectUtils.splitByParentTrackHead(objects);
        for (StructureObject parentTh : objectsByParentTh.keySet()) {
            Selection sel = null;
            String selName = "linkError_pIdx"+parentTh.getIdx()+"_Position"+parentTh.getFieldName();
            for (StructureObject o : objectsByParentTh.get(parentTh)) {
                if (o.getNext()!=null && o.getNext().getPrevious()!=o) {
                    if (o.getNext().getPrevious()==null) linkObjects(o, o.getNext(), modifiedObjects);
                    else {
                        if (sel ==null) sel = SelectionUtils.getSelection(db, selName, true);
                        sel.addElement(o);
                        sel.addElement(o.getNext());
                    }
                }
                if (o.getPrevious()!=null && o.getPrevious().getNext()!=o) {
                    if (o.getPrevious().getNext()==null) linkObjects(o.getPrevious(), o, modifiedObjects);
                    else {
                        if (sel ==null) sel = SelectionUtils.getSelection(db, selName, true);
                        sel.addElement(o);
                        sel.addElement(o.getPrevious());
                    }
                }
            }
            if (sel!=null) {
                sel.setIsDisplayingObjects(true);
                sel.setIsDisplayingTracks(true);
                db.getSelectionDAO().store(sel);
            }
        }
        GUI.getInstance().populateSelections();
    }
    
    
    public static void resetObjectLinks(MasterDAO db, List<StructureObject> objects, boolean updateDisplay) {
        StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.isEmpty()) return;
        
        if (updateDisplay) ImageWindowManagerFactory.getImageManager().removeTracks(StructureObjectUtils.getTrackHeads(objects));
        
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        for (StructureObject o : objects) ManualCorrection.unlinkObject(o, modifiedObjects);
        Utils.removeDuplicates(modifiedObjects, false);
        db.getDao(objects.get(0).getFieldName()).store(modifiedObjects, true);
        if (updateDisplay) {
            // reload track-tree and update selection list
            int parentStructureIdx = objects.get(0).getParent().getStructureIdx();
            GUI.getInstance().trackTreeController.updateParentTracks(GUI.getInstance().trackTreeController.getTreeIdx(parentStructureIdx));
            Set<StructureObject> uniqueTh = new HashSet<StructureObject>();
            for (StructureObject o : modifiedObjects) uniqueTh.add(o.getTrackHead());
            List<List<StructureObject>> trackToDisp = new ArrayList<List<StructureObject>>();
            for (StructureObject o : uniqueTh) trackToDisp.add(StructureObjectUtils.getTrack(o, true));
            // update current image
            ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
            if (!trackToDisp.isEmpty()) iwm.displayTracks(null, null, trackToDisp, true);
        }
    }
    
    public static void manualSegmentation(MasterDAO db, Image image, boolean test) {
        ImageWindowManager iwm = ImageWindowManagerFactory.getImageManager();
        if (image==null) {
            Object im = iwm.getDisplayer().getCurrentImage();
            if (im!=null) image = iwm.getDisplayer().getImage(im);
            if (image==null) {
                logger.warn("No image found");
                return;
            }
        }
        ImageObjectInterfaceKey key =  iwm.getImageObjectInterfaceKey(image);
        if (key==null) {
            logger.warn("Current image is not registered");
            return;
        }
        int structureIdx = key.childStructureIdx;
        int parentStructureIdx = db.getExperiment().getStructure(structureIdx).getParentStructure();
        ManualSegmenter segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
        
        if (segmenter==null) {
            logger.warn("No manual segmenter found for structure: {}", structureIdx);
            return;
        }
        
        Map<StructureObject, List<int[]>> points = iwm.getParentSelectedPointsMap(image, parentStructureIdx);
        if (points!=null) {
            logger.debug("manual segment: {} distinct parents. Segmentation structure: {}, parent structure: {}", points.size(), structureIdx, parentStructureIdx);
            List<StructureObject> segmentedObjects = new ArrayList<StructureObject>();
            for (Map.Entry<StructureObject, List<int[]>> e : points.entrySet()) {
                segmenter = db.getExperiment().getStructure(structureIdx).getManualSegmenter();
                segmenter.setManualSegmentationVerboseMode(test);
                
                Image segImage = e.getKey().getRawImage(structureIdx);
                
                // generate image mask without old objects
                ImageByte mask = TypeConverter.cast(e.getKey().getMask(), new ImageByte("Manual Segmentation Mask", 0, 0, 0));
                List<StructureObject> oldChildren = e.getKey().getChildren(structureIdx);
                for (StructureObject c : oldChildren) c.getObject().draw(mask, 0, new BoundingBox(0, 0, 0));
                if (test) iwm.getDisplayer().showImage(mask, 0, 1);
                // remove seeds out of mask
                Iterator<int[]> it=e.getValue().iterator();
                while(it.hasNext()) {
                    int[] seed = it.next();
                    if (!mask.insideMask(seed[0], seed[1], seed[2])) it.remove();
                }
                ObjectPopulation seg = segmenter.manualSegment(segImage, e.getKey(), mask, structureIdx, e.getValue());
                //seg.filter(new ObjectPopulation.Size().setMin(2)); // remove seeds
                logger.debug("{} children segmented in parent: {}", seg.getObjects().size(), e.getKey());
                if (!test && !seg.getObjects().isEmpty()) {
                    List<StructureObject> newChildren = e.getKey().setChildrenObjects(seg, structureIdx);
                    segmentedObjects.addAll(newChildren);
                    newChildren.addAll(oldChildren);
                    ArrayList<StructureObject> modified = new ArrayList<StructureObject>();
                    e.getKey().relabelChildren(structureIdx, modified);
                    modified.addAll(newChildren);
                    Utils.removeDuplicates(modified, false);
                    db.getDao(e.getKey().getFieldName()).store(modified, true);
                    
                    //Update tree
                    ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(e.getKey());
                    node.getParent().createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node.getParent());
                    
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(e.getKey(), structureIdx, false);
                }
            }
            // selected newly segmented objects on image
            ImageObjectInterface i = iwm.getImageObjectInterface(image);
            if (i!=null) iwm.displayObjects(image, i.pairWithOffset(segmentedObjects), Color.ORANGE, true, false);
        }
    }
    public static void splitObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay, boolean test) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        if (objects.isEmpty()) return;
        ObjectSplitter splitter = db.getExperiment().getStructure(structureIdx).getObjectSplitter();
        if (splitter==null) {
            logger.warn("No splitter configured");
            return;
        }
        Map<String, List<StructureObject>> objectsByFieldName = StructureObjectUtils.splitByFieldName(objects);
        for (String f : objectsByFieldName.keySet()) {
            ObjectDAO dao = db.getDao(f);
            List<StructureObject> objectsToStore = new ArrayList<StructureObject>();
            List<StructureObject> newObjects = new ArrayList<StructureObject>();
            for (StructureObject objectToSplit : objectsByFieldName.get(f)) {
                splitter = db.getExperiment().getStructure(structureIdx).getObjectSplitter();
                splitter.setSplitVerboseMode(test);
                StructureObject newObject = objectToSplit.split(splitter);
                if (newObject==null) logger.warn("Object could not be splitted!");
                else {
                    newObjects.add(newObject);
                    objectToSplit.getParent().relabelChildren(objectToSplit.getStructureIdx(), objectsToStore);
                    objectsToStore.add(newObject);
                    objectsToStore.add(objectToSplit);
                }
            }
            
            Utils.removeDuplicates(objectsToStore, false);
            if (!test) dao.store(objectsToStore, true);
            if (updateDisplay && !test) {
                // unselect
                ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
                
                Set<StructureObject> parents = StructureObjectUtils.getParents(newObjects);
                for (StructureObject p : parents) {
                    //Update tree
                    StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(p).getStructureNode(structureIdx);
                    node.createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node);
                    //Update all opened images & objectImageInteraction
                    ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                }
                // update selection
                ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
                if (i!=null) {
                    newObjects.addAll(objects);
                    ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
                }
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
    public static void mergeObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        int structureIdx = StructureObjectUtils.keepOnlyObjectsFromSameStructureIdx(objects);
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        if (objects.isEmpty()) return;
        ObjectDAO dao = db.getDao(fieldName);
        Map<StructureObject, List<StructureObject>> objectsByParent = StructureObjectUtils.splitByParent(objects);
        List<StructureObject> newObjects = new ArrayList<StructureObject>();
        for (StructureObject parent : objectsByParent.keySet()) {
            List<StructureObject> objectsToMerge = objectsByParent.get(parent);
            if (objectsToMerge.size()<=1) logger.warn("Merge Objects: select several objects from same parent!");
            else {
                StructureObject res = objectsToMerge.remove(0);
                List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
                for (StructureObject o : objectsToMerge) unlinkObject(o, modifiedObjects);
                for (StructureObject toMerge : objectsToMerge) {
                    res.merge(toMerge);
                    unlinkObject(toMerge, modifiedObjects);
                }
                newObjects.add(res);
                dao.delete(objectsToMerge, true, true, true);
                modifiedObjects.add(res);
                Utils.removeDuplicates(modifiedObjects, false);
                dao.store(modifiedObjects, true);
            }
        }
        if (updateDisplay) {
            ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
            ImageWindowManagerFactory.getImageManager().removeObjects(objects, true);
            for (StructureObject newObject: newObjects) {
                //Update object tree
                ObjectNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(newObject);
                node.getParent().createChildren();
                GUI.getInstance().objectTreeGenerator.reload(node.getParent());
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(newObjects);
            //Update all opened images & objectImageInteraction
            for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
            // update selection
            ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(null, structureIdx);
            if (i!=null) {
                ImageWindowManagerFactory.getImageManager().displayObjects(null, i.pairWithOffset(newObjects), Color.orange, true, false);
            }
            // update trackTree
            GUI.getInstance().trackTreeController.updateParentTracks();
        }
    }
    public static void deleteObjects(MasterDAO db, Collection<StructureObject> objects, boolean updateDisplay) {
        String fieldName = StructureObjectUtils.keepOnlyObjectsFromSameMicroscopyField(objects);
        ObjectDAO dao = db.getDao(fieldName);
        Map<Integer, List<StructureObject>> objectsByStructureIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int structureIdx : objectsByStructureIdx.keySet()) {
            List<StructureObject> list = objectsByStructureIdx.get(structureIdx);
            List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
            for (StructureObject o : list) {
                unlinkObject(o, modifiedObjects);
                o.getParent().getChildren(o.getStructureIdx()).remove(o);
            }
            Set<StructureObject> parents = StructureObjectUtils.getParents(list);
            logger.info("Deleting {} objects, from {} parents", list.size(), parents.size());
            dao.delete(list, true, true, true);
            Utils.removeDuplicates(modifiedObjects, false);
            dao.store(modifiedObjects, true);
            if (updateDisplay) {
                //Update selection on opened image
                ImageWindowManagerFactory.getImageManager().hideLabileObjects(null);
                ImageWindowManagerFactory.getImageManager().removeObjects(list, true);
                //Update object tree
                for (StructureObject s : parents) {
                    StructureNode node = GUI.getInstance().objectTreeGenerator.getObjectNode(s).getStructureNode(structureIdx);
                    node.createChildren();
                    GUI.getInstance().objectTreeGenerator.reload(node);
                }
                //Update all opened images & objectImageInteraction
                for (StructureObject p : parents) ImageWindowManagerFactory.getImageManager().reloadObjects(p, structureIdx, false);
                // update trackTree
                GUI.getInstance().trackTreeController.updateParentTracks();
            }
        }
    }
    
    public static void repairLinksForXP(MasterDAO db, int structureIdx) {
        for (String f : db.getExperiment().getFieldsAsString()) repairLinksForField(db, f, structureIdx);
    }
    public static void repairLinksForField(MasterDAO db, String fieldName, int structureIdx) {
        logger.debug("repairing field: {}", fieldName);
        int count = 0, countUncorr=0, count2=0, countUncorr2=0;
        ObjectDAO dao = db.getDao(fieldName);
        List<StructureObject> modifiedObjects = new ArrayList<StructureObject>();
        List<StructureObject> uncorrected = new ArrayList<StructureObject>();
        for (StructureObject root : dao.getRoots()) {
            for (StructureObject o : root.getChildren(structureIdx)) {
                if (o.getNext()!=null) {
                    if (o.getNext().getPrevious()!=o) {
                        logger.debug("inconsitency: o: {}, next: {}, next's previous: {}", o, o.getNext(), o.getNext().getPrevious());
                        if (o.getNext().getPrevious()==null) ManualCorrection.linkObjects(o, o.getNext(), modifiedObjects);
                        else {
                            uncorrected.add(o);
                            uncorrected.add(o.getNext());
                            countUncorr++;
                        }
                        ++count;
                    }
                    
                }
                if (o.getPrevious()!=null) {
                        if (o.getPrevious().getNext()!=o) {
                            if (o.getPrevious().getNext()==null) ManualCorrection.linkObjects(o.getPrevious(), o, modifiedObjects);
                            else {
                                uncorrected.add(o);
                                uncorrected.add(o.getPrevious());
                                countUncorr2++;
                            }
                            logger.debug("inconsitency: o: {}, previous: {}, previous's next: {}", o, o.getPrevious(), o.getPrevious().getNext());
                            ++count2;
                        }
                }
            }
        }
        logger.debug("total errors: type 1 : {}, uncorrected: {}, type 2: {}, uncorrected: {}", count, countUncorr, count2, countUncorr2);
        Map<StructureObject, List<StructureObject>> uncorrByParentTH = StructureObjectUtils.splitByParentTrackHead(uncorrected);
        
        // create selection of uncorrected
        for (StructureObject parentTh : uncorrByParentTH.keySet()) {
            String selectionName = "linkError_pIdx"+parentTh.getIdx()+"_Position"+fieldName;
            Selection sel = db.getSelectionDAO().getObject(fieldName);
            if (sel == null) sel = new Selection(selectionName, db);
            sel.addElements(uncorrByParentTH.get(parentTh));
            sel.setIsDisplayingObjects(true);
            sel.setIsDisplayingTracks(true);
            db.getSelectionDAO().store(sel);
        }
        Utils.removeDuplicates(modifiedObjects, false);
        dao.store(modifiedObjects, true);
    }
    
}
