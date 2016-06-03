package dataStructure.objects;

import image.Image;
import java.util.ArrayList;
import java.util.List;

public interface StructureObjectProcessing extends StructureObjectPreProcessing {

    public Image getRawImage(int structureIdx);
    @Override public StructureObjectProcessing getNext();
    @Override public StructureObjectProcessing getPrevious();
    public List<? extends StructureObjectPostProcessing> setChildrenObjects(ObjectPopulation children, int structureIdx);
}
