package io.github.gerbesh.unusualcopper;

import io.github.gerbesh.unusualcopper.client.MiningSphereMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningSphereMeshTest {
    @Test void cachedSphereHasFiniteNormalsAndTextureCoordinates(){
        assertEquals(512,MiningSphereMesh.VERTICES.size());
        for(var v:MiningSphereMesh.VERTICES){assertEquals(1,v.x()*v.x()+v.y()*v.y()+v.z()*v.z(),.00001);assertTrue(v.u()>=0&&v.u()<=1);assertTrue(v.v()>=0&&v.v()<=1);}
    }
    @Test void permanentSphereHasFiveBlockDiameterAndCoversBothPoles(){
        assertEquals(5,MiningSphereMesh.RADIUS*2);
        assertTrue(MiningSphereMesh.VERTICES.stream().anyMatch(v->v.y()==1));assertTrue(MiningSphereMesh.VERTICES.stream().anyMatch(v->v.y()==-1));
    }
    @Test void meshCannotBeChangedByAnotherFrame(){assertThrows(UnsupportedOperationException.class,()->MiningSphereMesh.VERTICES.clear());}
}
