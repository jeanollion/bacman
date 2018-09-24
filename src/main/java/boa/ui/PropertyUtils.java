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
package boa.ui;

import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Listenable;
import boa.configuration.parameters.NumberParameter;
import static boa.ui.GUI.logger;
import boa.configuration.parameters.Parameter;
import boa.data_structure.dao.MasterDAOFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;

/**
 *
 * @author Jean Ollion
 */
public class PropertyUtils {
    private static Properties props;
    public final static String LOG_FILE = "log_file";
    public final static String LOG_ACTIVATED = "log_activated";
    public final static String LOG_APPEND = "log_append";
    public final static String MONGO_BIN_PATH = "mongo_bin_path";
    public final static String LAST_SELECTED_EXPERIMENT = "last_selected_xp";
    public final static String EXPORT_FORMAT = "export_format";
    public final static String LAST_IMPORT_IMAGE_DIR = "last_import_image_dir";
    public final static String LAST_IO_DATA_DIR = "last_io_data_dir";
    public final static String LAST_IO_CONFIG_DIR = "last_io_config_dir";
    public final static String LAST_EXTRACT_MEASUREMENTS_DIR = "last_extract_measurement_dir";
    public final static String LOCAL_DATA_PATH = "local_data_path";
    public final static String HOSTNAME = "hostname";
    public final static String DATABASE_TYPE = MasterDAOFactory.DAOType.Morphium.toString();
    
    public static Properties getProps() { 
        if (props == null) { 
            props = new Properties();  
            File f = getFile(); 
            if (f.exists()) { 
                try { 
                    props.load(new FileReader(f)); 
                } catch (IOException e) { 
                    logger.error("Error while trying to load property file", e);
                } 
            } 
        }
        
        return props; 
    }
    public static String get(String key) {
        return getProps().getProperty(key);
    }
    public static String get(String key, String defaultValue) {
        return getProps().getProperty(key, defaultValue);
    }
    public static void set(String key, String value) {
        if (value!=null) {
            getProps().setProperty(key, value);
            saveParamChanges();
        }
        else remove(key);
    }
    public static void set(String key, int value) {
        getProps().setProperty(key, Integer.toString(value));
        saveParamChanges();
    }
    public static void set(String key, double value) {
        getProps().setProperty(key, Double.toString(value));
        saveParamChanges();
    }
    public static void remove(String key) {
        getProps().remove(key);
        saveParamChanges();
    }
    public static boolean get(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getProps().getProperty(key, Boolean.toString(defaultValue)));
    }
    public static int get(String key, int defaultValue) {
        return Integer.parseInt(getProps().getProperty(key, Integer.toString(defaultValue)));
    }
    public static double get(String key, double defaultValue) {
        return Double.parseDouble(getProps().getProperty(key, Double.toString(defaultValue)));
    }
    public static void set(String key, boolean value) {
        getProps().setProperty(key, Boolean.toString(value));
        saveParamChanges();
    }
    public static void setStrings(String key, List<String> values) {
        if (values==null) values=Collections.EMPTY_LIST;
        for (int i = 0; i<values.size(); ++i) {
            getProps().setProperty(key+"_"+i, values.get(i));
        }
        int idx = values.size();
        while(getProps().containsKey(key+"_"+idx)) {
            getProps().remove(key+"_"+idx);
            ++idx;
        }
        saveParamChanges();
    }
    public static void addStringToList(String key, String... values) {
        List<String> allStrings = PropertyUtils.getStrings(key);
        boolean store = false;
        for (String v : values) {
            allStrings.remove(v);
            allStrings.add(v);
        }
        if (store) PropertyUtils.setStrings(key, allStrings);
    }
    public static void addFirstStringToList(String key, String... values) {
        List<String> allStrings = PropertyUtils.getStrings(key);
        for (String v : values) {
            allStrings.remove(v);
            allStrings.add(0, v);
        }
        PropertyUtils.setStrings(key, allStrings);
    }
    public static List<String> getStrings(String key) {
        List<String> res = new ArrayList<>();
        int idx = 0;
        String next = get(key+"_"+idx);
        while(next!=null) {
            res.add(next);
            ++idx;
            next = get(key+"_"+idx);
        }
        return res;
    }
    public static synchronized void saveParamChanges() {
        try {
            File f = getFile();
            OutputStream out = new FileOutputStream( f );
            props.store(out, "This is an optional header comment string");
        }
        catch (Exception e ) {
            logger.error("Error while trying to save to property file", e);
        }
    }
    
    private static File getFile() { 
        String path = System.getProperty("user.home") + "/.boa.cfg";
        File f = new File(path); 
        if (!f.exists()) try {
            f.createNewFile();
        } catch (IOException ex) {
            logger.error("Error while trying to create property file at: "+path, ex);
        }
        //logger.info("propery file: "+f);
        return f;
    }

    public static void setPersistant(JMenuItem item, String key, boolean defaultValue) {
        item.setSelected(PropertyUtils.get(key, defaultValue));
        item.addActionListener((java.awt.event.ActionEvent evt) -> { logger.debug("item: {} persistSel {}", key, item.isSelected());PropertyUtils.set(key, item.isSelected()); });
    }
    public static int setPersistant(ButtonGroup group, String key, int defaultSelectedIdx) {
        Enumeration<AbstractButton> enume = group.getElements();
        int idxSel = get(key, defaultSelectedIdx);
        int idx= 0;
        logger.debug("set persistant: {} #={} current Sel: {}", key, group.getButtonCount(), idxSel);
        while (enume.hasMoreElements()) {
            AbstractButton b = enume.nextElement();
            b.setSelected(idx == idxSel);
            final int currentIdx = idx;
            b.addActionListener((java.awt.event.ActionEvent evt) -> { logger.debug("item: {} persistSel {}", b, b.isSelected()); PropertyUtils.set(key, currentIdx); });
            ++idx;
        }
        return idxSel;
    }
    public static void setPersistant(Listenable parameter, String key) {
        if (parameter instanceof NumberParameter) {
            NumberParameter np = (NumberParameter)parameter;    
            np.setValue(get(key, np.getValue().doubleValue()));
            logger.debug("persit number: {} = {} -> {}", np.getName(), np.getValue().doubleValue(), np.toString());
            parameter.addListener(p->{
                //logger.debug("persist parameter: {}", ((Parameter)parameter).getName());
                PropertyUtils.set(key, ((NumberParameter)p).getValue().doubleValue());
            });
        } else if (parameter instanceof ChoiceParameter) {
            ChoiceParameter cp = (ChoiceParameter) parameter;
            cp.setValue(get(key, cp.getValue()));
            logger.debug("persit choice: {} -> {}", cp.getName(), cp.getValue());
            cp.addListener(p -> {
                PropertyUtils.set(key, ((ChoiceParameter)p).getValue());
                logger.debug("persit choice: {} -> {}", cp.getName(), cp.getValue());
            });
        } else logger.debug("persistance on parameter not supported yet!");
        
    }
}
