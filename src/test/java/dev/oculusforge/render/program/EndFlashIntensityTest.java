package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.nio.*;
import java.util.List;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class EndFlashIntensityTest {
    private static WorldMatrixData snapshot(float intensity) {
        var identity=new Matrix4f();
        return new WorldMatrixData(identity,identity,identity,identity,1,2,3,854,480,6000,
            AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,WorldEnvironment.CLEAR,
            new EyeBrightnessSmoother.Value(16,32),CustomUniformBindings.Values.EMPTY,intensity);
    }
    @Test void nativeTerrainAndShadowBindIntensityInBothStages() throws Exception {
        for(boolean shadow:new boolean[]{false,true}) {
            var program=NativeTerrainProgram.adaptWithMesh(
                "uniform float endFlashIntensity;\nvoid main(){gl_Position=ftransform()+vec4(endFlashIntensity);}",
                "uniform float endFlashIntensity;\nvoid main(){gl_FragColor=vec4(endFlashIntensity);}",
                List.of(0),shadow,false);
            assertTrue(program.usesWorldMatrices());
            assertFalse(program.needsShadowMatrices());
            for(String source:List.of(program.vertexSource(),program.fragmentSource()))
                assertTrue(source.contains("vec4(oculusNative_endFlashIntensity)"));
        }
    }
    @Test void incorrectTypesDuplicatesArraysAndCustomOverridesReject() {
        for(String declaration:List.of("uniform int endFlashIntensity;",
            "uniform float endFlashIntensity,endFlashIntensity;","uniform float endFlashIntensity[2];"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(
                "void main(){gl_Position=ftransform();}",declaration+"\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)));
        assertThrows(IllegalArgumentException.class,()->CustomExpressionRuntime.compile(
            java.util.Map.of("endFlashIntensity","1"),java.util.Set.of("endFlashIntensity")));
    }
    @Test void payloadUsesAlignmentGapWithoutMovingExistingValues() {
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        for(ByteBuffer bytes:List.of(ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE + 16),ByteBuffer.allocateDirect(NativeWorldBindings.BYTE_SIZE + 16))) {
            bytes.order(ByteOrder.nativeOrder());
            for(int i=0;i<bytes.capacity();i++) bytes.put(i,(byte)0x5a);
            bytes.position(8).limit(8 + NativeWorldBindings.BYTE_SIZE);
            var first=snapshot(.75f);first.write(bytes);
            assertEquals(.75f,bytes.getFloat(8+596));
            assertEquals(16,bytes.getInt(8+608));assertEquals(32,bytes.getInt(8+612));
            assertEquals(0,bytes.getInt(8+620));
            for(int slot=0;slot<64;slot++) assertEquals(0f,bytes.getFloat(8+624+slot*4));
            assertEquals(8,bytes.position());assertEquals(0x5a5a5a5a,bytes.getInt(0));
            assertEquals(0x5a5a5a5a,bytes.duplicate().clear().getInt(8 + NativeWorldBindings.BYTE_SIZE));
            snapshot(0).write(bytes);assertEquals(0f,bytes.getFloat(8+596));
            first.write(bytes);assertEquals(.75f,bytes.getFloat(8+596));
        }
    }
    @Test void nonfiniteIntensityRejectsBeforeUpload() {
        for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->snapshot(value));
        assertDoesNotThrow(()->snapshot(0));assertDoesNotThrow(()->snapshot(1));
    }
    @Test void completePackPreparationBindsNativeAndPostWithoutCustomSlots(
        @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var shaders=java.nio.file.Files.createDirectories(root.resolve("shaders"));
        String vertex="#version 120\nuniform float endFlashIntensity;\n"
            +"void main(){gl_Position=ftransform()+vec4(endFlashIntensity*0.001);}";
        String fragment="#version 120\nuniform float endFlashIntensity;\n"
            +"void main(){gl_FragColor=vec4(endFlashIntensity);}";
        for(String stage:List.of("gbuffers_terrain","shadow")) {
            java.nio.file.Files.writeString(shaders.resolve(stage+".vsh"),vertex);
            java.nio.file.Files.writeString(shaders.resolve(stage+".fsh"),fragment);
        }
        java.nio.file.Files.writeString(shaders.resolve("final.fsh"),fragment);
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("flash","Flash",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var nativeWorld=prepared.world().variant("shaders");
        assertNotNull(nativeWorld.nativeTerrain(),nativeWorld.nativeTerrainIssue());
        assertNotNull(nativeWorld.nativeShadow(),nativeWorld.nativeShadowIssue());
        var variant=prepared.variants().get("shaders");
        assertTrue(variant.customUniforms().slots().isEmpty());
        assertEquals(List.of(new LegacyFinalShaderAdapter.UniformBinding("endFlashIntensity",
            LegacyFinalShaderAdapter.UniformSemantic.END_FLASH_INTENSITY)),
            variant.programs().getFirst().fragment().uniforms());
    }
}
