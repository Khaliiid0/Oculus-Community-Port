package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeDepthArgumentHelpersTest {
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String HELPER="vec4 reflectDepth(vec3 a,vec3 b,vec3 c,vec3 d,float distance,float z0,"
        +"sampler2D image,vec2 uv,out float border,int steps){border=0.;for(int i=0;i<steps;i++)border+=texture2D(image,uv).r;return vec4(border/float(steps));}\n";
    private static String call(String sampler) {
        return "reflectDepth(vec3(0),vec3(sin(1.),2.,3.),vec3(0),vec3(0),length(vec3(1)),0.,"+sampler+",vec2(.5),b,2)";
    }
    private static String shader(String helper,String call) {
        return "uniform sampler2D depthtex1;\n"+helper+"void main(){float b;gl_FragColor="+call+";}";
    }

    @Test void gpuFixtureRetainsSeventhArgumentDepthAndUnmodifiedNoiseWithIndependentPostDepth() throws Exception {
        var path=java.nio.file.Path.of(getClass().getClassLoader().getResource("native-depth-arguments").toURI());
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("arguments","Arguments",path,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertTrue(world.layerRuntimeEligible());
        var water=world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram();
        assertTrue(water.usesOpaqueWorldDepth());assertTrue(water.usesNoise());
        assertTrue(water.fragmentSource().contains("0.,OculusOpaqueWorldDepth,uv,sampled,2)"));
        assertTrue(water.fragmentSource().contains("0.,OculusNoise,noiseUV,sampled,2)"));
        assertEquals(Set.of(0,1),prepared.programs().getLast().fragment().samplers().stream()
            .filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.DEPTH)
            .map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void specializesTheSeventhSamplerArgumentWithoutChangingOtherArguments() throws Exception {
        var p=NativeTerrainProgram.adapt(V,shader(HELPER,call("depthtex1")),List.of(0));
        assertTrue(p.usesOpaqueWorldDepth());
        assertTrue(p.fragmentSource().contains("oculusNativeWorldDepthHelper0(vec3(0),vec3(sin(1.),2.,3.)"));
        assertTrue(p.fragmentSource().contains("length(vec3(1)),0.,OculusOpaqueWorldDepth,vec2(.5),b,2)"));
        assertTrue(p.fragmentSource().contains("border+=oculusNativeWorldDepthSample(image,uv).r"));
        assertEquals(1,p.fragmentSource().split("1.0-value.r",-1).length-1);
    }

    @Test void preservesTheSameHelperForOtherTexturesAndAllDirectSampleForms() throws Exception {
        for(String sample:List.of("texture2D(image,uv)","texture(image,uv)","textureLod(image,uv,0.)",
            "texture2DLod(image,uv,0.)","texelFetch(image,ivec2(uv),0)")) {
            String source=shader(HELPER.replace("texture2D(image,uv)",sample),call("depthtex1")+"+"+call("gaux2"));
            source="uniform sampler2D gaux2;\n"+source;
            var p=NativeTerrainProgram.adapt(V,source,List.of(0));
            assertEquals(List.of(5),p.colorInputs());
            assertTrue(p.fragmentSource().contains("reflectDepth(vec3(0),vec3(sin(1.),2.,3.)"));
            assertTrue(p.fragmentSource().contains("0.,OculusColor5,vec2(.5),b,2)"));
            assertTrue(p.fragmentSource().contains(sample.replace("texture2DLod(","textureLod(").replace("texture2D(","texture("))
                ||p.fragmentSource().contains(sample),sample);
        }
    }

    @Test void supportsSamplerOnlyLastAndQualifiedParametersWithoutFabricatingInputs() throws Exception {
        for(String helper:List.of("vec4 leaf(sampler2D image){return texture2D(image,vec2(.5));}",
            "vec4 leaf(const in float a,in sampler2D image){return texture2D(image,vec2(a));}")) {
            String call=helper.contains("float a")?"leaf(.5,depthtex1)":"leaf(depthtex1)";
            var p=NativeTerrainProgram.adapt(V,shader(helper,call),List.of(0));
            assertTrue(p.usesOpaqueWorldDepth());
            assertTrue(p.fragmentSource().contains(helper.contains("float a")?"oculusNativeWorldDepthHelper0(.5,OculusOpaqueWorldDepth)"
                :"oculusNativeWorldDepthHelper0(OculusOpaqueWorldDepth)"));
        }
    }

    @Test void rejectsWrongSamplerPositionsArityAndMultipleDepthArguments() {
        for(String call:List.of(call("depthtex1").replace("0.,depthtex1","depthtex1,0."),
            call("depthtex1").replace(",b,2)",",b)"),call("depthtex1").replace(",b,2)",",b,2,0)"),
            call("depthtex1").replace("length(vec3(1))","depthtex1")))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,shader(HELPER,call),List.of(0)),call);
    }

    @Test void rejectsMultipleSamplersForwardingUnsupportedUsesAndAmbiguousSignatures() {
        for(String helper:List.of(HELPER.replace("vec2 uv","sampler2D second"),
            HELPER.replace("sampler2D image","out sampler2D image"),
            HELPER.replace("vec3 b","vec3 a"),HELPER.replace("sampler2D image","sampler2D depthtex1"),
            HELPER.replace("texture2D(image,uv)","nested(uv,image)"),
            HELPER.replace("texture2D(image,uv)","vec4(textureSize(image,0),0,1)"),
            HELPER+"vec4 reflectDepth(float x){return vec4(x);}",
            HELPER+"vec4 reflectDepth(vec3 a,vec3 b,vec3 c,vec3 d,float distance,float z0,sampler2D image,vec2 uv,out float border,int steps);"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,shader(helper,call("depthtex1")),List.of(0)));
    }

    @Test void nestedDirectReadsAreConvertedAtEachDistinctReadSiteOnly() throws Exception {
        String source="uniform sampler2D depthtex1;void main(){gl_FragColor=texture2D(depthtex1,vec2(texture2D(depthtex1,vec2(.5)).r));}";
        var adapted=NativeOpaqueDepth.adapt(source,false);
        assertTrue(adapted.contains("oculusNativeWorldDepthSample(OculusOpaqueWorldDepth,vec2(oculusNativeWorldDepthSample(OculusOpaqueWorldDepth,vec2(.5)).r))"));
        assertEquals(1,adapted.split("1.0-value.r",-1).length-1);
    }

    @Test void nonSamplerUsesStillRejectAndShadowCannotReadWorldDepth() {
        for(String expression:List.of("texture2D(depthtex1)","textureLod(depthtex1,vec2(.5))",
            "texture2D(depthtex1,vec2(.5),0.,1.)","texture2D((depthtex1),vec2(.5))","unknown(vec2(.5),depthtex1)"))
            assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepth.adapt(shader("",expression),false));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,shader(HELPER,call("depthtex1")),List.of(0)));
    }

    @Test void boundsTheNumberOfSpecializationsAndExpandedSource() {
        var helpers=new StringBuilder();var calls=new StringBuilder("vec4(0)");
        for(int i=0;i<17;i++) {
            helpers.append("vec4 leaf").append(i).append("(float a,sampler2D image){return texture2D(image,vec2(a));}\n");
            calls.append("+leaf").append(i).append("(.5,depthtex1)");
        }
        assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepth.adapt(shader(helpers.toString(),calls.toString()),false));
        String huge="vec4 leaf(float a,sampler2D image){"+" ".repeat(GlslCallArguments.MAX_SOURCE/2)
            +"return texture2D(image,vec2(a));}";
        assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepth.adapt(shader(huge,"leaf(.5,depthtex1)"),false));
    }

    @Test void boundsTheHelperDeclarationIndexEvenWhenManyHelpersAreNotDepthCallers() {
        var source=new StringBuilder();
        for(int i=0;i<33;i++) source.append("vec4 helper").append(i).append("(sampler2D image){return texture2D(image,vec2(.5));}\n");
        assertThrows(ShaderCompatibilityException.class,()->NativeOpaqueDepthHelpers.adapt(
            source+"void main(){gl_FragColor=texture2D(depthtex1,vec2(.5));}"));
    }
}
