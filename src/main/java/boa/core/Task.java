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
package boa.core;

import boa.configuration.experiment.Experiment;
import boa.ui.Console;
import boa.ui.logger.ExperimentSearchUtils;
import static boa.ui.logger.ExperimentSearchUtils.searchForLocalDir;
import static boa.ui.logger.ExperimentSearchUtils.searchLocalDirForDB;
import boa.ui.GUI;
import boa.ui.logger.FileProgressLogger;
import boa.ui.logger.MultiProgressLogger;
import boa.ui.PropertyUtils;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import static boa.core.TaskRunner.logger;
import boa.configuration.experiment.PreProcessingChain;
import boa.core.Processor.MEASUREMENT_MODE;
import static boa.core.Processor.deleteObjects;
import static boa.core.Processor.executeProcessingScheme;
import static boa.core.Processor.getOrCreateRootTrack;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.DBMapMasterDAO;
import boa.data_structure.dao.DBMapObjectDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import ij.IJ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import boa.measurement.MeasurementKeyObject;
import boa.measurement.MeasurementExtractor;
import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import boa.utils.ArrayUtil;
import boa.utils.FileIO;
import boa.utils.FileIO.ZipWriter;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;
import boa.utils.MultipleException;
import boa.utils.Pair;
import boa.utils.Utils;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import boa.ui.logger.ProgressLogger;

/**
 *
 * @author Jean Ollion
 */
public class Task extends SwingWorker<Integer, String> implements ProgressCallback {
        String dbName, dir;
        boolean preProcess, segmentAndTrack, trackOnly, measurements, generateTrackImages, exportPreProcessedImages, exportTrackImages, exportObjects, exportSelections, exportConfig;
        MEASUREMENT_MODE measurementMode = MEASUREMENT_MODE.ERASE_ALL;
        boolean exportData;
        List<Integer> positions;
        int[] structures;
        List<Pair<String, int[]>> extractMeasurementDir = new ArrayList<>();
        MultipleException errors = new MultipleException();
        MasterDAO db;
        final boolean keepDB;
        int[] taskCounter;
        ProgressLogger ui;
        public JSONObject toJSON() {
            JSONObject res=  new JSONObject();
            res.put("dbName", dbName); 
            if (this.dir!=null) res.put("dir", dir); // put dbPath ?
            if (preProcess) res.put("preProcess", preProcess);
            if (segmentAndTrack) res.put("segmentAndTrack", segmentAndTrack);
            if (trackOnly) res.put("trackOnly", trackOnly);
            if (measurements) {
                res.put("measurements", measurements);
                res.put("measurementMode", measurementMode.toString());
            }
            if (generateTrackImages) res.put("generateTrackImages", generateTrackImages);
            if (exportPreProcessedImages) res.put("exportPreProcessedImages", exportPreProcessedImages);
            if (exportTrackImages) res.put("exportTrackImages", exportTrackImages);
            if (exportObjects) res.put("exportObjects", exportObjects);
            if (exportSelections) res.put("exportSelections", exportSelections);
            if (exportConfig) res.put("exportConfig", exportConfig);
            if (positions!=null) res.put("positions", JSONUtils.toJSONArray(positions));
            if (structures!=null) res.put("structures", JSONUtils.toJSONArray(structures));
            JSONArray ex = new JSONArray();
            for (Pair<String, int[]> p : extractMeasurementDir) {
                JSONObject o = new JSONObject();
                o.put("dir", p.key);
                o.put("s", JSONUtils.toJSONArray(p.value));
                ex.add(o);
            }
            if (!ex.isEmpty()) res.put("extractMeasurementDir", ex);
            return res;
        }
        public Task duplicate() {
            return new Task().fromJSON(toJSON());
        }
        public Task fromJSON(JSONObject data) {
            if (data==null) return null;
            this.dbName = (String)data.getOrDefault("dbName", "");
            if (data.containsKey("dir")) {
                dir = (String)data.get("dir");
                if (!new File(dir).exists()) dir=null;
            }
            if (dir==null) dir = searchForLocalDir(dbName);
            this.preProcess = (Boolean)data.getOrDefault("preProcess", false);
            this.segmentAndTrack = (Boolean)data.getOrDefault("segmentAndTrack", false);
            this.trackOnly = (Boolean)data.getOrDefault("trackOnly", false);
            this.measurements = (Boolean)data.getOrDefault("measurements", false);
            this.measurementMode = MEASUREMENT_MODE.valueOf((String)data.getOrDefault("measurementMode", MEASUREMENT_MODE.ERASE_ALL.toString()));
            this.generateTrackImages = (Boolean)data.getOrDefault("generateTrackImages", false);
            this.exportPreProcessedImages = (Boolean)data.getOrDefault("exportPreProcessedImages", false);
            this.exportTrackImages = (Boolean)data.getOrDefault("exportTrackImages", false);
            this.exportObjects = (Boolean)data.getOrDefault("exportObjects", false);
            this.exportSelections = (Boolean)data.getOrDefault("exportSelections", false);
            this.exportConfig = (Boolean)data.getOrDefault("exportConfig", false);
            if (exportPreProcessedImages || exportTrackImages || exportObjects || exportSelections || exportConfig) exportData= true;
            if (data.containsKey("positions")) positions = JSONUtils.fromIntArrayToList((JSONArray)data.get("positions"));
            if (data.containsKey("structures")) structures = JSONUtils.fromIntArray((JSONArray)data.get("structures"));
            if (data.containsKey("extractMeasurementDir")) {
                extractMeasurementDir = new ArrayList<>();
                JSONArray ex = (JSONArray)data.get("extractMeasurementDir");
                for (Object o : ex) {
                    JSONObject jo = (JSONObject)(o);
                    extractMeasurementDir.add(new Pair((String)jo.get("dir"), JSONUtils.fromIntArray((JSONArray)jo.get("s"))));
                }
            }
            return this;
        }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.dbName);
        hash = 59 * hash + Objects.hashCode(this.dir);
        hash = 59 * hash + (this.preProcess ? 1 : 0);
        hash = 59 * hash + (this.segmentAndTrack ? 1 : 0);
        hash = 59 * hash + (this.trackOnly ? 1 : 0);
        hash = 59 * hash + (this.measurements ? 1 : 0);
        hash = 59 * hash + (this.generateTrackImages ? 1 : 0);
        hash = 59 * hash + (this.exportPreProcessedImages ? 1 : 0);
        hash = 59 * hash + (this.exportTrackImages ? 1 : 0);
        hash = 59 * hash + (this.exportObjects ? 1 : 0);
        hash = 59 * hash + (this.exportSelections ? 1 : 0);
        hash = 59 * hash + (this.exportConfig ? 1 : 0);
        hash = 59 * hash + (this.exportData ? 1 : 0);
        hash = 59 * hash + Objects.hashCode(this.positions);
        hash = 59 * hash + Arrays.hashCode(this.structures);
        hash = 59 * hash + Objects.hashCode(this.extractMeasurementDir);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Task other = (Task) obj;
        if (this.preProcess != other.preProcess) {
            return false;
        }
        if (this.segmentAndTrack != other.segmentAndTrack) {
            return false;
        }
        if (this.trackOnly != other.trackOnly) {
            return false;
        }
        if (this.measurements != other.measurements) {
            return false;
        }
        if (this.generateTrackImages != other.generateTrackImages) {
            return false;
        }
        if (this.exportPreProcessedImages != other.exportPreProcessedImages) {
            return false;
        }
        if (this.exportTrackImages != other.exportTrackImages) {
            return false;
        }
        if (this.exportObjects != other.exportObjects) {
            return false;
        }
        if (this.exportSelections != other.exportSelections) {
            return false;
        }
        if (this.exportConfig != other.exportConfig) {
            return false;
        }
        if (this.exportData != other.exportData) {
            return false;
        }
        if (!Objects.equals(this.dbName, other.dbName)) {
            return false;
        }
        if (!Objects.equals(this.dir, other.dir)) {
            return false;
        }
        if (!Objects.equals(this.positions, other.positions)) {
            return false;
        }
        if (!Arrays.equals(this.structures, other.structures)) {
            return false;
        }
        if (!Objects.equals(this.extractMeasurementDir, other.extractMeasurementDir)) {
            return false;
        }
        return true;
    }
        
        public Task setUI(ProgressLogger ui) {
            if (ui==null) this.ui=null;
            else {
                if (ui.equals(this.ui)) return this;
                this.ui=ui;
                addPropertyChangeListener((PropertyChangeEvent evt) -> {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress1 = (Integer) evt.getNewValue();
                        ui.setProgress(progress1);
                        //if (IJ.getInstance()!=null) IJ.getInstance().getProgressBar().show(progress, 100);
                        //logger.ingo("progress: {}%", i);
                        //gui.setProgress((Integer) evt.getNewValue());
                    }
                });
            }
            return this;
        }
        public Task() {
            if (GUI.hasInstance()) setUI(GUI.getInstance());
            keepDB = false;
        }
        public Task(MasterDAO db) {
            if (GUI.hasInstance()) setUI(GUI.getInstance());
            this.db=db;
            this.dbName=db.getDBName();
            this.dir=db.getDir();
            keepDB = true;
        }
        public Task(String dbName) {
            this(dbName, null);
        }
        public Task(String dbName, String dir) {
            this();
            this.dbName=dbName;
            if (dir!=null && !"".equals(dir)) this.dir=dir;
            else this.dir = searchForLocalDir(dbName);
        }
        public Task setDBName(String dbName) {
            if (dbName!=null && dbName.equals(this.dbName)) return this;
            this.db=null;
            this.dbName=dbName;
            return this;
        }
        public Task setDir(String dir) {
            if (dir!=null && dir.equals(this.dir)) return this;
            this.db=null;
            this.dir=dir;
            return this;
        }
        
        public List<Pair<String, Throwable>> getErrors() {return errors.getExceptions();}
        public MasterDAO getDB() {
            initDB();
            return db;
        }
        public String getDir() {
            return dir;
        }
        public Task setAllActions() {
            this.preProcess=true;
            this.segmentAndTrack=true;
            this.measurements=true;
            this.trackOnly=false;
            return this;
        }
        public Task setActions(boolean preProcess, boolean segment, boolean track, boolean measurements) {
            this.preProcess=preProcess;
            this.segmentAndTrack=segment;
            if (segmentAndTrack) trackOnly = false;
            else trackOnly = track;
            this.measurements=measurements;
            return this;
        }
        public Task setMeasurementMode(MEASUREMENT_MODE mode) {
            this.measurementMode=mode;
            return this;
        }
        public boolean isPreProcess() {
            return preProcess;
        }

        public boolean isSegmentAndTrack() {
            return segmentAndTrack;
        }

        public boolean isTrackOnly() {
            return trackOnly;
        }

        public boolean isMeasurements() {
            return measurements;
        }

        public boolean isGenerateTrackImages() {
            return generateTrackImages;
        }
        
        public Task setGenerateTrackImages(boolean generateTrackImages) {
            this.generateTrackImages=generateTrackImages;
            return this;
        }
        
        public Task setExportData(boolean preProcessedImages, boolean trackImages, boolean objects, boolean config, boolean selections) {
            this.exportPreProcessedImages=preProcessedImages;
            this.exportTrackImages=trackImages;
            this.exportObjects=objects;
            this.exportConfig=config;
            this.exportSelections=selections;
            if (preProcessedImages || trackImages || objects || config || selections) exportData= true;
            return this;
        }
        
        public Task setPositions(int... positions) {
            if (positions!=null && positions.length>0) this.positions=Utils.toList(positions);
            return this;
        }
        public Task unsetPositions(int... positions) {
            initDB();
            if (this.positions==null) this.positions=Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            for (int p : positions) this.positions.remove((Integer)p);
            logger.debug("positions: {} ({})", this.positions, Utils.transform(this.positions, i->db.getExperiment().getPositionsAsString()[i]));
            return this;
        }
        private void initDB() {
            if (db==null) {
                if (dir==null) throw new RuntimeException("XP not found");
                if (!"localhost".equals(dir) && new File(dir).exists()) db = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.DBMap);
                //else db = MasterDAOFactory.createDAO(dbName, dir, MasterDAOFactory.DAOType.Morphium);
            }
        }
        public Task setPositions(String... positions) {
            if (positions!=null && positions.length>0) {
                boolean initDB = db==null;
                if (initDB) initDB();
                this.positions=new ArrayList<>(positions.length);
                for (int i = 0; i<positions.length; ++i) this.positions.add(db.getExperiment().getPositionIdx(positions[i]));
                if (initDB) db=null; // only set to null if no db was set before, to be able to run on GUI db without lock issues
            }
            return this;
        }
        
        public Task setStructures(int... structures) {
            this.structures=structures;
            Arrays.sort(structures);
            return this;
        }
        
        public Task addExtractMeasurementDir(String dir, int... extractStructures) {
            if (extractStructures!=null && extractStructures.length==0) extractStructures = null;
            this.extractMeasurementDir.add(new Pair(dir, extractStructures));
            return this;
        }
        private void ensurePositionAndStructures(boolean positions, boolean structures) {
            if ((!positions || this.positions!=null) && (!structures || this.structures!=null)) return;
            initDB();
            if (positions && this.positions==null) this.positions = Utils.toList(ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount()));
            if (structures && this.structures==null) this.structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
        }

        public boolean isValid() {
            initDB(); // read only be default
            
            if (db.getExperiment()==null) {
                errors.addExceptions(new Pair(dbName, new Exception("DB: "+ dbName+ " not found")));
                printErrors();
                if (!keepDB) db = null;
                return false;
            } 
            if (structures!=null) checkArray(structures, db.getExperiment().getStructureCount(), "Invalid structure: ");
            if (positions!=null) checkArray(positions, db.getExperiment().getPositionCount(), "Invalid position: ");
            if (preProcess) { // compare pre processing to template
                ensurePositionAndStructures(true, false);
                PreProcessingChain template = db.getExperiment().getPreProcessingTemplate();
                for (int p : positions) {
                    PreProcessingChain pr = db.getExperiment().getPosition(p).getPreProcessingChain();
                    if (!template.getTransformations().sameContent(pr.getTransformations())) publish("Warning: Position: "+db.getExperiment().getPosition(p).getName()+": pre-processing pipeline differs from template");
                }
            }
            // check files
            for (Pair<String, int[]> e : extractMeasurementDir) {
                String exDir = e.key==null? db.getDir() : e.key;
                File f= new File(exDir);
                if (!f.exists()) errors.addExceptions(new Pair(dbName, new Exception("File: "+ exDir+ " not found")));
                else if (!f.isDirectory()) errors.addExceptions(new Pair(dbName, new Exception("File: "+ exDir+ " is not a directory")));
                else if (e.value!=null) checkArray(e.value, db.getExperiment().getStructureCount(), "Extract structure for dir: "+e.value+": Invalid structure: ");
            }
            if (!measurements && !preProcess && !segmentAndTrack && ! trackOnly && extractMeasurementDir.isEmpty() &&!generateTrackImages && !exportData) errors.addExceptions(new Pair(dbName, new Exception("No action to run!")));
            // check parametrization
            if (preProcess) {
                ensurePositionAndStructures(true, false);
                for (int p : positions) if (!db.getExperiment().getPosition(p).isValid()) errors.addExceptions(new Pair(dbName, new Exception("Configuration error @ Position: "+ db.getExperiment().getPosition(p).getName())));
            }
            if (segmentAndTrack || trackOnly) {
                ensurePositionAndStructures(false, true);
                for (int s : structures) if (!db.getExperiment().getStructure(s).isValid()) errors.addExceptions(new Pair(dbName, new Exception("Configuration error @ Structure: "+ db.getExperiment().getStructure(s).getName())));
            }
            if (measurements) {
                if (!db.getExperiment().getMeasurements().isValid()) errors.addExceptions(new Pair(dbName, new Exception("Configuration error @ Meausements: ")));
            }
            for (Pair<String, Throwable> e : errors.getExceptions()) publish("Invalid Task Error @"+e.key+" "+(e.value==null?"null":e.value.getLocalizedMessage()));
            logger.info("task : {}, isValid: {}", dbName, errors.isEmpty());
            if (!keepDB) {
                db.clearCache();
                db=null;
            }
            return errors.isEmpty();
        }
        private void checkArray(int[] array, int maxValue, String message) {
            if (array[ArrayUtil.max(array)]>=maxValue) errors.addExceptions(new Pair(dbName, new Exception(message + array[ArrayUtil.max(array)]+ " not found, max value: "+maxValue)));
            if (array[ArrayUtil.min(array)]<0) errors.addExceptions(new Pair(dbName, new Exception(message + array[ArrayUtil.min(array)]+ " not found")));
        }
        private void checkArray(List<Integer> array, int maxValue, String message) {
            if (Collections.max(array)>=maxValue) errors.addExceptions(new Pair(dbName, new Exception(message + Collections.max(array)+ " not found, max value: "+maxValue)));
            if (Collections.min(array)<0) errors.addExceptions(new Pair(dbName, new Exception(message + Collections.min(array)+ " not found")));
        }
        public void printErrors() {
            if (!errors.isEmpty()) logger.error("Errors for Task: {}", toString());
            for (Pair<String, ? extends Throwable> e : errors.getExceptions()) logger.error(e.key, e.value);
        }
        public int countSubtasks() {
            initDB();
            ensurePositionAndStructures(true, true);
            int count=0;
            // preProcess: 
            if (preProcess) count += positions.size();
            if (this.segmentAndTrack || this.trackOnly) count += positions.size() * structures.length;
            if (this.measurements) count += positions.size();
            if (this.generateTrackImages) {
                int gen = 0;
                for (int s : structures)  if (!db.getExperiment().getAllDirectChildStructures(s).isEmpty()) ++gen;
                count+=positions.size()*gen;
            }
            count+=extractMeasurementDir.size();
            if (this.exportObjects || this.exportPreProcessedImages || this.exportTrackImages) count+=positions.size();
            return count;
        }
        public void setSubtaskNumber(int[] taskCounter) {
            this.taskCounter=taskCounter;
        }
        public void runTask() {
            if (ui!=null) ui.setRunning(true);
            publish("Run task: "+this.toString());
            initDB();
            ImageWindowManagerFactory.getImageManager().flush();
            publishMemoryUsage("Before processing");
            this.ensurePositionAndStructures(true, true);
            Function<Integer, String> posIdxNameMapper = pIdx -> db.getExperiment().getPosition(pIdx).getName();
            String[] pos = positions.stream().map(posIdxNameMapper).toArray(l->new String[l]);
            db.lockPositions(pos);
            
            // check that all position to be processed are effectively locked
            List<String> lockedPos = positions.stream().map(posIdxNameMapper).filter(p->db.getDao(p).isReadOnly()).collect(Collectors.toList());
            if (!lockedPos.isEmpty()) {
                ui.setMessage("Some positions could not be locked and will not be processed: " + lockedPos);
                for (String p : lockedPos) errors.addExceptions(new Pair(p, new RuntimeException("Locked position. Already used by another process?")));
                positions.removeIf( p -> MasterDAO.getDao(db, p).isReadOnly());
            }
            boolean needToDeleteObjects = preProcess || segmentAndTrack;
            boolean deleteAll =  needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && positions.size()==db.getExperiment().getPositionCount();
            if (deleteAll) {
                publish("deleting objects...");
                db.deleteAllObjects();
            }
            boolean deleteAllField = needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && !deleteAll;
            logger.info("Run task: db: {} preProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", dbName, preProcess, segmentAndTrack, trackOnly, measurements, needToDeleteObjects, deleteAll, deleteAllField);
            if (this.taskCounter==null) this.taskCounter = new int[]{0, this.countSubtasks()};
            publish("number of subtasks: "+countSubtasks());
            
            try {
                positions.stream().map((pIdx) -> db.getExperiment().getPosition(pIdx).getName()).forEachOrdered((position) -> {
                    try {
                        process(position, deleteAllField);
                    } catch (MultipleException e) {
                        errors.addExceptions(e.getExceptions());
                    } catch (Throwable e) {
                        errors.addExceptions(new Pair("Error while processing: db: "+db.getDBName()+" pos: "+position, e));
                    } finally {
                        db.getExperiment().getPosition(position).flushImages(true, true);
                        db.clearCache(position);
                        if (!db.isConfigurationReadOnly() && db.getSelectionDAO()!=null) db.getSelectionDAO().clearCache();
                        ImageWindowManagerFactory.getImageManager().flush();
                        System.gc();
                        publishMemoryUsage("After clearing cache");
                    }
                });
            } catch (Throwable t) {
                publish("Error While Processing Positions");
                publishError(t);
                publishErrors();
            }
            for (Pair<String, int[]> e  : this.extractMeasurementDir) extractMeasurements(e.key==null?db.getDir():e.key, e.value);
            if (exportData) exportData();
            if (!keepDB) db.unlockPositions(pos);
            else positions.forEach(p -> db.clearCache(posIdxNameMapper.apply(p)));
        }
    private void process(String position, boolean deleteAllField) {
        publish("Position: "+position);
        if (deleteAllField) db.getDao(position).deleteAllObjects();
        if (preProcess) {
            publish("Pre-Processing: DB: "+dbName+", Position: "+position);
            logger.info("Pre-Processing: DB: {}, Position: {}", dbName, position);
            Processor.preProcessImages(db.getExperiment().getPosition(position), db.getDao(position), true, this);
            boolean createRoot = segmentAndTrack || trackOnly || generateTrackImages;
            if (createRoot) Processor.getOrCreateRootTrack(db.getDao(position)); // will set opened pre-processed images to root -> no need to open them once again in further steps
            db.getExperiment().getPosition(position).flushImages(true, true); 
            System.gc();
            incrementProgress();
            publishMemoryUsage("After PreProcessing:");
        }
        
        if ((segmentAndTrack || trackOnly)) {
            logger.info("Processing: DB: {}, Position: {}", dbName, position);
            deleteObjects(db.getDao(position), structures);
            List<StructureObject> root = getOrCreateRootTrack(db.getDao(position));
            for (int s : structures) { // TODO take code from processor
                publish("Processing structure: "+s);
                try {
                    executeProcessingScheme(root, s, trackOnly, false);
                } catch (MultipleException e) {
                    errors.addExceptions(e.getExceptions());
                } catch (Throwable e) {
                    errors.addExceptions(new Pair("Error while processing: db: "+db.getDBName()+" pos: "+position+" structure: "+s, e));
                }
                incrementProgress();
                if (generateTrackImages && !db.getExperiment().getAllDirectChildStructures(s).isEmpty()) {
                    publish("Generating Track Images for Structure: "+s);
                    Processor.generateTrackImages(db.getDao(position), s, this);
                    incrementProgress();
                }
                //db.getDao(position).applyOnAllOpenedObjects(o->{if (o.hasRegion()) o.getRegion().clearVoxels();}); // possible memory leak at this stage : list of voxels of big objects -> no necessary for further processing. 
                // TODO : when no more processing with direct parent as root: get all images of direct root children & remove images from root
                System.gc();
                publishMemoryUsage("After Processing structure:"+s);
            }
            publishMemoryUsage("After Processing:");
        } else if (generateTrackImages) {
            publish("Generating Track Images...");
            // generate track images for all selected structure that has direct children
            for (int s : structures) {
                if (db.getExperiment().getAllDirectChildStructures(s).isEmpty()) continue;
                Processor.generateTrackImages(db.getDao(position), s, this);
                incrementProgress();
            }
            //publishMemoryUsage("After Generate Track Images:");
        }
        
        if (measurements) {
            publish("Measurements...");
            logger.info("Measurements: DB: {}, Position: {}", dbName, position);
            Processor.performMeasurements(db.getDao(position), measurementMode, this);
            incrementProgress();
            //publishMemoryUsage("After Measurements");
        }
    }
    public void publishMemoryUsage(String message) {
        publish(message+Utils.getMemoryUsage());
    }
    public void extractMeasurements(String dir, int[] structures) {
        ensurePositionAndStructures(true, true);
        String file = dir+File.separator+db.getDBName()+Utils.toStringArray(structures, "_", "", "_")+".csv";
        publish("extracting measurements from object class: "+Utils.toStringArray(structures));
        publish("measurements will be extracted to: "+ file);
        Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, structures);
        logger.debug("keys: {}", keys);
        MeasurementExtractor.extractMeasurementObjects(db, file, getPositions(), keys);
        incrementProgress();
    }
    public void exportData() {
        try {
            String file = db.getDir()+File.separator+db.getDBName()+"_dump.zip";
            ZipWriter w = new ZipWriter(file);
            if (exportObjects || exportPreProcessedImages || exportTrackImages) {
                ImportExportJSON.exportPositions(w, db, exportObjects, exportPreProcessedImages, exportTrackImages , getPositions(), this);
            }
            if (exportConfig) ImportExportJSON.exportConfig(w, db);
            if (exportSelections) ImportExportJSON.exportSelections(w, db);
            w.close();
        } catch (Exception e) {
            publish("Error while dumping");
            this.errors.addExceptions(new Pair(this.dbName, e));
        }
    }
    private List<String> getPositions() {
        this.ensurePositionAndStructures(true, false);
        List<String> res = new ArrayList<>(positions.size());
        for (int i : positions) res.add(db.getExperiment().getPosition(i).getName());
        return res;
    }
    @Override public String toString() {
        char sep = ';';
        StringBuilder sb = new StringBuilder();
        Runnable addSep = () -> {if (sb.length()>0) sb.append(sep);};

        if (preProcess) sb.append("preProcess");
        if (segmentAndTrack) {
            addSep.run();
            sb.append("segmentAndTrack");
        } else if (trackOnly) {
            addSep.run();
            sb.append("trackOnly");
        }
        if (measurements) {
            addSep.run();
            sb.append("measurements[").append(measurementMode.toString()).append("]");
        }
        if (structures!=null) {
            addSep.run();
            sb.append("structures:").append(ArrayUtils.toString(structures));
        }
        if (positions!=null) {
            addSep.run();
            sb.append("positions:").append(Utils.toStringArrayShort(positions));
        } 
        if (!extractMeasurementDir.isEmpty()) {
            addSep.run();
            sb.append("Extract: ");
            for (Pair<String, int[]> p : this.extractMeasurementDir) sb.append((p.key==null?dir:p.key)).append('=').append(ArrayUtils.toString(p.value));
        }
        if (exportData) {
            if (exportPreProcessedImages) {
                addSep.run();
                sb.append("ExportPPImages");
            }
            if (exportTrackImages) {
                addSep.run();
                sb.append("ExportTrackImages");
            }
            if (exportObjects) {
                addSep.run();
                sb.append("ExportObjects");
            }
            if (exportConfig) {
                addSep.run();
                sb.append("ExportConfig");
            }
            if (exportSelections) {
                addSep.run();
                sb.append("ExportSelection");
            }
        }
        addSep.run();
        sb.append("db:").append(dbName).append(sep).append("dir:").append(dir);
        return sb.toString();
    }
    @Override
    public void incrementProgress() {
        setProgress(100*(++taskCounter[0])/taskCounter[1]);
    }
    @Override
    protected Integer doInBackground() throws Exception {
        this.runTask();
        return this.errors.getExceptions().size();
    }
    @Override
    protected void process(List<String> strings) {
        if (ui!=null) for (String s : strings) ui.setMessage(s);
        for (String s : strings) logger.info(s);
        
    }
    public static boolean printStackTraceElement(String stackTraceElement) {
        //return true;
        return !stackTraceElement.startsWith("java.util.")&&!stackTraceElement.startsWith("java.lang.")
                &&!stackTraceElement.startsWith("java.awt.")&&!stackTraceElement.startsWith("java.lang.")
                &&!stackTraceElement.startsWith("sun.reflect.")&&!stackTraceElement.startsWith("javax.swing.")
                &&!stackTraceElement.startsWith("boa.core.")&&!stackTraceElement.startsWith("boa.utils."); 
    }
    @Override 
    public void done() {
        //logger.debug("EXECUTING DONE FOR : {}", this.toJSON().toJSONString());
        this.publish("Job done.");
        publishErrors();
        this.printErrors();
        this.publish("------------------");
        if (ui!=null) ui.setRunning(false);
    }
    private void unrollMultipleExceptions() {
        // check for multiple exceptions and unroll them
        List<Pair<String, Throwable>> errorsToAdd = new ArrayList<>();
        Iterator<Pair<String, Throwable>> it = errors.getExceptions().iterator();
        while(it.hasNext()) {
            Pair<String, ? extends Throwable> e = it.next();
            if (e.value instanceof MultipleException) {
                it.remove();
                errorsToAdd.addAll(((MultipleException)e.value).getExceptions());
            }
        }
        this.errors.addExceptions(errorsToAdd);
    }
    public void publishErrors() {
        unrollMultipleExceptions();
        this.publish("Errors: "+this.errors.getExceptions().size()+ " For JOB: "+this.toString());
        for (Pair<String, ? extends Throwable> e : errors.getExceptions()) {
            publish("Error @"+e.key+" "+(e.value==null?"null":e.value.toString()));
            publishError(e.value);
        }
    }
    private void publishError(Throwable t) {
        for (StackTraceElement s : t.getStackTrace()) {
            String ss = s.toString();
            if (printStackTraceElement(ss)) publish(s.toString());
        }
        if (t.getCause()!=null) {
            publish("caused By");
            publishError(t.getCause());
        }
    }
    // Progress Callback
    @Override
    public void incrementTaskNumber(int subtask) {
        if (taskCounter!=null) this.taskCounter[1]+=subtask;
    }

    @Override
    public void log(String message) {
        publish(message);
    }
    
    public static void executeTasks(List<Task> tasks, ProgressLogger ui, Runnable... endOfWork) {
        int totalSubtasks = 0;
        for (Task t : tasks) {
            logger.debug("checking task: {}", t);
            if (!t.isValid()) {
                if (ui!=null) ui.setMessage("Invalid task: "+t.toString());
                return;
            } 
            t.setUI(ui);
            totalSubtasks+=t.countSubtasks();
        }
        if (ui!=null) ui.setMessage("Total subTasks: "+totalSubtasks);
        int[] taskCounter = new int[]{0, totalSubtasks};
        for (Task t : tasks) t.setSubtaskNumber(taskCounter);
        DefaultWorker.execute(i -> {
            //if (ui!=null && i==0) ui.setRunning(true);
            tasks.get(i).initDB();
            Consumer<FileProgressLogger> setLF = l->{if (l.getLogFile()==null) l.setLogFile(tasks.get(i).getDir()+File.separator+"Log.txt");};
            Consumer<FileProgressLogger> unsetLF = l->{l.setLogFile(null);};
            if (ui instanceof MultiProgressLogger) ((MultiProgressLogger)ui).applyToLogUserInterfaces(setLF);
            else if (ui instanceof FileProgressLogger) setLF.accept((FileProgressLogger)ui);
            tasks.get(i).runTask(); // clears cache +  unlock if !keepdb
            tasks.get(i).done();
            if (tasks.get(i).db!=null && !tasks.get(i).keepDB) tasks.get(i).db=null; 
            if (ui instanceof MultiProgressLogger) ((MultiProgressLogger)ui).applyToLogUserInterfaces(unsetLF);
            else if (ui instanceof FileProgressLogger) unsetLF.accept((FileProgressLogger)ui);
            
            if (ui!=null && i==tasks.size()-1) {
                ui.setRunning(false);
                if (tasks.size()>1) {
                    for (Task t : tasks) t.publishErrors();
                }
            }
            return "";
        }, tasks.size()).setEndOfWork(
                ()->{for (Runnable r : endOfWork) r.run();});
    }
    public static void executeTask(Task t, ProgressLogger ui, Runnable... endOfWork) {
        executeTasks(new ArrayList<Task>(1){{add(t);}}, ui, endOfWork);
    }
    private static Stream<Task> splitByPosition(Task task) {
        task.ensurePositionAndStructures(true, true);
        Function<Integer, Task> subTaskCreator = p -> {
            Task res = new Task(task.dbName, task.getDir()).setPositions(p).setStructures(task.structures);
            if (task.preProcess) res.preProcess = true;
            res.setStructures(task.structures);
            res.segmentAndTrack = task.segmentAndTrack;
            res.trackOnly = task.trackOnly;
            if (task.measurements) res.measurements = true;
            return res;
        };
        return task.positions.stream().map(subTaskCreator);
    }
    
    // check that no 2 xp with same name and different dirs
    private static void checkXPNameDir(List<Task> tasks) {
        boolean[] haveDup = new boolean[1];
        tasks.stream().map(t -> new Pair<>(t.dbName, t.dir)).distinct().collect(Collectors.groupingBy(p -> p.key)).entrySet().stream().filter(e->e.getValue().size()>1).forEach(e -> {
            haveDup[0] = true;
            logger.error("Task: {} has several directories: {}", e.getKey(), e.getValue().stream().map(p->p.value).collect(Collectors.toList()));
        });
        if (haveDup[0]) throw new IllegalArgumentException("Cannot process tasks: some duplicate experiment name with distinct path");
    }
    public static Map<XP_POS, List<Task>> getProcessingTasksByPosition(List<Task> tasks) {
        //checkXPNameDir(tasks);
        BinaryOperator<Task> taskMerger=(t1, t2) -> {
            if (!Arrays.equals(t1.structures, t2.structures)) throw new IllegalArgumentException("Tasks should have same structures to be merged");
            if (t2.measurements) t1.measurements= true;
            if (t2.preProcess) t1.preProcess = true;
            if (t2.segmentAndTrack) {
                t1.segmentAndTrack = true;
                t1.trackOnly = false;
            } else if (t2.trackOnly && !t1.segmentAndTrack) t1.trackOnly = true;
            
            return t1;
        };
        Map<XP_POS, List<Task>> res = tasks.stream().flatMap(t -> splitByPosition(t)) // split by db / position
                .collect(Collectors.groupingBy(t->new XP_POS_S(t.dbName, t.dir, t.positions.get(0), new StructureArray(t.structures)))) // group including structures;
                .entrySet().stream().map(e -> e.getValue().stream().reduce(taskMerger).get()) // merge all tasks from same group
                .collect(Collectors.groupingBy(t->new XP_POS(t.dbName, t.dir, t.positions.get(0)))); // merge without including structures
        Function<Task, Stream<Task>> splitByStructure = t -> {
            return Arrays.stream(t.structures).mapToObj(s->  new Task(t.dbName, t.getDir()).setActions(false, t.segmentAndTrack, t.trackOnly, false).setPositions(t.positions.get(0)).setStructures(s));
        };
        Comparator<Task> subTComp = (t1, t2)-> {
            int sC = Integer.compare(t1.structures[0], t2.structures[0]);
            if (sC!=0) return sC;
            if (t1.segmentAndTrack && t2.segmentAndTrack) return 0;
            if (t1.segmentAndTrack && !t2.segmentAndTrack) return -1;
            else return 1;
        };
        res.entrySet().stream().filter(e->e.getValue().size()>1).forEach(e-> { // remove redundent tasks
            boolean meas = false, pp = false;
            for (Task t : e.getValue()) {
                if (t.measurements) {
                    meas = true;
                    t.measurements=false;
                }
                if (t.preProcess) {
                    pp = true;
                    t.preProcess=false;
                }
            }
            Task ta = e.getValue().get(0);
            e.getValue().removeIf(t->!t.segmentAndTrack && !t.trackOnly);
            if (e.getValue().isEmpty() && (meas || pp) ) e.getValue().add(ta);
            else { // tasks are only segment / track : reduce tasks in minimal number
                // split in one task per structure
                List<Task> subT = e.getValue().stream().flatMap(splitByStructure).distinct().sorted(subTComp).collect(Collectors.toList());
                logger.debug("subtT: {}", subT);
                // remove redondent tasks
                BiPredicate<Task, Task> removeNext = (t1, t2) -> t1.structures[0] == t2.structures[0] && t2.trackOnly; // sorted tasks
                for (int i = 0; i<subT.size()-1; ++i) { 
                    while(subT.size()>i+1 && removeNext.test(subT.get(i), subT.get(i+1))) subT.remove(i+1);
                }
                logger.debug("subtT after remove: {}", subT);
                // merge per segment
                BiPredicate<Task, Task> mergeNext = (t1, t2) -> t1.segmentAndTrack == t2.segmentAndTrack && t1.trackOnly == t2.trackOnly ;
                BiConsumer<Task, Task> merge = (t1, t2) -> {
                    List<Integer> allS = Utils.toList(t1.structures);
                    allS.addAll(Utils.toList(t2.structures));
                    Utils.removeDuplicates(allS, false);
                    Collections.sort(allS);
                    t1.setStructures(Utils.toArray(allS, false));
                };
                for (int i = 0; i<subT.size()-1; ++i) {
                    while(subT.size()>i+1 && mergeNext.test(subT.get(i), subT.get(i+1))) merge.accept(subT.get(i), subT.remove(i+1));
                }
                logger.debug("subtT after merge: {}", subT);
                e.setValue(subT);
            }
            if (pp) e.getValue().get(0).preProcess=true;
            if (meas) e.getValue().get(e.getValue().size()-1).measurements = true;
        });
        return res;
    }
    public static Map<XP, List<Task>> getGlobalTasksByExperiment(List<Task> tasks) {
        //checkXPNameDir(tasks);
        Function<Task, Task> getGlobalTask = t -> {
            if (!t.exportConfig && !t.exportData && !t.exportObjects && !t.exportPreProcessedImages && !t.exportSelections && !t.exportTrackImages && t.extractMeasurementDir.isEmpty()) return null;
            Task res = new Task(t.dbName, t.getDir());
            res.extractMeasurementDir.addAll(t.extractMeasurementDir);
            res.exportConfig = t.exportConfig;
            res.exportData = t.exportData;
            res.exportObjects = t.exportObjects;
            res.exportPreProcessedImages = t.exportPreProcessedImages;
            res.exportSelections = t.exportSelections;
            res.exportTrackImages = t.exportTrackImages;
            res.setPositions(Utils.toArray(t.positions, false));
            return res;
        };
        return tasks.stream().map(getGlobalTask).filter(t->t!=null).collect(Collectors.groupingBy(t->new XP(t.dbName, t.dir)));
    }
    // utility classes for task split & merge
    public static class XP {
        public final String dbName, dir;

        public XP(String dbName, String dir) {
            this.dbName = dbName;
            this.dir = dir;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 89 * hash + Objects.hashCode(this.dbName);
            hash = 89 * hash + Objects.hashCode(this.dir);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final XP other = (XP) obj;
            if (!Objects.equals(this.dbName, other.dbName)) {
                return false;
            }
            if (!Objects.equals(this.dir, other.dir)) {
                return false;
            }
            return true;
        }
        
    }
    
    public static class XP_POS extends XP {
        public final int position;
        
        public XP_POS(String dbName, String dir, int position) {
            super(dbName, dir);
            this.position = position;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 53 * hash + Objects.hashCode(this.dbName);
            hash = 53 * hash + Objects.hashCode(this.dir);
            hash = 53 * hash + this.position;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final XP_POS other = (XP_POS) obj;
            if (this.position != other.position) {
                return false;
            }
            if (!Objects.equals(this.dbName, other.dbName)) {
                return false;
            }
            if (!Objects.equals(this.dir, other.dir)) {
                return false;
            }
            return true;
        }
        
        
    }
    private static class XP_POS_S extends XP_POS {
        StructureArray structures;
        
        public XP_POS_S(String dbName, String dir, int position, StructureArray structures) {
            super(dbName, dir, position);
            this.structures=structures;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.dbName);
            hash = 37 * hash + Objects.hashCode(this.dir);
            hash = 37 * hash + Objects.hashCode(this.structures);
            hash = 37 * hash + this.position;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final XP_POS_S other = (XP_POS_S) obj;
            if (this.position != other.position) {
                return false;
            }
            if (!Objects.equals(this.dbName, other.dbName)) {
                return false;
            }
            if (!Objects.equals(this.dir, other.dir)) {
                return false;
            }
            if (!Objects.equals(this.structures, other.structures)) {
                return false;
            }
            return true;
        }
        
    }
    
    private static class StructureArray implements Comparable<StructureArray> {
        final int[] structures;

        public StructureArray(int[] structures) {
            this.structures = structures;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Arrays.hashCode(this.structures);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final StructureArray other = (StructureArray) obj;
            return Arrays.equals(this.structures, other.structures);
        }

        @Override
        public int compareTo(StructureArray o) {
            if (structures==null) {
                if (o.structures==null) return 0;
                else return o.structures[0] == 0 ? 0 : -1;
            } else {
                if (o.structures==null) return structures[0] == 0 ? 0 : 1;
                else return Integer.compare(structures[0], o.structures[0]); // structures is a sorted array
            }
        }
        
    }
}
