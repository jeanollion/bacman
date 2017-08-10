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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.GUI;
import boa.gui.ManualCorrection;
import core.Task;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;

/**
 *
 * @author jollion
 */
public class RepairTrackInconsitencies {
    static int structureIdx = 2;
    public static void main(String[] args) {
        //String dbName = "boa_fluo160501";
        String dbName = "boa_fluo160501";
        MasterDAO mDAO = new Task(dbName).getDB();
        ManualCorrection.repairLinksForField(mDAO, mDAO.getExperiment().getPositionsAsString()[0], structureIdx);
        ManualCorrection.repairLinksForField(mDAO, mDAO.getExperiment().getPositionsAsString()[1], structureIdx);
        ManualCorrection.repairLinksForField(mDAO, mDAO.getExperiment().getPositionsAsString()[3], structureIdx);
    }
    
}
