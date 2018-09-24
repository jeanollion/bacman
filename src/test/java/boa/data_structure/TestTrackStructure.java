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
package boa.data_structure;

import static boa.test_utils.TestUtils.logger;
import boa.configuration.experiment.ChannelImage;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import boa.data_structure.dao.MasterDAOFactory.DAOType;
import boa.data_structure.Region;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import static boa.data_structure.StructureObjectUtils.setTrackLinks;
import boa.image.BlankMask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static junit.framework.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class TestTrackStructure {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();
    
    @Test
    public void testTrackStructure() {
        //testTrackStructure(DAOType.Morphium);
        testTrackStructure(DAOType.DBMap);
    }
    
    public void testTrackStructure(DAOType daoType) {
        MasterDAO masterDAO = MasterDAOFactory.createDAO("testTrack", testFolder.newFolder("testTrack").getAbsolutePath(), daoType); //
        MasterDAO.deleteObjectsAndSelectionAndXP(masterDAO);
        Experiment xp = new Experiment("test");
        //xp.setOutputImageDirectory("/data/Images/Test/");
        xp.setOutputDirectory(testFolder.newFolder("testTrackOuput").getAbsolutePath());
        ChannelImage image = new ChannelImage("ChannelImage");
        xp.getChannelImages().insert(image);
        Structure microChannel = new Structure("MicroChannel", -1, 0);
        Structure bacteries = new Structure("Bacteries", 0, 0);
        xp.getStructures().insert(microChannel, bacteries);
        bacteries.setParentStructure(0);
        xp.createPosition("field1");
        masterDAO.setExperiment(xp);
        ObjectDAO dao = masterDAO.getDao("field1");
        StructureObject[] rootT = new StructureObject[5];
        for (int i = 0; i<rootT.length; ++i) rootT[i] = new StructureObject(i, new BlankMask(1, 1, 1), dao);
        
        setTrackLinks(Arrays.asList(rootT));
        dao.store(Arrays.asList(rootT));
        StructureObject[] mcT = new StructureObject[5];
        for (int i = 0; i<mcT.length; ++i) mcT[i] = new StructureObject(i, 0, 0, new Region(new BlankMask( 1, 1, 1), 1, false), rootT[i]);
        setTrackLinks(Arrays.asList(mcT));
        dao.store(Arrays.asList(mcT));
        StructureObject[][] bTM = new StructureObject[5][3];
        for (int t = 0; t<bTM.length; ++t) {
            for (int j = 0; j<3; ++j) bTM[t][j] = new StructureObject(t, 1, j, new Region(new BlankMask( 1, 1, 1), j+1, false), mcT[t]);
            //dao.storeLater(bTM[i]);
        }
        for (int i= 1; i<mcT.length; ++i) {
            setTrackLinks(bTM[i-1][0], bTM[i][0], true, true);
            //bTM[i][0].setPreviousInTrack(bTM[i-1][0], false);
        }
        setTrackLinks(bTM[0][0], bTM[1][1], true, false);
        //bTM[1][1].setPreviousInTrack(bTM[0][0], true);
        for (int i= 2; i<mcT.length; ++i) {
            setTrackLinks(bTM[i-1][1], bTM[i][1], true, true);
            //bTM[i][1].setPreviousInTrack(bTM[i-1][1], false);
        }
        setTrackLinks(bTM[2][1], bTM[3][2], true, false);
        //bTM[3][2].setPreviousInTrack(bTM[2][1], true); 
        setTrackLinks(bTM[3][2], bTM[4][2], true, true);
        //bTM[4][2].setPreviousInTrack(bTM[3][2], false);
        setTrackLinks(bTM[0][1], bTM[1][2], true, true);
        //bTM[1][2].setPreviousInTrack(bTM[0][1], false); 
        setTrackLinks(bTM[1][2], bTM[2][2], true, true);
        //bTM[2][2].setPreviousInTrack(bTM[1][2], false);
        /*
        0.0->4
        -1->4
        --3->4
        1.0->2
        2.0
        */
        for (int i = 0; i<bTM.length; ++i) dao.store(Arrays.asList(bTM[i]));
        dao.clearCache();
        // retrive tracks head for microChannels
        List<StructureObject> mcHeads = dao.getTrackHeads(rootT[0], 0);
        
        assertEquals("number of heads for microChannels", 1, mcHeads.size());
        //assertEquals("head is in idCache", mcHeads.get(0), dao.getFromCache(mcHeads.get(0).getId()));
        assertEquals("head for microChannel", mcT[0].getId(), mcHeads.get(0).getId());
        assertEquals("head for microChannel (unique instanciation)", dao.getById(mcT[0].getParentTrackHeadId(), mcT[0].getStructureIdx(), mcT[0].getFrame(), mcT[0].getId()), mcHeads.get(0));

        // retrieve microChannel track
        List<StructureObject> mcTrack = dao.getTrack(mcHeads.get(0));
        assertEquals("number of elements in microChannel track", 5, mcTrack.size());
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("microChannel track element: "+i, mcT[i].getId(), mcTrack.get(i).getId());
        assertEquals("head of microChannel track (unique instanciation)", mcHeads.get(0), mcTrack.get(0));
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("microChannel track element: "+i+ " unique instanciation", dao.getById(mcT[i].getParentTrackHeadId(), mcT[i].getStructureIdx(), mcT[i].getFrame(), mcT[i].getId()), mcTrack.get(i));

        // retrive tracks head for bacteries
        List<StructureObject> bHeadsRetrive = dao.getTrackHeads(mcT[0], 1);
        List<StructureObject> bHeads = new ArrayList<StructureObject>(5){{add(bTM[0][0]);add(bTM[0][1]);add(bTM[0][2]);add(bTM[1][1]);add(bTM[3][2]);}};
        logger.debug("retrived bacts: {}", Utils.toStringList(bHeadsRetrive, b->b.toString()));
        assertEquals("number of heads for bacteries", 5, bHeadsRetrive.size());
        for (int i = 0; i<bHeads.size(); ++i) {
            logger.debug("compare: {} and {}",bHeads.get(i), bHeadsRetrive.get(i) );
            assertEquals("head for bacteries :"+i, bHeads.get(i).getId(), bHeadsRetrive.get(i).getId());
        }
        assertEquals("head for bacteries (0, unique instanciation)", dao.getById(bTM[0][0].getParentTrackHeadId(), bTM[0][0].getStructureIdx(), bTM[0][0].getFrame(), bTM[0][0].getId()), bHeadsRetrive.get(0));

        // retrieve bacteries track
        List<StructureObject> bTrack0 = dao.getTrack(bHeadsRetrive.get(0));
        assertEquals("number of elements in bacteries track (0)", 5, bTrack0.size());
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("bacteries track element: "+i, bTM[i][0].getId(), bTrack0.get(i).getId());
        assertEquals("head of bacteria track (unique instanciation)", bHeadsRetrive.get(0), bTrack0.get(0));
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("bacteries track element: "+i+ " unique instanciation", dao.getById(bTM[i][0].getParentTrackHeadId(), bTM[i][0].getStructureIdx(), bTM[i][0].getFrame(), bTM[i][0].getId()), bTrack0.get(i));

        masterDAO.clearCache();
        
    }

}
