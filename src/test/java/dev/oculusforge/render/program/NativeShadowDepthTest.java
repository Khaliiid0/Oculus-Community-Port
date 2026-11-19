package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeShadowDepthTest {
    @Test void nativeSnapshotGpuDiagnosticPreparesIndependentBindingsAndFullscreenFetches() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("native-shadow-snapshot").toURI());
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("snapshot","Snapshot",root,ShaderPackDescriptor.Kind.DIRECTORY));
        var p=prepared.world().variant("shaders");
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertTrue(p.nativeTerrain().usesShadowDepth());assertTrue(p.nativeTerrain().usesOpaqueShadowDepth());
        assertTrue(p.nativeTerrain().fragmentSource().contains("oculusNativeShadowHelper0(OculusOpaqueShadowDepth,"));
        var finalShader=new LegacyFinalShaderAdapter().adapt(Files.readString(root.resolve("shaders/final.fsh")));
        assertTrue(finalShader.source().contains("oculus_fetchDepth(shadowtex0,"));
        assertTrue(finalShader.source().contains("oculus_fetchDepth(shadowtex1,"));
        assertEquals(2,finalShader.samplers().stream().filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.SHADOW_DEPTH).count());
    }
    @Test void gpuDiagnosticPreparesNativeDepthAndColorConsumer() throws Exception {
        var root=Path.of(getClass().getClassLoader().getResource("shadow-depth-read").toURI());
        var p=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("probe","Probe",root,ShaderPackDescriptor.Kind.DIRECTORY)).world().variant("shaders");
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertTrue(p.nativeTerrain().usesShadowDepth());assertTrue(p.nativeTerrain().usesShadowColor());
    }
    @Test void gpuComparisonDiagnosticPreparesBothComparisonCallForms(
        @org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        var original=Path.of(getClass().getClassLoader().getResource("shadow-depth-read/shaders").toURI());
        var shaders=Files.createDirectories(root.resolve("shaders"));
        try(var files=Files.list(original)) {
            for(Path file:files.toList()) Files.copy(file,shaders.resolve(file.getFileName()));
        }
        var fragment=shaders.resolve("gbuffers_terrain.fsh");
        Files.writeString(fragment,Files.readString(fragment).replace("#define COMPARE_PROBE 0","#define COMPARE_PROBE 1"));
        var p=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("compare","Compare",root,ShaderPackDescriptor.Kind.DIRECTORY)).world().variant("shaders");
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertTrue(p.nativeTerrain().fragmentSource().contains("oculusNativeShadowCompareVector(OculusShadowDepth,"));
        assertTrue(p.nativeTerrain().fragmentSource().contains("oculusNativeShadowCompare(OculusShadowDepth,"));
    }
    private static final String V="void main(){gl_Position=ftransform();}";
    private static String fragment(String type,String expression) {
        return "uniform "+type+" shadowtex0;\nvoid main(){gl_FragColor="+expression+";}";
    }
    @Test void rawDepthReadsConvertExactlyOnceAndBindDistinctTexture() throws Exception {
        for(String call:List.of("texture2D(shadowtex0,vec2(.5))","texture(shadowtex0,vec2(.5))",
            "texture2DLod(shadowtex0,vec2(.5),0.0)","textureLod(shadowtex0,vec2(.5),0.0)",
            "texelFetch(shadowtex0,ivec2(0),0)")) {
            for(boolean extended:new boolean[]{false,true}) {
                var p=NativeTerrainProgram.adaptWithMesh(V,fragment("sampler2D",call),List.of(0),false,extended);
                assertTrue(p.usesShadowDepth());assertTrue(p.usesShadowTextures());
                assertFalse(p.usesShadowColor());assertFalse(p.usesNoise());
                assertTrue(p.fragmentSource().contains("value.r = 1.0 - value.r"));
                assertFalse(p.fragmentSource().contains("oculus_textureDepth("));
                assertFalse(p.fragmentSource().contains("shadowtex0"));
            }
        }
    }
    @Test void legacyCompareIsVectorAndModernCompareIsScalar() throws Exception {
        var legacy=NativeTerrainProgram.adapt(V,fragment("sampler2DShadow",
            "shadow2D(shadowtex0,vec3(.5))"),List.of(0));
        assertFalse(legacy.fragmentSource().contains("gl_"));
        assertTrue(legacy.fragmentSource().contains("oculusNativeShadowCompareVector(OculusShadowDepth,"));
        var modern=NativeTerrainProgram.adapt(V,fragment("sampler2DShadow",
            "vec4(texture(shadowtex0,vec3(.5)))"),List.of(0));
        assertTrue(modern.fragmentSource().contains("vec4(oculusNativeShadowCompare(OculusShadowDepth,"));
        assertTrue(modern.fragmentSource().contains("float stored = 1.0 - texture(image, coordinate.xy).r"));
        assertTrue(modern.fragmentSource().contains("return step(coordinate.z, stored)"));
        assertFalse(modern.fragmentSource().contains("uniform sampler2DShadow"));
    }
    @Test void unusedDepthDoesNotRequireProducerOrBinding() throws Exception {
        for(String type:List.of("sampler2D","sampler2DShadow")) {
            var p=NativeTerrainProgram.adapt(V,fragment(type,"vec4(1)"),List.of(0));
            assertFalse(p.usesShadowTextures());
            assertFalse(p.fragmentSource().contains("OculusShadowDepth"));
            var shadow=NativeTerrainProgram.adaptShadow(V,fragment(type,"vec4(1)"),List.of(0));
            assertFalse(shadow.usesShadowTextures());
        }
    }
    @Test void feedbackUnsupportedAliasesAndMalformedDeclarationsFailClosed() {
        String raw=fragment("sampler2D","texture2D(shadowtex0,vec2(.5))");
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,raw,List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,raw.replace("shadowtex0","shadowtex1"),List.of(0)));
        for(String source:List.of(raw.replace("shadowtex0","watershadow"),
            raw.replace("shadowtex0","OculusShadowDepth"),raw.replace("shadowtex0;","shadowtex0[2];"),
            "uniform sampler2D shadowtex0;\n"+raw,
            raw.replace("sampler2D","samplerCube"),
            fragment("sampler2DShadow","textureLod(shadowtex0,vec3(.5),0.0)")))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,source,List.of(0)),source);
    }

    @Test void opaqueRawSnapshotUsesIndependentBindingAndSingleConversion() throws Exception {
        var p=NativeTerrainProgram.adapt(V,fragment("sampler2D","texture2D(shadowtex0,vec2(.5))")
            .replace("shadowtex0","shadowtex1"),List.of(0));
        assertTrue(p.usesOpaqueShadowDepth());assertTrue(p.usesShadowTextures());
        assertFalse(p.usesShadowDepth());
        assertTrue(p.fragmentSource().contains("oculusNativeShadowSample(OculusOpaqueShadowDepth,"));
        assertFalse(p.fragmentSource().contains("oculus_textureDepth(OculusOpaqueShadowDepth,"));
    }

    @Test void mixedRawAndComparisonSnapshotsShareHelpersButNotBindings() throws Exception {
        for(boolean opaqueCompare:new boolean[]{false,true}) {
            String raw=opaqueCompare?"shadowtex0":"shadowtex1";
            String compare=opaqueCompare?"shadowtex1":"shadowtex0";
            var p=NativeTerrainProgram.adapt(V,"uniform sampler2D "+raw+";\n"
                +"uniform sampler2DShadow "+compare+";\n"
                +"void main(){gl_FragColor=texture2D("+raw+",vec2(.5))+vec4(texture("+compare+",vec3(.5)));}",List.of(0));
            assertTrue(p.usesShadowDepth());assertTrue(p.usesOpaqueShadowDepth());
            assertEquals(1,p.fragmentSource().split("float oculusNativeShadowCompare\\(",-1).length-1);
            assertEquals(1,p.fragmentSource().split("vec4 oculusNativeShadowReverse\\(",-1).length-1);
        }
    }
    @Test void helperArgumentsAndOtherSamplerUsesAreNotSilentlyReinterpreted() {
        for(String type:List.of("sampler2D","sampler2DShadow")) {
            String source="uniform "+type+" shadowtex0;\n"
                +"vec4 sampleHelper("+type+" image){return vec4(1);}\n"
                +"void main(){gl_FragColor=sampleHelper(shadowtex0);}";
            var error=assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,source,List.of(0)));
            assertTrue(error.getMessage().contains("direct sampling"));
        }
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
            fragment("sampler2D","vec4(textureSize(shadowtex0,0),0,1)"),List.of(0)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"shadowtex0","shadowtex1"})
    void fullPackRequiresSupportedNativeProducer(String sampler,@org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        var shaders=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"),V);
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),fragment("sampler2D","texture2D(shadowtex0,vec2(.5))").replace("shadowtex0",sampler));
        Files.writeString(shaders.resolve("final.fsh"),"void main(){gl_FragColor=vec4(1);}");
        var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        var pack=new ShaderPackDescriptor("depth","Depth",root,ShaderPackDescriptor.Kind.DIRECTORY);
        var p=compiler.prepare(pack).world().variant("shaders");
        assertNull(p.nativeTerrain());assertTrue(p.nativeTerrainIssue().contains("supported native shadow producer"));
        Files.writeString(shaders.resolve("shadow.vsh"),V);
        Files.writeString(shaders.resolve("shadow.fsh"),"uniform float unknown;\nvoid main(){gl_FragColor=vec4(unknown);}");
        p=compiler.prepare(pack).world().variant("shaders");
        assertNull(p.nativeTerrain());assertNull(p.nativeShadow());
        Files.writeString(shaders.resolve("shadow.fsh"),"void main(){gl_FragColor=vec4(1);}");
        p=compiler.prepare(pack).world().variant("shaders");
        assertNotNull(p.nativeTerrain(),p.nativeTerrainIssue());assertNotNull(p.nativeShadow(),p.nativeShadowIssue());
        assertTrue(p.nativeTerrain().usesShadowTextures());
        assertEquals(sampler.equals("shadowtex0"),p.nativeTerrain().usesShadowDepth());
        assertEquals(sampler.equals("shadowtex1"),p.nativeTerrain().usesOpaqueShadowDepth());
        assertFalse(p.nativeShadow().usesShadowTextures());
    }
    @Test void reservedHelperIdentifiersAndVertexSamplersStayRejected() {
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
            "float oculusNativeShadowSample;\nvoid main(){gl_FragColor=vec4(1);}",List.of(0)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(
            "uniform sampler2D shadowtex0;\n"+V.replace("ftransform()","ftransform()+texture2D(shadowtex0,vec2(.5))"),"void main(){gl_FragColor=vec4(1);}",List.of(0)));
    }
}
