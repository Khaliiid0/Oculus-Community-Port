package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class SpecularGenerationTest {
    @Test void specularCannotOutliveItsResourceOrAlbedoGeneration() {
        Object resources=new Object(),albedo=new Object();
        assertDoesNotThrow(()->NativeSpecularAtlas.requireGeneration(resources,albedo,resources,albedo));
        assertThrows(IllegalStateException.class,()->NativeSpecularAtlas.requireGeneration(resources,albedo,new Object(),albedo));
        assertThrows(IllegalStateException.class,()->NativeSpecularAtlas.requireGeneration(resources,albedo,resources,new Object()));
        assertThrows(IllegalStateException.class,()->NativeSpecularAtlas.requireGeneration(null,null,null,null));
    }
}
