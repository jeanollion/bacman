/*
 * Copyright (C) 2015 jollion
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
package boa.configuration.parameters;

import boa.gui.GUI;
import boa.configuration.experiment.Experiment;
import boa.gui.configuration.ConfigurationTreeModel;
import boa.gui.configuration.TreeModelContainer;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import static boa.configuration.parameters.Parameter.logger;
import boa.core.Processor;
import boa.configuration.experiment.MicroscopyField;
import boa.configuration.experiment.PreProcessingChain;
import boa.configuration.experiment.Structure;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.image_container.MemoryImageContainer;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BoundingBox;
import boa.image.Image;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import boa.plugins.ParameterSetup;
import boa.plugins.ParameterSetupTracker;
import boa.plugins.ProcessingScheme;
import boa.plugins.Segmenter;
import boa.plugins.Tracker;
import boa.plugins.TrackerSegmenter;
import boa.plugins.Transformation;
import static boa.plugins.Transformation.SelectionMode.ALL;
import static boa.plugins.Transformation.SelectionMode.SAME;
import boa.plugins.UseMaps;
import boa.plugins.plugins.processing_scheme.SegmentAndTrack;
import boa.plugins.plugins.processing_scheme.SegmentOnly;
import boa.plugins.plugins.processing_scheme.SegmentThenTrack;
import boa.utils.ArrayUtil;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class ParameterUtils {

    public static ConfigurationTreeModel getModel(Parameter p) {
        if (p instanceof TreeModelContainer) {
            return ((TreeModelContainer) p).getModel();
        }
        Parameter root = p;
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            if (root instanceof TreeModelContainer) {
                return ((TreeModelContainer) root).getModel();
            }
        }
        return null;
    }

    public static Experiment getExperiment(Parameter p) {
        if (p instanceof Experiment) {
            return (Experiment) p;
        }
        Parameter root = p;
        //logger.trace("getExperiment: {}", p.getName());
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            if (root instanceof Experiment) {
                return (Experiment) root;
            }
        }
        return null;
    }
    
    public static MicroscopyField getMicroscopyField(Parameter p) {
        //logger.debug("get microscopy field from tree... {}", p.getName());
        if (p instanceof MicroscopyField) {
            return (MicroscopyField) p;
        }
        Parameter parent = p;
        while (parent.getParent() != null) {
            parent = (Parameter) parent.getParent();
            //logger.debug("get microscopy field from tree... {}", parent.getName());
            if (parent instanceof MicroscopyField) {
                return (MicroscopyField) parent;
            }
        }
        return null;
    }
    
    public static int getTimePointNumber(Parameter p, boolean useRawInputFrames) {
        MicroscopyField f = getMicroscopyField(p);
        if (f!=null) {
            return f.getTimePointNumber(useRawInputFrames);
        } else {
            logger.warn("parameter: {}, no microscopy Field found in tree to get timePoint number", p.getName());
            /*Experiment xp = getExperiment(p);
            if (xp==null) {
                logger.warn("parameter: {}, no experiment found in tree to get timePoint number", p.getName());
                return 0;
            }
            else return xp.getTimePointNumber(afterTrim);*/
            return -1;
        }
    }

    public static boolean setContent(Parameter[] recieve, Parameter[] give) {
        if (recieve==null || give== null || recieve.length!=give.length) return false;
        boolean ok = true;
        for (int i = 0; i < recieve.length; i++) {
            try {
                recieve[i].setContentFrom(give[i]);
            } catch (Error e) {
                logger.debug("set content list error @ {} : r={} / s={}", i, recieve[i]!=null ? recieve[i].getName() : "null", give[i]!=null ? give[i].getName() : "null");
                logger.error("set content error :", e);
                ok = false;
            }
        }
        return ok;
    }
    public static boolean sameContent(List<Parameter> recieve, List<Parameter> give) {return sameContent(recieve, give, null);}
    public static boolean sameContent(List<Parameter> recieve, List<Parameter> give, String message) {
        if (recieve==null && give!=null) {
            if (message!=null) logger.debug(message+" 1st null");
            return false;
        }
        if (recieve!=null && give==null) {
            if (message!=null) logger.debug(message+" 2nd null");
            return false;
        }
        if (recieve==null && give==null) return true;
        if (recieve.size()!=give.size()) {
            if (message!=null) logger.debug(message+" differ in size: {} vs {}", recieve.size(), give.size());
            return false;
        }
        for (int i = 0; i < recieve.size(); i++) {
            if (!recieve.get(i).sameContent(give.get(i))) {
                if (message!=null) logger.debug(message+" differ at index: {}", i);
                return false;
            }
        }
        return true;
    }
    public static boolean setContent(List<Parameter> recieve, List<Parameter> give) {
        if (recieve==null || give== null || recieve.size()!=give.size()) {
            setContentMap(recieve, give);
            return false;
        }
        boolean ok = true;
        for (int i = 0; i < recieve.size(); i++) {
            try {
                recieve.get(i).setContentFrom(give.get(i));
            } catch (IllegalArgumentException e) {
                logger.debug("set content list error @ {} : r={} / s={}", i, recieve.get(i)!=null ? recieve.get(i).getName() : "null", give.get(i)!=null ? give.get(i).getName() : "null");
                logger.error("set content list error : ", e);
                ok = false;
            }
        }
        return ok;
    }
    private static void setContentMap(List<Parameter> recieve, List<Parameter> give) {
        if (recieve==null || recieve.isEmpty() || give==null || give.isEmpty()) return;
        Map<String, Parameter> recieveMap = recieve.stream().collect(Collectors.toMap(Parameter::getName, Function.identity()));
        for (Parameter p : give) {
            if (recieveMap.containsKey(p.getName())) {
                Parameter r = recieveMap.get(p.getName());
                if (r.getClass()==p.getClass()) r.setContentFrom(p);
            }
        }
    }

    public static Parameter[] duplicateArray(Parameter[] parameters) {
        if (parameters==null) return null;
        Parameter[] res = new Parameter[parameters.length];
        for (int i = 0; i < parameters.length; ++i) {
            res[i] = parameters[i].duplicate();
        }
        return res;
    }
    
    public static List<Parameter> duplicateList(List<Parameter> parameters) {
        if (parameters==null) return null;
        ArrayList<Parameter> res = new ArrayList<Parameter>(parameters.size());
        for (Parameter p : parameters) res.add(p.duplicate());
        return res;
    }

    public static boolean arraysEqual(int[] array1, int[] array2) {
        if (array1==null && array2==null) return true;
        else if (array1==null || array2==null) return false;
        if (array1.length == array2.length) {
            for (int i = 0; i < array1.length; ++i) {
                if (array1[i] != array2[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static String[] createChoiceList(int startElement, int endElement) {
        String[] res = new String[endElement - startElement + 1];
        int paddingSize = String.valueOf(endElement).length();
        for (int i = startElement; i <= endElement; ++i) {
            res[i - startElement] = Utils.formatInteger(paddingSize, i);
        }
        return res;
    }

    public static Object duplicateConfigurationData(Object in) {
        //logger.debug("duplicating config data of class: {}, {} ", in.getClass(), in);
        if (in != null) {
            if (in instanceof Number) {
                if (in instanceof Double || in instanceof Float) {
                    return ((Number) in).doubleValue();
                } else if (in instanceof Long) {
                    return ((Number) in).longValue();
                } else {
                    return ((Number) in).intValue();
                }
            } else if (in instanceof String) {
                return (String) in;
            } else if (in instanceof int[]) return copyArray((int[])in);
            else if (in instanceof double[]) return copyArray((double[])in);
            else if (in.getClass().isArray()) {
                if (in instanceof Object[]) {
                    return duplicateConfigurationDataArray((Object[]) in);
                }
            } else if (in.getClass()==ArrayList.class) {
                return duplicateConfigurationDataList((ArrayList)in);
            }
        }
        return null;
    }

    private static int[] copyArray(int[] source) {
        int[] res = new int[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    }
    private static double[] copyArray(double[] source) {
        double[] res = new double[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    } 

    public static Object[] duplicateConfigurationDataArray(Object[] in) {
        if (in != null) {
            Object[] res = new Object[in.length];
            for (int i = 0; i < res.length; ++i) {
                res[i] = duplicateConfigurationData(in[i]);
            }
            return res;
        } else {
            return null;
        }
    }
    public static ArrayList duplicateConfigurationDataList(List in) {
        if (in != null) {
            ArrayList res = new ArrayList(in.size());
            for (Object o : in) {
                res.add(duplicateConfigurationData(o));
            }
            return res;
        } else {
            return null;
        }
    }
    public static Parameter[] aggregate(Parameter[] array, Parameter... parameters) {
        if (parameters.length==0) return array;
        else {
            Parameter[] res = new Parameter[array.length+parameters.length];
            System.arraycopy(array, 0, res, 0, array.length);
            System.arraycopy(parameters, 0, res, array.length, parameters.length);
            return res;
        }
    }
    
    // Configuration by hints
    public static <T extends Parameter> T getFirstParameterFromParents(Class<T> clazz, Parameter parameter, boolean lookInIndirectParents) {
        if (parameter==null) return null;
        Parameter parent=parameter;
        while (parent.getParent()!=null) {
            parent = ((Parameter)parent.getParent());
            // look in siblings/uncles
            if (lookInIndirectParents && parent instanceof ListParameter) {
                for (Parameter p : ((ListParameter<Parameter>)parent).getActivatedChildren()) if (clazz.equals(p.getClass())) return (T)p;
            } else if (lookInIndirectParents && parent instanceof ContainerParameter) {
                for (Parameter p : ((SimpleContainerParameter)parent).getChildren())  if (clazz.equals(p.getClass())) return (T)p;
            } else  if (clazz.equals(parent.getClass())) return (T)parent;
        }
        return null;
    }
    public static void configureStructureParametersFromParent(Parameter parameter) {
        Structure s = getFirstParameterFromParents(Structure.class, parameter, false);
        if (s!=null) configureStructureParameters(s.getIndex(), parameter);
    }
    public static void configureStructureParameters(final int structureIdxHint, Parameter parameter) {
        if (structureIdxHint==-1) return;
        ParameterConfiguration config = new ParameterConfiguration() {
            public void configure(Parameter p) {
                if (((StructureParameter)p).getSelectedStructureIdx()==-1) {
                    ((StructureParameter)p).setSelectedStructureIdx(structureIdxHint);
                    //logger.debug("Configuring: {}, with value: {}", p.getName(), structureIdxHint);
                }
            }
            public boolean isConfigurable(Parameter p) {
                return p instanceof StructureParameter;
            }
        };
        configureParameter(config, parameter);
    }
    
    public static void configureParameter(final ParameterConfiguration config, Parameter parameter) {       
        if (config.isConfigurable(parameter)) config.configure(parameter);
        else if (parameter instanceof ListParameter) {
            for (Parameter p : ((ListParameter<Parameter>)parameter).getActivatedChildren()) configureParameter(config, p);
        } else if (parameter instanceof ContainerParameter) {
            for (Parameter p : ((SimpleContainerParameter)parameter).getChildren()) configureParameter(config, p);
        }
    }
    public interface ParameterConfiguration {
        public void configure(Parameter p);
        public boolean isConfigurable(Parameter p);
    }
}