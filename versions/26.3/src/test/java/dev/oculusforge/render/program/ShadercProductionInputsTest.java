package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.port.ShadercInterfacePair;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.util.shaderc.Shaderc;

/** Runs real ShaderC on actual prepared inputs, without RenderSystem or a GPU context. */
class ShadercProductionInputsTest {
    @Test void wetnessTransitionOracleAndBothFaultControlsCompileExactPreparedSources() throws Exception {
        var root=Path.of(java.util.Objects.requireNonNull(getClass().getClassLoader().getResource("wetness")).toURI());
        var descriptor=new ShaderPackDescriptor("wetness","Wetness",root,ShaderPackDescriptor.Kind.DIRECTORY);
        for(String mode:java.util.List.of("0","1","2")) {
            var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(descriptor,MaterialFormat.DEFAULT,
                java.util.Map.of("NEGATIVE_MODE",mode));
            assertEquals(new WetnessSettings(10,20),prepared.variants().get("shaders").wetnessSettings());
            var world=prepared.world().variant("shaders");
            assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
            assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
            for(var program:world.nativePrograms())for(boolean indirect:java.util.List.of(false,true)) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            for(var program:prepared.programs()) {
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }
    @Test void sharedWetnessCompilesForNativeMeshesShadowAndBothPostStages() throws Exception {
        String vertex="uniform float wetness;\nvarying float fromVertex;\n"
            +"void main(){fromVertex=wetness;gl_Position=ftransform();}";
        String fragment="uniform float wetness;\nvarying float fromVertex;\n"
            +"float puddles(vec3 pos, float wetness){return wetness*pos.x;}\n"
            +"void main(){gl_FragColor=vec4(puddles(vec3(1),wetness),fromVertex,0,1);}";
        for(boolean shadow:java.util.List.of(false,true))for(boolean extended:java.util.List.of(false,true)) {
            var nativeProgram=NativeTerrainProgram.adaptWithMesh(vertex,fragment,java.util.List.of(0),shadow,extended);
            for(boolean indirect:java.util.List.of(false,true)) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(nativeProgram.vertexSource(),indirect),nativeProgram.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
        var postVertex=new LegacyFinalVertexAdapter().adapt(vertex);
        var postFragment=new LegacyFinalShaderAdapter().adapt(fragment,postVertex.outputs(),java.util.Map.of());
        assertEquals(LegacyFinalShaderAdapter.UniformSemantic.WETNESS,postVertex.uniforms().getFirst().semantic());
        var pair=ShadercInterfacePair.adapt(
            LegacyFinalShaderAdapter.withUniformBlock(postVertex.source(),postFragment.uniforms()),postFragment.source());
        compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
    }
    @Test void multilineMaterialInoutParametersCompileOnBothNativeBackends() throws Exception {
        String fragment="void material(out float rough,\n                  inout float emission,\n                  inout float subsurface, out vec3 normalMap)"
            +"{rough=.5;emission+=.1;subsurface+=.2;normalMap=vec3(0,0,1);}\n"
            +"void main(){float r=0.,e=0.,s=0.;vec3 n;material(r,e,s,n);gl_FragColor=vec4(n*r+e+s,1);}";
        var program=NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",fragment,java.util.List.of(0));
        for(boolean indirect:java.util.List.of(false,true)) {
            var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
            compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
        }
    }
    @Test void materialFormatControlsCompileAllThreeResourceContexts() throws Exception {
        for(String fixture:java.util.List.of("material-format","material-format-negative"))for(var format:MaterialFormat.values()) {
            var root=Path.of(java.util.Objects.requireNonNull(getClass().getClassLoader().getResource(fixture)).toURI());
            var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT,format).prepare(new ShaderPackDescriptor(fixture,fixture,root,ShaderPackDescriptor.Kind.DIRECTORY));
            var world=prepared.world().variant("shaders");
            assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
            for(var nativeProgram:java.util.List.of(world.nativeTerrain(),world.nativeShadow())) {
                assertEquals(format!=MaterialFormat.DEFAULT,nativeProgram.usesNormals());
                for(boolean indirect:java.util.List.of(false,true)) {
                    var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(nativeProgram.vertexSource(),indirect),nativeProgram.fragmentSource());
                    compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
                }
            }
            for(var program:prepared.programs()) {
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }
    @Test void normalAndSpecularInputsCompileTogetherForBothMeshesAndShadow() throws Exception {
        String fragment="uniform sampler2D normals;\nuniform sampler2D specular;\nvoid main(){gl_FragColor=texture2DLod(normals,vec2(.5),1.0)+texture2D(specular,vec2(.5));}";
        for(boolean shadow:java.util.List.of(false,true))for(boolean extended:java.util.List.of(false,true)) {
            var program=NativeTerrainProgram.adaptWithMesh("void main(){gl_Position=ftransform();}",fragment,java.util.List.of(0),shadow,extended);
            assertTrue(program.usesNormals());assertTrue(program.usesSpecular());
            for(boolean indirect:java.util.List.of(false,true)) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }
    @Test void normalAtlasControlsCompileTheActualTerrainShadowAndPostSources() throws Exception {
        for(String fixture:java.util.List.of("native-normals","native-normals-default","native-normals-negative")) {
            var root=Path.of(java.util.Objects.requireNonNull(getClass().getClassLoader().getResource(fixture)).toURI());
            var prepared=prepare(root);var world=prepared.world().variant("shaders");
            assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
            for(var nativeProgram:java.util.List.of(world.nativeTerrain(),world.nativeShadow())) {
                assertTrue(nativeProgram.usesNormals());assertTrue(nativeProgram.usesSpecular());
                for(boolean indirect:java.util.List.of(false,true)) {
                    var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(nativeProgram.vertexSource(),indirect),nativeProgram.fragmentSource());
                    compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
                }
            }
            for(var program:prepared.programs()) {
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }
    @Test void exactGpuCenterDepthKernelCompilesWithProductionOptions() throws Exception {
        var fragment=dev.oculusforge.render.pipeline.CenterDepthPass.class.getDeclaredField("FRAGMENT_SOURCE");
        fragment.setAccessible(true);
        var pair=ShadercInterfacePair.adapt(LegacyFinalVertexAdapter.DEFAULT_SOURCE,(String)fragment.get(null));
        compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
    }

    @Test void centerDepthControlsCompileVertexAndFragmentGpuSampling(@TempDir Path root) throws Exception {
        for(String fixture:java.util.List.of("center-depth","center-depth-native","center-depth-negative","center-depth-smooth","center-depth-smooth-negative")) {
            var shaders=Files.createDirectories(root.resolve(fixture).resolve("shaders"));
            for(String name:java.util.List.of("final.vsh","final.fsh","composite.fsh","gbuffers_terrain.vsh","gbuffers_terrain.fsh"))
                try(var input=getClass().getClassLoader().getResourceAsStream(fixture+"/shaders/"+name)) {
                    if(input!=null)Files.copy(input,shaders.resolve(name));
                }
            var prepared=prepare(shaders.getParent());var program=prepared.programs().getFirst();
            assertEquals(1,program.fragment().samplers().stream().filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.CENTER_DEPTH).count());
            assertFalse(program.fragment().uniforms().stream().anyMatch(u->u.semantic()==LegacyFinalShaderAdapter.UniformSemantic.CENTER_DEPTH_SMOOTH));
            assertEquals(fixture.endsWith("smooth")?10:0,CenterDepthHistory.halfLife(java.util.List.of(program.vertexSource(),program.fragment().source())));
            for(var stage:prepared.programs()) {
                var pair=ShadercInterfacePair.adapt(stage.vertexSource(),stage.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            if(fixture.endsWith("native")||fixture.contains("-smooth"))assertNotNull(prepared.world().variant("shaders").nativeTerrain());
            else assertNull(prepared.world().variant("shaders"));
        }
    }
    private static final String V="""
        #version 120
        varying vec2 uv;
        void main(){uv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}
        """;
    private static final String F="""
        #version 120
        varying vec2 uv;
        uniform sampler2D colortex0;
        uniform float viewWidth;
        void main(){gl_FragColor=texture2D(colortex0,uv)*vec4(viewWidth/800.0);}
        """;

    @Test void realCompilerAcceptsPreparedFullscreenSamplerAndUniformBlock(@TempDir Path root) throws Exception {
        Path shaders=Files.createDirectory(root.resolve("shaders"));
        Files.writeString(shaders.resolve("final.vsh"),V);Files.writeString(shaders.resolve("final.fsh"),F);
        var prepared=prepare(root);var program=prepared.programs().getFirst();
        assertFalse(program.fragment().uniforms().isEmpty());assertFalse(program.fragment().samplers().isEmpty());
        var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
        compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
    }

    @Test void realCompilerAcceptsPreparedBasicTerrainAndShadowStages(@TempDir Path root) throws Exception {
        Path shaders=Files.createDirectory(root.resolve("shaders"));
        for(String stage:java.util.List.of("gbuffers_terrain","shadow")) {
            Files.writeString(shaders.resolve(stage+".vsh"),V);
            Files.writeString(shaders.resolve(stage+".fsh"),
                "varying vec2 uv; uniform sampler2D texture; void main(){gl_FragColor=texture2D(texture,uv);}");
        }
        Files.writeString(shaders.resolve("final.fsh"),"void main(){gl_FragColor=vec4(1);}");
        var world=prepare(root).world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        for(var program:java.util.List.of(world.nativeTerrain(),world.nativeShadow())) {
            assertTrue(program.fragmentSource().contains("uniform sampler2D Sampler0;"));
            assertFalse(program.fragmentSource().contains("uniform sampler2D texture;"));
            for(boolean indirect:new boolean[]{false,true}) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),
                    program.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }

    @Test void realCompilerAcceptsSpecializedNativeEndSky() throws Exception {
        var program=NativeSkyProgram.adapt(V,"""
            varying vec2 uv;
            uniform sampler2D texture;
            uniform int renderStage;
            void main(){gl_FragColor=texture2D(texture,uv)*float(renderStage);}
            """,java.util.List.of(0));
        assertTrue(SkyRenderStages.endSkyEligible(program));
        var pair=ShadercInterfacePair.adapt(SkyRenderStages.specializeEndSky(program.vertex().source()),
            SkyRenderStages.specializeEndSky(program.fragmentSource()));
        compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
    }

    @Test void realCompilerAcceptsProductionPresentationTransfer() throws Exception {
        // Read the exact private production literal, without adding a test-only runtime API.
        var fragment=FullscreenColorTransfer.class.getDeclaredField("FRAGMENT_SOURCE");fragment.setAccessible(true);
        var pair=ShadercInterfacePair.adapt(LegacyFinalVertexAdapter.DEFAULT_SOURCE,(String)fragment.get(null));
        compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
    }

    @Test void normalViewRuntimeFixtureRemainsARealNativePairWithoutTheGreenMultiplier(@TempDir Path root) throws Exception {
        var shaders=Files.createDirectory(root.resolve("shaders"));
        for(String name:java.util.List.of("gbuffers_terrain.vsh","gbuffers_terrain.fsh","shadow.vsh","shadow.fsh","final.fsh"))
            try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("native-normal/shaders/"+name))) {
                Files.copy(input,shaders.resolve(name));
            }
        assertFalse(Files.readString(shaders.resolve("gbuffers_terrain.fsh")).contains("vec3(0.25, 1.0, 0.25)"));
        var prepared=prepare(root);var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        for(var program:java.util.List.of(world.nativeTerrain(),world.nativeShadow()))for(boolean indirect:new boolean[]{false,true}) {
            var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
            compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
        }
        for(var program:prepared.programs()) {
            var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
            compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
        }
    }

    @Test void qualificationOraclesPreserveExtendedAdmissionAndCompileTheirSupportedStages(@TempDir Path root) throws Exception {
        var fingerprints=new java.util.HashMap<String,String>();
        for(String fixture:java.util.List.of("extended-normal","extended-negative","hdr-scene","hdr-negative","hdr-inspect","hdr-localize","native-normal-hdr")) {
            var pack=Files.createDirectory(root.resolve(fixture));var shaders=Files.createDirectory(pack.resolve("shaders"));
            for(String name:java.util.List.of("gbuffers_terrain.vsh","gbuffers_terrain.fsh","shadow.vsh","shadow.fsh","final.vsh","final.fsh","block.properties"))
                try(var input=getClass().getClassLoader().getResourceAsStream(fixture+"/shaders/"+name)) {
                    if(input!=null)Files.copy(input,shaders.resolve(name));
                }
            var prepared=prepare(pack);var world=prepared.world().variant("shaders");
            boolean extended=fixture.startsWith("extended-");
            if(extended&&!TerrainMeshMode.EXTENDED) {
                assertNull(world.nativeTerrain(),"Extended inputs must not be admitted against BLOCK vertices");
                assertFalse(world.nativeTerrainIssue().isBlank());
            } else {
                assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
                fingerprints.put(fixture,world.nativeTerrain().fingerprint());
                var programs=new java.util.ArrayList<NativeTerrainProgram>();programs.add(world.nativeTerrain());
                if(extended) {
                    assertTrue(world.nativeTerrain().usesMaterialIds());
                    assertNotNull(world.nativeShadow(),world.nativeShadowIssue());programs.add(world.nativeShadow());
                } else assertEquals(fixture.equals("native-normal-hdr")?ColorBufferFormat.R11F_G11F_B10F:ColorBufferFormat.RGBA16F,world.formats().get(0));
                for(var program:programs)for(boolean indirect:new boolean[]{false,true}) {
                    var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
                    compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
                }
            }
            for(var program:prepared.programs()) {
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
        assertNotEquals(fingerprints.get("hdr-scene"),fingerprints.get("hdr-negative"),
            "The positive and deliberately altered terrain must not share a descriptor identity");
        if(TerrainMeshMode.EXTENDED)
            assertNotEquals(fingerprints.get("extended-normal"),fingerprints.get("extended-negative"));
    }

    @Test void deferredDistanceControlsCompileNativeWaterAndPreserveThePhaseBoundary(@TempDir Path root) throws Exception {
        var producerSources=new java.util.ArrayList<String>();
        for(String fixture:java.util.List.of("deferred-distance","deferred-distance-negative")) {
            var pack=Files.createDirectory(root.resolve(fixture));var shaders=Files.createDirectory(pack.resolve("shaders"));
            for(String name:java.util.List.of("gbuffers_terrain.vsh","gbuffers_terrain.fsh","gbuffers_water.vsh",
                "gbuffers_water.fsh","deferred.fsh","final.fsh","shaders.properties"))
                try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(fixture+"/shaders/"+name))) {
                    Files.copy(input,shaders.resolve(name));
                }
            var prepared=prepare(pack);var world=prepared.world().variant("shaders");
            var water=world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram();
            assertNotNull(water);
            var programs=prepared.variants().get("shaders").programs();
            var phases=WorldPostPhasePlan.create(programs,world.initialColorBuffers(),programs.getFirst().formats());
            assertEquals(1,phases.deferredCount());
            assertTrue(phases.deferred().retainedColorSlots().containsKey(4));
            assertEquals(ColorBufferFormat.RGBA16F,world.formats().get(4));
            for(boolean indirect:new boolean[]{false,true}) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(water.vertexSource(),indirect),water.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            for(var program:programs) {
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            producerSources.add(programs.getFirst().fragment().source());
        }
        assertNotEquals(producerSources.get(0),producerSources.get(1));
    }

    @Test void deferredFaultControlsUseIdenticalHdrShadersAndTwoScratchSlots(@TempDir Path root) throws Exception {
        var pack=Files.createDirectory(root.resolve("healthy"));
        var shaders=Files.createDirectory(pack.resolve("shaders"));
        for(String name:java.util.List.of("gbuffers_terrain.vsh","gbuffers_terrain.fsh","gbuffers_water.vsh",
            "gbuffers_water.fsh","deferred.fsh","final.fsh","shaders.properties")) {
            byte[] expected;
            try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("fault-healthy/shaders/"+name))) {
                expected=input.readAllBytes();
            }
            Files.write(shaders.resolve(name),expected);
            for(String point:java.util.List.of("allocate","draw","precommit","copy"))
                try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("fault-"+point+"/shaders/"+name))) {
                    assertArrayEquals(expected,input.readAllBytes(),point+" must change the fault point, not the shader");
                }
        }
        var prepared=prepare(pack);var world=prepared.world().variant("shaders");
        assertNotNull(world.terrainLayers().program(TerrainLayerPlan.Layer.SOLID).nativeProgram());
        assertNotNull(world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram());
        assertEquals(ColorBufferFormat.R11F_G11F_B10F,world.formats().get(0));
        assertEquals(ColorBufferFormat.RGBA16F,world.formats().get(4));
        var programs=prepared.variants().get("shaders").programs();
        var phases=WorldPostPhasePlan.create(programs,world.initialColorBuffers(),programs.getFirst().formats());
        assertEquals(1,phases.deferredCount());
        assertEquals(2,phases.deferred().scratchFormats().size());
        assertTrue(phases.deferred().retainedColorSlots().containsKey(4));
        assertNotEquals(ColorBufferPlan.MAIN,phases.deferred().presentedSlot());
        for(var program:programs) {
            var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
            compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
        }
    }

    @Test void depthSnapshotControlsCompileBothWaterBackendsAndKeepSignedFloatMarkers(@TempDir Path root) throws Exception {
        String waterSource=null,positivePredicate=null;
        for(String fixture:java.util.List.of("depth-snapshots","depth-snapshots-alias-negative","depth-snapshots-reconstruction-negative")) {
            var pack=Files.createDirectory(root.resolve(fixture));var shaders=Files.createDirectory(pack.resolve("shaders"));
            for(String name:java.util.List.of("gbuffers_terrain.vsh","gbuffers_terrain.fsh","gbuffers_water.vsh",
                "gbuffers_water.fsh","final.fsh","shaders.properties"))
                try(var input=java.util.Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(fixture+"/shaders/"+name))) {
                    Files.copy(input,shaders.resolve(name));
                }
            String current=Files.readString(shaders.resolve("gbuffers_water.fsh"));
            if(waterSource==null)waterSource=current;else assertEquals(waterSource,current,"Only the post predicate changes");
            String predicate=PostProcessCapabilities.withoutComments(Files.readString(shaders.resolve("final.fsh"))).replaceAll("\\s+","");
            if(positivePredicate==null)positivePredicate=predicate;
            else assertEquals(positivePredicate,predicate.replace("preHand=texelFetch(depthtex0,","preHand=texelFetch(depthtex2,")
                .replace("surfaceDistance*1.25","surfaceDistance"),"Negative controls must not change the separation/coverage predicate");
            var prepared=prepare(pack);var world=prepared.world().variant("shaders");
            var water=world.terrainLayers().program(TerrainLayerPlan.Layer.TRANSLUCENT).nativeProgram();
            assertNotNull(water);assertTrue(water.usesOpaqueWorldDepth());
            assertEquals(ColorBufferFormat.RGBA16F,world.formats().get(0));
            assertEquals(ColorBufferFormat.RGBA32F,world.formats().get(4));
            assertTrue(current.contains("-distanceAt(screen,gl_FragCoord.z)"));
            for(boolean indirect:new boolean[]{false,true}) {
                var pair=ShadercInterfacePair.adapt(dev.oculusforge.port.TerrainShaderAbi.vertex(water.vertexSource(),indirect),water.fragmentSource());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
            for(var program:prepared.programs()) {
                var indices=program.fragment().samplers().stream()
                    .filter(s->s.texture()==LegacyFinalShaderAdapter.TextureSemantic.DEPTH)
                    .map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).collect(java.util.stream.Collectors.toSet());
                assertTrue(indices.contains(0)&&indices.contains(1));
                assertEquals(!fixture.endsWith("alias-negative"),indices.contains(2));
                var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragment().source());
                compile(pair.vertex(),Shaderc.shaderc_vertex_shader);compile(pair.fragment(),Shaderc.shaderc_fragment_shader);
            }
        }
    }

    @Test void hdrOraclesSampleTexelCentersRatherThanFilteredEdges() throws Exception {
        for(String fixture:java.util.List.of("hdr-scene","hdr-negative")) {
            try(var input=getClass().getClassLoader().getResourceAsStream(fixture+"/shaders/final.fsh")) {
                assertNotNull(input);
                String source=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(source.contains("uniform float viewWidth;"));
                assertTrue(source.contains("uniform float viewHeight;"));
                assertTrue(source.contains("floor((vec2(x,y)+.5)*size/16.)+.5"));
                assertTrue(source.contains("texture2D(colortex0,pixel/size)"));
            }
        }
        // The old 960-wide grid landed exactly BETWEEN adjacent texels:
        // a valid HDR marker next to unrelated scene color became an invalid
        // interpolated marker. This is not evidence of clamping or stale shaders.
        float originalUv=.5f/16;
        assertEquals(29.5f,originalUv*960-.5f);
        float[] terrain={-.5f,2,4,1.5f}, neighbor={0,0,0,1};
        float[] mixed=new float[4];
        for(int c=0;c<4;c++) mixed[c]=(terrain[c]+neighbor[c])*.5f;
        assertTrue(mixed[0]<.1f&&mixed[1]>.9f&&mixed[2]>.9f&&mixed[3]>.9f);
        assertTrue(Math.abs(mixed[1]-terrain[1])>.01f);
        // Check the GLSL float-coordinate rule for windowed, fullscreen, odd
        // and tiny extents. Reconstructing a texel index must have no blend weight.
        for(int extent:new int[]{1,7,16,480,540,854,960,1080,1920})
            for(int sample=0;sample<16;sample++) {
                float pixel=(float)Math.floor((sample+.5f)*extent/16)+.5f;
                float index=(pixel/extent)*extent-.5f;
                assertEquals(Math.rint(index),index,.0001);
                assertTrue(index>=0&&index<extent);
            }
    }

    private PreparedPackPipeline prepare(Path root) throws Exception {
        return new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("shaderc","ShaderC fixture",root,ShaderPackDescriptor.Kind.DIRECTORY));
    }

    private void compile(String source,int kind) {
        compile(source,kind,false);
        compile(source,kind,true);
    }

    private void compile(String source,int kind,boolean zeroToOne) {
        long compiler=Shaderc.shaderc_compiler_initialize();assertNotEquals(0,compiler);
        long options=0,result=0;
        try {
            options=Shaderc.shaderc_compile_options_initialize();assertNotEquals(0,options);
            // Exact target/options from the bundled GlslCompiler; no RenderSystem/GPU bootstrap.
            Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
            Shaderc.shaderc_compile_options_set_preserve_bindings(options,false);
            Shaderc.shaderc_compile_options_set_generate_debug_info(options);
            Shaderc.shaderc_compile_options_set_optimization_level(options,Shaderc.shaderc_optimization_level_zero);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options,false);
            if(zeroToOne) {
                for(String define:java.util.List.of("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE",
                    "OCULUS_FRAGMENT_ZERO_TO_ONE","OCULUS_SHADOW_ZERO_TO_ONE"))
                    Shaderc.shaderc_compile_options_add_macro_definition(options,define,"");
            }
            result=Shaderc.shaderc_compile_into_spv(compiler,source,kind,"prepared-oculus.glsl","main",options);
            assertNotEquals(0,result);
            assertEquals(Shaderc.shaderc_compilation_status_success,Shaderc.shaderc_result_get_compilation_status(result),
                Shaderc.shaderc_result_get_error_message(result));
            assertEquals(0x07230203,java.util.Objects.requireNonNull(Shaderc.shaderc_result_get_bytes(result))
                .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(0));
        } finally {
            if(result!=0)Shaderc.shaderc_result_release(result);
            if(options!=0)Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
