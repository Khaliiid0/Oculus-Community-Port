package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class HeldLightTest {
    @Test void shadowHeldFixturePreparesBothNativePrograms() throws Exception {
        var root = java.nio.file.Path.of(getClass().getClassLoader().getResource("held-shadow").toURI());
        var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("held-shadow", "Held shadow", root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world = prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(), world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(), world.nativeShadowIssue());
        assertTrue(world.nativeShadow().usesWorldMatrices());
        assertTrue(world.nativeShadow().vertexSource().contains("oculusNative_heldBlockLightValue"));
        assertTrue(world.nativeShadow().fragmentSource().contains("oculusNative_heldBlockLightValue2"));
        assertFalse(prepared.world().oldHandLight());
    }

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void packInspectionReadsHandPolicyAndPreparesHeldBindings() throws Exception {
        var shaders = java.nio.file.Files.createDirectories(directory.resolve("shaders"));
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.vsh"),
            "void main(){gl_Position=ftransform();}");
        java.nio.file.Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),
            "uniform int heldBlockLightValue;\nvoid main(){gl_FragColor=vec4(float(heldBlockLightValue));}");
        java.nio.file.Files.writeString(shaders.resolve("final.fsh"), "void main(){gl_FragColor=vec4(1);}");
        for (boolean legacy : new boolean[] {true, false}) {
            java.nio.file.Files.writeString(shaders.resolve("shaders.properties"), "oldHandLight=" + legacy);
            var prepared = new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
                new dev.oculusforge.shaderpack.ShaderPackDescriptor("held", "Held probe", directory,
                    dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
            assertEquals(legacy, prepared.world().oldHandLight());
            assertNotNull(prepared.world().variant("shaders").nativeTerrain());
        }
    }

    @Test void independentAndLegacyHands() {
        assertEquals(new HeldLight(4, 15), HeldLight.resolve(4, 15, false));
        assertEquals(new HeldLight(15, 15), HeldLight.resolve(4, 15, true));
        assertEquals(new HeldLight(15, 0), HeldLight.resolve(15, 0, true));
        assertEquals(HeldLight.NONE, HeldLight.capture(null, true));
        // Modded emission is not silently clamped to vanilla's range.
        assertEquals(new HeldLight(20, 20), HeldLight.resolve(0, 20, true));
    }

    @Test void legacyPropertyDefaultsAndOverrides() throws Exception {
        assertTrue(HeldLight.legacyMode(""));
        assertTrue(HeldLight.legacyMode("# oldHandLight=false\noldHandLight=true"));
        assertFalse(HeldLight.legacyMode("oldHandLight = false  \n"));
        assertFalse(HeldLight.legacyMode("oldHandLight=true\noldHandLight=false"));
    }

    @Test void planTransformsPreserveHandPolicy() {
        var plan = new WorldProgramPlan(Map.of(), false);
        assertFalse(plan.withRequiredColorBuffers(Map.of()).oldHandLight());
        assertFalse(plan.withFormats(Map.of()).oldHandLight());
        assertTrue(new WorldProgramPlan(Map.of()).oldHandLight());
    }

    @Test void integerBindingsWorkInBothStagesAndMeshModes() throws Exception {
        String decl = "uniform int heldBlockLightValue, heldBlockLightValue2;\n";
        for (boolean shadow : new boolean[] {false, true}) {
            for (boolean extended : new boolean[] {false, true}) {
                var result = NativeTerrainProgram.adaptWithMesh(
                    decl + "void main(){gl_Position=ftransform()+vec4(float(heldBlockLightValue));}",
                    decl + "void main(){gl_FragColor=vec4(float(heldBlockLightValue2));}",
                    List.of(0), shadow, extended);
                assertTrue(result.usesWorldMatrices());
                assertTrue(result.vertexSource().contains("float(oculusNative_heldBlockLightValue)"));
                assertTrue(result.fragmentSource().contains("float(oculusNative_heldBlockLightValue2)"));
            }
        }
    }

    @Test void malformedHeldUniformsAreRejected() {
        for (String decl : List.of("uniform float heldBlockLightValue;",
            "uniform int heldBlockLightValue[2];", "uniform int heldBlockLightValue, heldBlockLightValue;")) {
            assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
                "void main(){gl_Position=ftransform();}", decl + "\nvoid main(){gl_FragColor=vec4(1);}", List.of(0)));
        }
    }

    @Test void appendedIntegersPreserveEarlierLayoutAndBounds() {
        var identity = new Matrix4f();
        var snapshot = new WorldMatrixData(identity, identity, identity, identity,
            1, 2, 3, 854, 480, 24007, new AnimationClock.Snapshot(42, .01f, 2),
            new RelativeEyePosition(1, -2, 3), new HeldLight(14, 7));
        for (ByteBuffer bytes : List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),
            ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder()).position(8).limit(NativeWorldBindings.BYTE_SIZE + 8);
            snapshot.write(bytes);
            assertEquals(42, bytes.getInt(8 + 548));
            assertEquals(-2f, bytes.getFloat(8 + 564));
            assertEquals(14, bytes.getInt(8 + 576));
            assertEquals(7, bytes.getInt(8 + 580));
            assertEquals(0, bytes.getInt(8 + 584));
            assertEquals(0, bytes.getInt(8 + 588));
            assertEquals(8, bytes.position());
            assertEquals(0, bytes.getInt(0));
            assertEquals(0, bytes.duplicate().clear().getInt(NativeWorldBindings.BYTE_SIZE + 8));
        }
    }
}
