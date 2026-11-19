package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.program.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SceneColorFormatTest {
    @Test void flagKeepsLegacyRgba8AndExplicitSceneFormatsSeparate() {
        var formats=new ColorBufferFormats(Map.of(0,ColorBufferFormat.RGBA16F));
        assertEquals(SceneColorFormat.ENABLED?ColorBufferFormat.RGBA16F:ColorBufferFormat.RGBA8,SceneColorFormat.terrain(formats));
        assertEquals(SceneColorFormat.ENABLED,SceneColorFormat.requiresCapture(formats));
        assertFalse(SceneColorFormat.requiresCapture(ColorBufferFormats.DEFAULT));
        System.out.println("HDR world scene mode: "+SceneColorFormat.ENABLED);
    }
    @Test void unsupportedTransparencyPaddedRgbCustomClearsAndHistoryRejectExplicitly() {
        var hdr=new ColorBufferFormats(Map.of(0,ColorBufferFormat.RGBA16F));
        assertDoesNotThrow(()->SceneColorFormat.requireSupported(hdr,false));
        assertDoesNotThrow(()->SceneColorFormat.requireSupported(new ColorBufferFormats(Map.of(0,ColorBufferFormat.R11F_G11F_B10F)),false));
        assertThrows(IllegalStateException.class,()->SceneColorFormat.requireSupported(hdr,true));
        assertThrows(IllegalStateException.class,()->SceneColorFormat.requireSupported(new ColorBufferFormats(Map.of(0,ColorBufferFormat.RGB16F)),false));
        assertThrows(IllegalStateException.class,()->SceneColorFormat.requireSupported(new ColorBufferFormats(hdr.declared(),Map.of(0,false),Map.of()),false));
        assertThrows(IllegalStateException.class,()->SceneColorFormat.requireSupported(new ColorBufferFormats(hdr.declared(),Map.of(),Map.of(0,ColorClearValue.BLACK)),false));
    }
    @Test void ownedSceneChargesPhysicalColorBytesWithoutChargingBorrowedDepthTwice() {
        var formats=new ColorBufferFormats(Map.of(0,ColorBufferFormat.RGBA16F,4,ColorBufferFormat.RGB16F));
        var legacy=new WorldTargetLayout(1920,1080,Set.of(4),1024,formats,true,Set.of(0,1));
        var scene=new WorldTargetLayout(1920,1080,Set.of(0,4),1024,formats,true,Set.of(0,1),true);
        assertEquals(1920L*1080*8,scene.byteSize()-legacy.byteSize());
        assertEquals(legacy.textureCount()+1,scene.textureCount());assertEquals(Set.of(4),scene.auxiliaryBuffers());
        assertNotEquals(legacy,scene);
        assertThrows(IllegalStateException.class,()->new WorldTargetLayout(854,480,Set.of(),4096,formats,false,Set.of(0),true));
    }
    @Test void transferDescriptorsAreInternedAndUseRealDestinationFormats() {
        for(var format:List.of(ColorBufferFormat.RGBA8,ColorBufferFormat.RGBA16F,ColorBufferFormat.R11F_G11F_B10F,ColorBufferFormat.RGB16F)) {
            var pipeline=FullscreenColorTransfer.pipeline(format);
            assertSame(pipeline,FullscreenColorTransfer.pipeline(format));
            assertEquals(format.gpu(),pipeline.getColorTargetStates().getFirst().format());
            assertEquals(format.writeMask(),pipeline.getColorTargetStates().getFirst().writeMask());
            assertFalse(pipeline.isCull());assertNull(pipeline.getDepthStencilState());
        }
    }
    @Test void inactiveScopeNeverReplacesForeignTargetsOrDefersOwners() {
        assertNull(WorldSceneCapture.colorTexture(null,null));assertNull(WorldSceneCapture.colorView(null,null));
        assertFalse(WorldSceneCapture.isMain(null));assertFalse(WorldSceneCapture.ownsTargets(null));
        assertFalse(WorldSceneCapture.deferClose(null));
        assertThrows(IllegalStateException.class,()->WorldSceneCapture.format(null));
    }
    @Test void actualPrecisionFixturePreparesNativeTerrainAndIndependentColortexZeroRead() throws Exception {
        var root=java.nio.file.Path.of(getClass().getClassLoader().getResource("hdr-world-scene").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("hdr-scene","HDR scene",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertEquals(ColorBufferFormat.RGBA16F,world.formats().get(0));assertEquals(List.of(0),world.terrainTargets());
        assertEquals(ColorBufferPlan.MAIN,prepared.variants().get("shaders").buffers().steps().getFirst().colorInputs().get("colortex0"));
        assertEquals(ColorBufferFormat.RGBA8,prepared.programs().getFirst().formats().get(ColorBufferPlan.SCREEN));
    }
}
