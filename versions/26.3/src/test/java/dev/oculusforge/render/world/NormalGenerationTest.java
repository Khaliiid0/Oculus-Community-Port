package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NormalGenerationTest {
    @Test void normalAtlasCannotOutliveItsResourceOrAlbedoGeneration() {
        Object resources=new Object(),albedo=new Object();
        assertDoesNotThrow(()->NativeMaterialAtlas.requireGeneration(resources,albedo,resources,albedo));
        assertThrows(IllegalStateException.class,()->NativeMaterialAtlas.requireGeneration(resources,albedo,new Object(),albedo));
        assertThrows(IllegalStateException.class,()->NativeMaterialAtlas.requireGeneration(resources,albedo,resources,new Object()));
        assertThrows(IllegalStateException.class,()->NativeMaterialAtlas.requireGeneration(null,null,null,null));
    }
}
