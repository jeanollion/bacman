/*
 * Copyright (C) 2017 jollion
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
package dataStructure.objects;

import dataStructure.configuration.Experiment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.types.ObjectId;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class DBMapObjectDAO implements ObjectDAO {
    public static final Logger logger = LoggerFactory.getLogger(DBMapObjectDAO.class);
    final DBMapMasterDAO mDAO;
    final String positionName;
    //List<StructureObject> rootCache;
    final HashMapGetCreate<Pair<ObjectId, Integer>, Map<ObjectId, StructureObject>> cache = new HashMapGetCreate<>(new HashMapGetCreate.MapFactory()); // parent trackHead id -> id cache
    final HashMapGetCreate<Pair<ObjectId, Integer>, Boolean> allObjectsRetrievedInCache = new HashMapGetCreate<>(p -> false);
    final Map<Pair<ObjectId, Integer>, HTreeMap<String, String>> dbMaps = new HashMap<>();
    final String dir;
    final Map<Integer, DB> dbS = new HashMap<>();
    final Map<Integer, Pair<DB, HTreeMap<String, String>>> measurementdbS = new HashMap<>();
    public DBMapObjectDAO(DBMapMasterDAO mDAO, String positionName, String dir) {
        this.mDAO=mDAO;
        this.positionName=positionName;
        this.dir = dir+File.separator+positionName+File.separator+"segmented_objects"+File.separator;
        new File(this.dir).mkdirs();
    }
    
    @Override
    public MasterDAO getMasterDAO() {
        return mDAO;
    }

    @Override
    public Experiment getExperiment() {
        return mDAO.getExperiment();
    }

    @Override
    public String getPositionName() {
        return positionName;
    }

    
    private String getDBFile(int structureIdx) {
        String res = dir+"objects_"+structureIdx+".db";
        //logger.debug("db file: {}", res);
        return res;
    }
    
    protected DB getDB(int structureIdx) {
        DB res = this.dbS.get(structureIdx);
        if (res==null) {
            synchronized(dbS) {
                if (!dbS.containsKey(structureIdx)) {
                    res = DBMaker.fileDB(getDBFile(structureIdx)).transactionEnable().make(); // concurency ? close on shutdown?
                    dbS.put(structureIdx, res);
                } else {
                    res = dbS.get(structureIdx);
                }
            }
        }
        return res;
    }
    protected HTreeMap<String, String> getDBMap(Pair<ObjectId, Integer> key) {
        HTreeMap<String, String> res = this.dbMaps.get(key);
        if (res==null) {
            synchronized(dbMaps) {
                if (dbMaps.containsKey(key)) res=dbMaps.get(key);
                else {
                    DB db = getDB(key.value);
                    res = db.hashMap(key.key!=null? key.key.toHexString() : "root", Serializer.STRING, Serializer.STRING).createOrOpen(); // case of roots: no parent
                    dbMaps.put(key, res);
                }
            }
        }
        return res;
    }
    protected Map<ObjectId, StructureObject> getChildren(Pair<ObjectId, Integer> key) {
        if (cache.containsKey(key) && allObjectsRetrievedInCache.getAndCreateIfNecessary(key)) return cache.get(key);
        else {
            boolean cacheAlreadyPresent = cache.containsKey(key);
            Map<ObjectId, StructureObject> objectMap = cache.getAndCreateIfNecessary(key);
            HTreeMap<String, String> dbm = getDBMap(key);
            if (cacheAlreadyPresent) {
                Set<String> alreadyInCache = new HashSet<>(objectMap.size());
                for (ObjectId id : objectMap.keySet()) alreadyInCache.add(id.toHexString());
                for (Entry<String, String> e : dbm.getEntries()) {
                    if (!alreadyInCache.contains(e.getKey())) {
                        StructureObject o = this.mDAO.unmarshall(StructureObject.class, e.getValue());
                        o.dao=this;
                        objectMap.put(o.id, o);
                    }
                }
            } else {
                for (String s : dbm.getValues()) {
                    StructureObject o = this.mDAO.unmarshall(StructureObject.class, s);
                    o.dao=this;
                    objectMap.put(o.id, o);
                }
            }
            allObjectsRetrievedInCache.put(key, true);
            // set prev, next & trackHead
            for (StructureObject o : objectMap.values()) {
                if (o.nextId!=null) o.next=objectMap.get(o.nextId);
                if (o.previousId!=null) o.previous=objectMap.get(o.previousId);
                if (o.trackHeadId!=null) o.trackHead=objectMap.get(o.trackHeadId);
            }
            // set parents ? 
            if (key.value>=0) {
                int parentStructureIdx = mDAO.getExperiment().getStructure(key.value).getParentStructure();
                Map<ObjectId, StructureObject> parents = this.getCacheContaining(key.key, parentStructureIdx);
                if (parents!=null) {
                    for (StructureObject o : objectMap.values()) o.parent=parents.get(o.parentId);
                    Map<StructureObject, List<StructureObject>> byP = StructureObjectUtils.splitByParent(objectMap.values());
                    for (StructureObject p : byP.keySet()) {
                        List<StructureObject> children = byP.get(p);
                        Collections.sort(children);
                        p.setChildren(children, key.value);
                    }
                }
            }
            
            
            return objectMap;
        }
    }
    private void setParents(Collection<StructureObject> objects, Pair<ObjectId, Integer> parentKey) {
        Map<ObjectId, StructureObject> allParents = getChildren(parentKey);
        for (StructureObject o : objects) if (o.parent==null) o.parent = allParents.get(o.parentId);
    }
    @Override
    public StructureObject getById(ObjectId parentTrackHeadId, int structureIdx, int frame, ObjectId id) {
        // parentTrackHeadId can be null in case of parent call -> frame not null
        // frame can be < 
        if (parentTrackHeadId!=null || structureIdx==-1) return ((Map<ObjectId, StructureObject>)getChildren(new Pair(parentTrackHeadId, structureIdx))).get(id);
        else { // search in all parentTrackHeadId
            Map<ObjectId, StructureObject> cacheMap = getCacheContaining(id, structureIdx);
            if (cacheMap!=null) return cacheMap.get(id);
        }
        return null;
    }
    
    private Map<ObjectId, StructureObject> getCacheContaining(ObjectId id, int structureIdx) {
        if (structureIdx==-1) { //getExperiment().getStructure(structureIdx).getParentStructure()==-1
            Map<ObjectId, StructureObject> map = getChildren(new Pair(null, structureIdx));
            if (map.containsKey(id)) return map;
        } else {
            for (String parentTHId : getDB(structureIdx).getAllNames()) {
                Map<ObjectId, StructureObject> map = getChildren(new Pair(new ObjectId(parentTHId), structureIdx)); //"root".equals(parentTHId) ?  null : 
                if (map.containsKey(id)) return map;
            }
        }
        return null;
    }
    @Override
    public void setAllChildren(List<StructureObject> parentTrack, int childStructureIdx) {
        if (parentTrack.isEmpty()) return;
        Map<ObjectId, StructureObject> children = getChildren(new Pair(parentTrack.get(0).getTrackHeadId(), childStructureIdx));
        Map<StructureObject, List<StructureObject>> byParent = StructureObjectUtils.splitByParent(children.values());
        for (StructureObject parent : parentTrack) parent.setChildren(byParent.get(parent), childStructureIdx);
    }
    
    @Override
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        for (StructureObject o : ((Map<ObjectId, StructureObject>)getChildren(new Pair(parent.getTrackHeadId(), structureIdx))).values()) {
            if (o.parentId.equals(parent.getId())) {
                o.parent=parent;
                res.add(o);
            }
        }
        Collections.sort(res);
        return res;
    }
    /*public List<StructureObject> getChildren(Collection<StructureObject> parents, int structureIdx) {
        List<StructureObject> res = new ArrayList<>();
        Map<ObjectId, StructureObject> parentIds = toIdMap(parents);
        Map<StructureObject, List<StructureObject>> byParentTH = StructureObjectUtils.splitByParentTrackHead(parents);
        for (StructureObject pth : byParentTH.keySet()) {
            for (StructureObject o : getChildren(pth.getId(), structureIdx).values()) {
                if (parentIds.containsKey(o.parentId)) {
                    o.setParent(parentIds.get(o.parentId));
                    res.add(o);
                }
            }
        }
        return res;
    }*/

    @Override
    public void deleteChildren(StructureObject parent, int structureIdx) {
        List<StructureObject> children = DBMapObjectDAO.this.getChildren(parent, structureIdx);
        if (!children.isEmpty()) {
            Pair<ObjectId, Integer> key = new Pair(parent.getTrackHeadId(), structureIdx);
            Map<ObjectId, StructureObject> cacheMap = cache.get(key);
            HTreeMap<String, String> dbm = getDBMap(key);
            for (StructureObject o : children) {
                if (cacheMap!=null) cacheMap.remove(o.getId());
                dbm.remove(o.getId().toHexString());
            }
            getDB(structureIdx).commit();
        }
    }

    public static Set<ObjectId> toIds(Collection<StructureObject> objects) {
        Set<ObjectId> res= new HashSet<>(objects.size());
        for (StructureObject o : objects) res.add(o.getId());
        return res;
    }
    public static Map<ObjectId, StructureObject> toIdMap(Collection<StructureObject> objects) {
        Map<ObjectId, StructureObject> res= new HashMap<>(objects.size());
        for (StructureObject o : objects) res.put(o.getId(), o);
        return res;
    }
    
    @Override
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx) {
        Map<StructureObject, List<StructureObject>> byTh = StructureObjectUtils.splitByTrackHead(parents);
        for (StructureObject pth : byTh.keySet()) deleteChildren(byTh.get(pth), structureIdx, pth.getId(), false);
        getDB(structureIdx).commit();
    }
    protected void deleteChildren(Collection<StructureObject> parents, int structureIdx, ObjectId parentThreackHeadId, boolean commit) {
        Pair<ObjectId, Integer> key = new Pair(parentThreackHeadId, structureIdx);
        Set<ObjectId> parentIds = toIds(parents);
        Map<ObjectId, StructureObject> cacheMap = getChildren(key);
        HTreeMap<String, String> dbMap = getDBMap(key);
        Iterator<ObjectId> it = cacheMap.keySet().iterator();
        while(it.hasNext()) {
            ObjectId id = it.next();
            if (parentIds.contains(cacheMap.get(id).parentId)) {
                it.remove();
                dbMap.remove(id.toHexString());
            }
        }
        if (commit) getDB(structureIdx).commit();
    }
    

    @Override
    public synchronized void deleteObjectsByStructureIdx(int... structures) {
        for (int structureIdx : structures) {
            if (this.dbS.containsKey(structureIdx)) {
                dbS.remove(structureIdx).close();
                dbMaps.entrySet().removeIf(k -> k.getKey().value==structureIdx);
            }
            File f = new File(getDBFile(structureIdx));
            f.delete();
        }
        
    }

    @Override
    public void clearCache() {
        cache.clear();
        allObjectsRetrievedInCache.clear();
        closeAllFiles(true);
    }
    
    @Override
    public synchronized void deleteAllObjects() {
        closeAllObjectFiles(false);
        closeAllMeasurementFiles(false);
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) for (File subF : f.listFiles())  subF.delete();
    }
    private synchronized void closeAllObjectFiles(boolean commit) {
        for (DB db : dbS.values()) {
            if (commit) db.commit();
            db.close();
        }
        dbS.clear();
        dbMaps.clear();
        //cache.clear();
        //allObjectsRetrieved.clear();
    }
    private void closeAllFiles(boolean commit) {
        closeAllObjectFiles(commit);
        closeAllMeasurementFiles(commit);
    }
    public synchronized void compactDBs(boolean onlyOpened) {
        compactObjectDBs(onlyOpened);
        compactMeasurementDBs(onlyOpened);
    }
    public synchronized void compactObjectDBs(boolean onlyOpened) {
        if (onlyOpened) {
            for (DB db : this.dbS.values()) {
                db.commit();
                db.compact();
            }
        } else {
            for (int s = -1; s<mDAO.getExperiment().getStructureCount(); ++s) {
                if (dbS.keySet().contains(s)) {
                    dbS.get(s).commit();
                    dbS.get(s).compact();
                } else if (new File(getDBFile(s)).exists()) getDB(s).compact();
            }
        }
    }
    public synchronized void compactMeasurementDBs(boolean onlyOpened) {
        if (onlyOpened) {
            for (Pair<DB, ?> p : this.measurementdbS.values()) {
                p.key.commit();
                p.key.compact();
            }
        } else {
            for (int s = -1; s<mDAO.getExperiment().getStructureCount(); ++s) {
                if (measurementdbS.keySet().contains(s)) {
                    measurementdbS.get(s).key.commit();
                    measurementdbS.get(s).key.compact();
                } else if (new File(this.getMeasurementDBFile(s)).exists()) this.getMeasurementDB(s).key.compact();
            }
        }
    }
    @Override
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        Pair<ObjectId, Integer> key = new Pair(o.getParentTrackHeadId(), o.getStructureIdx());
        HTreeMap<String, String> dbMap = getDBMap(key);
        dbMap.remove(o.getId().toHexString());
        if (cache.containsKey(key)) cache.get(key).remove(o.getId());
        if (deleteChildren) {
            for (int s : o.getExperiment().getAllDirectChildStructures(o.getStructureIdx())) deleteChildren(o, s);
            //.. if track head is removed and has children -> inconsistency -> check at each delete, if delete children -> delete whole collection if trackHead, if not dont do anything
        }
        if (deleteFromParent) {
            if (o.getParent().getChildren(o.getStructureIdx()).remove(o) && relabelSiblings) {
                List<StructureObject> relabeled = new ArrayList<>(o.getParent().getChildren(o.getStructureIdx()).size());
                o.relabelChildren(o.getStructureIdx(), relabeled);
                store(relabeled, false, false);
            }   
        }
        getDB(o.getStructureIdx()).commit();
    }

    @Override
    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        Map<Pair<ObjectId, Integer>, List<StructureObject>> splitByPTH = splitByParentTrackHeadIdAndStructureIdx(list);
        Set<Integer> allModifiedStructureIdx = new HashSet<>(Pair.unpairValues(splitByPTH.keySet()));
        for (Pair<ObjectId, Integer> key : splitByPTH.keySet()) {
            List<StructureObject> toRemove = splitByPTH.get(key);
            HTreeMap<String, String> dbMap = getDBMap(key);
            for (StructureObject o : toRemove) dbMap.remove(o.getId().toHexString());
            if (cache.containsKey(key)) {
                Map<ObjectId, StructureObject> cacheMap = cache.get(key);
                for (StructureObject o : toRemove) cacheMap.remove(o.getId());
            }
            if (deleteChildren) {
                for (int sChild : getExperiment().getAllDirectChildStructures(key.value)) {
                    allModifiedStructureIdx.add(sChild);
                    deleteChildren(toRemove, sChild, key.key, false);
                }
            }
            //TODO if track head is removed and has children -> inconsistency -> check at each delete, if delete children -> delete whole collection if trackHead, if not dont do anything
            if (deleteFromParent && relabelSiblings) {
                if (key.value==-1) continue; // no parents
                Set<StructureObject> parents = new HashSet<StructureObject>();
                for (StructureObject o : toRemove) {
                    if (o.getParent().getChildren(key.value).remove(o)) {
                        parents.add(o.getParent());
                    }
                }
                //logger.debug("number of parents with delete object from structure: {} = {}", sIdx, parents.size());
                List<StructureObject> relabeled = new ArrayList<StructureObject>();
                for (StructureObject p : parents) {
                    p.relabelChildren(key.value, relabeled);
                }
                Utils.removeDuplicates(relabeled, false);
                store(relabeled, false, false);
            } else if (deleteFromParent) {
                for (StructureObject o : toRemove) {
                    if (o.getParent()!=null) o.getParent().getChildren(o.getStructureIdx()).remove(o);
                }
            }
        }
        for (int i : allModifiedStructureIdx) getDB(i).commit();
    }
    
    
    @Override
    public void store(StructureObject object, boolean updateTrackAttributes) {
        Pair<ObjectId, Integer> key = new Pair(object.getParentTrackHeadId(), object.getStructureIdx());
        if (object.hasMeasurementModifications()) upsertMeasurement(object);
        object.updateObjectContainer();
        if (updateTrackAttributes) {
            object.getParentTrackHeadId();
            object.getTrackHeadId();
            object.getPrevious();
            object.getNext();
        }
        cache.getAndCreateIfNecessary(key).put(object.getId(), object);
        getDBMap(key).put(object.getId().toHexString(), mDAO.marshall(object));
        getDB(object.getStructureIdx()).commit();
    }
    protected void store(Collection<StructureObject> objects, boolean updateTrackAttributes, boolean commit) {
        logger.debug("storing: {} commit: {}", objects.size(), commit);
        List<StructureObject> upserMeas = new ArrayList<>(objects.size());
        Map<Pair<ObjectId, Integer>, List<StructureObject>> splitByPTH = splitByParentTrackHeadIdAndStructureIdx(objects);
        logger.debug("storing: {} under #keys: {} commit: {}", objects.size(), splitByPTH.size(), commit);
        for (Pair<ObjectId, Integer> key : splitByPTH.keySet()) {
            List<StructureObject> toStore = splitByPTH.get(key);
            logger.debug("storing: {} objects under key: {}", toStore.size(), key.toString());
            Map<ObjectId, StructureObject> cacheMap = cache.getAndCreateIfNecessary(key);
            HTreeMap<String, String> dbMap = getDBMap(key);
            for (StructureObject object : toStore) {
                object.updateObjectContainer();
                if (updateTrackAttributes) {
                    object.getParentTrackHeadId();
                    object.getTrackHeadId();
                    object.getPrevious();
                    object.getNext();
                }
                if (object.hasMeasurementModifications()) upserMeas.add(object);
                cacheMap.put(object.getId(), object);
                dbMap.put(object.getId().toHexString(), mDAO.marshall(object));
            }
            if (commit) this.getDB(key.value).commit();            
        }
        upsertMeasurements(upserMeas);
    }
    @Override
    public void store(Collection<StructureObject> objects, boolean updateTrackAttributes) {
        store(objects, updateTrackAttributes, true);
    }

    @Override
    public List<StructureObject> getRoots() {
        // todo: root cache list to avoid sorting each time getRoot is called?
        List<StructureObject> res =  new ArrayList<>(getChildren(new Pair(null, -1)).values());
        Collections.sort(res, (o1, o2) -> Integer.compare(o1.getFrame(), o2.getFrame()));
        return res;
    }

    @Override
    public void setRoos(List<StructureObject> roots) {
        this.store(roots, true);
    }

    @Override
    public StructureObject getRoot(int timePoint) {
        List<StructureObject> roots = getRoots();
        if (roots.size()<=timePoint) return null;
        return roots.get(timePoint);
    }

    @Override
    public List<StructureObject> getTrack(StructureObject trackHead) {
        Map<ObjectId, StructureObject> allObjects = getChildren(new Pair(trackHead.getParentTrackHeadId(), trackHead.getStructureIdx()));
        List<StructureObject> list = new ArrayList<>();
        for (StructureObject o : allObjects.values()) if (o.getTrackHeadId().equals(trackHead.id)) list.add(o);
        Collections.sort(list, (o1, o2)-> Integer.compare(o1.getFrame(), o2.getFrame()));
        // TODO: parents may no be set !
        return list;
        /*
        StructureObject o = trackHead;
        while(o!=null) {
            if (o.getTrackHead()!=trackHead) break;
            list.add(o);
            o = o.getNext();
        }
        return list;
                */
    }

    @Override
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        Map<ObjectId, StructureObject> allObjects = getChildren(new Pair(parentTrack.id, structureIdx));
        List<StructureObject> list = new ArrayList<>();
        for (StructureObject o : allObjects.values()) if (o.isTrackHead) list.add(o);
        Collections.sort(list); //, (o1, o2)-> Integer.compare(o1.getFrame(), o2.getFrame())
        setParents(list, new Pair(parentTrack.getParentTrackHeadId(), parentTrack.getStructureIdx()));
        return list;
    }

    // measurements
    // store by structureIdx in another folder. Id = same as objectId
    private String getMeasurementDBFile(int structureIdx) {
        return dir+"measurements_"+structureIdx+".db";
    }
    protected Pair<DB, HTreeMap<String, String>> getMeasurementDB(int structureIdx) {
        Pair<DB, HTreeMap<String, String>> res = this.measurementdbS.get(structureIdx);
        if (res==null) {
            synchronized(measurementdbS) {
                if (!dbS.containsKey(structureIdx)) {
                    DB db = DBMaker.fileDB(getMeasurementDBFile(structureIdx)).transactionEnable().make(); // concurency ? close on shutdown?
                    HTreeMap<String, String> dbMap = db.hashMap("measurements", Serializer.STRING, Serializer.STRING).createOrOpen();
                    res = new Pair(db, dbMap);
                    measurementdbS.put(structureIdx, res);
                } else {
                    res = measurementdbS.get(structureIdx);
                }
            }
        }
        return res;
    }
    
    @Override
    public void upsertMeasurements(Collection<StructureObject> objects) {
        Map<Integer, List<StructureObject>> bySIdx = StructureObjectUtils.splitByStructureIdx(objects);
        for (int i : bySIdx.keySet()) {
            Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(i);
            for (StructureObject o : bySIdx.get(i)) {
                o.getMeasurements().updateObjectProperties(o);
                mDB.value.put(o.getId().toHexString(), mDAO.marshall(o.getMeasurements()));
                o.getMeasurements().modifications=false;
            }
            mDB.key.commit();
        }
    }

    @Override
    public void upsertMeasurement(StructureObject o) {
        o.getMeasurements().updateObjectProperties(o);
        Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(o.getStructureIdx());
        mDB.value.put(o.getId().toHexString(), mDAO.marshall(o.getMeasurements()));
        mDB.key.commit();
        o.getMeasurements().modifications=false;
    }

    @Override
    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        Pair<DB, HTreeMap<String, String>> mDB = getMeasurementDB(structureIdx);
        List<Measurements> res = new ArrayList<>();
        for (String s : mDB.value.getValues()) {
            Measurements m = this.mDAO.unmarshall(Measurements.class, s);
            res.add(m);
        }
        return res;
    }

    @Override
    public void deleteAllMeasurements() {
        closeAllMeasurementFiles(false);
        for (int s = 0; s<getExperiment().getStructureCount(); ++s) {
            String d = this.getMeasurementDBFile(s);
            File f = new File(d);
            f.delete();
        }
    }
    
    private synchronized void closeAllMeasurementFiles(boolean commit) {
        for (Pair<DB, HTreeMap<String, String>> p : this.measurementdbS.values()) {
            if (commit) p.key.commit();
            p.key.close();
        }
        measurementdbS.clear();
    }
    
    public static Map<Pair<ObjectId, Integer>, List<StructureObject>> splitByParentTrackHeadIdAndStructureIdx(Collection<StructureObject> list) {
        if (list.isEmpty()) return Collections.EMPTY_MAP;
        return list.stream().collect(Collectors.groupingBy(o -> new Pair(o.isRoot()? null : o.getParent().getTrackHeadId(), o.getStructureIdx())));
    }
}