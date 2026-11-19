package dev.oculusforge.render.world;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import dev.oculusforge.port.ShadercInterfacePair;
import dev.oculusforge.port.TerrainShaderAbi;
import dev.oculusforge.render.program.NativeTerrainProgram;
import dev.oculusforge.render.program.NativeSkyProgram;
import dev.oculusforge.render.program.SkyRenderStages;
import java.nio.*;
import java.util.*;
import net.minecraft.client.renderer.DynamicGpuData;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;

/** Real ShaderC + Minecraft SPIR-V reflection, without a graphics device or world. */
class TerrainShaderAbiTest {
    private static final String V="""
        varying vec2 uv;
        void main(){uv=gl_MultiTexCoord0.xy;gl_Position=ftransform();}
        """;
    private static final String F="""
        varying vec2 uv;
        uniform sampler2D texture;
        void main(){gl_FragColor=texture2D(texture,uv);}
        """;
    private static final Map<String,Integer> GLOBAL_OFFSETS=Map.of("CameraBlockPos",0,"GlintAlpha",12,
        "CameraOffset",16,"GameTime",28,"ScreenSize",32,"MenuBlurRadius",40,"UseRgss",44);
    record Input(int base,int size) {}
    record Layout(Map<String,Input> inputs,Map<String,Map<String,Integer>> blocks) {}

    @Test void nativeTerrainAndShadowMatchBothBackendInterfacesAndActualUboWriters() throws Exception {
        var section=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        new DynamicGpuData.ChunkSectionInfo(-32,64,160,0.75f).write(section);
        assertEquals(-32,section.getInt(0));assertEquals(64,section.getInt(4));assertEquals(160,section.getInt(8));
        assertEquals(0.75f,section.getFloat(12));
        var transform=ByteBuffer.allocateDirect(80).order(ByteOrder.nativeOrder());
        new DynamicGpuData.TerrainTransform(new Matrix4f().translation(10,20,30),2048,1024).write(transform);
        assertEquals(10,transform.getFloat(48));assertEquals(2048,transform.getInt(64));assertEquals(1024,transform.getInt(68));

        for(var program:List.of(NativeTerrainProgram.adapt(V,F,List.of(0)),NativeTerrainProgram.adaptShadow(V,F,List.of(0))))
            for(boolean indirect:new boolean[]{false,true}) {
                var pair=ShadercInterfacePair.adapt(TerrainShaderAbi.vertex(program.vertexSource(),indirect),program.fragmentSource());
                var layout=reflect(pair.vertex());
                assertEquals(GLOBAL_OFFSETS,layout.blocks().get("Globals"));
                assertEquals(Map.of("ModelViewMat",0,"TextureSize",64),layout.blocks().get("TerrainUniform"));
                assertEquals(!indirect,layout.blocks().containsKey("ChunkSection"));
                if(indirect) {
                    assertEquals(new Input(Spvc.SPVC_BASETYPE_INT32,3),layout.inputs().get("ChunkPosition"));
                    assertEquals(new Input(Spvc.SPVC_BASETYPE_FP32,1),layout.inputs().get("ChunkVisibility"));
                } else assertEquals(Map.of("ChunkPosition",0,"ChunkVisibility",12),layout.blocks().get("ChunkSection"));
                verifyBindings(layout,indirect);
            }
    }

    @Test void internalTerrainAndShadowUseTheSameQualifiedAbi() throws Exception {
        for(String field:List.of("TERRAIN_VERTEX","SHADOW_VERTEX")) {
            var constant=WorldRenderPipeline.class.getDeclaredField(field);constant.setAccessible(true);
            for(boolean indirect:new boolean[]{false,true}) {
                String prepared=TerrainShaderAbi.vertex((String)constant.get(null),indirect);
                // A matching empty fragment keeps this check about actual internal vertex literals.
                var pair=ShadercInterfacePair.adapt(prepared,"layout(location=0) out vec4 c;void main(){c=vec4(1);}");
                var layout=reflect(pair.vertex());
                assertEquals(GLOBAL_OFFSETS,layout.blocks().get("Globals"));
                assertEquals(Map.of("ModelViewMat",0,"TextureSize",64),layout.blocks().get("TerrainUniform"));
                assertEquals(!indirect,layout.blocks().containsKey("ChunkSection"));
                verifyBindings(layout,indirect);
            }
        }
    }

    @Test void backendDescriptorsKeepChunkUboAndInstancedInputsMutuallyExclusive() {
        for(boolean indirect:new boolean[]{false,true}) {
            var pipeline=descriptor(indirect);
            var uniforms=BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts()).stream()
                .map(BindGroupLayout.UniformDescription::name).collect(java.util.stream.Collectors.toSet());
            assertTrue(uniforms.containsAll(Set.of("TerrainUniform","Projection","Globals","Sampler0","Sampler2")));
            assertEquals(!indirect,uniforms.contains("ChunkSection"));
            assertSame(ExtendedTerrainMesh.format(),pipeline.getVertexFormatBinding(0));
            if(indirect) {
                assertSame(DefaultVertexFormat.CHUNK_DATA_INSTANCED,pipeline.getVertexFormatBinding(1));
                assertEquals(1,pipeline.getVertexFormatBinding(1).getStepRate());
                assertEquals(16,pipeline.getVertexFormatBinding(1).getVertexSize());
                assertEquals(0,pipeline.getVertexFormatBinding(1).getElement("ChunkPosition").offset());
                assertEquals(12,pipeline.getVertexFormatBinding(1).getElement("ChunkVisibility").offset());
            } else assertNull(pipeline.getVertexFormatBinding(1));
        }
    }

    @Test void generationLookupKeepsBackendAndDimensionFamiliesSeparate() throws Exception {
        var separate=new WorldRenderPipeline.Variant(null,descriptor(false),null,null,null);
        var indirect=new WorldRenderPipeline.Variant(null,descriptor(true),null,null,null);
        var endSeparate=new WorldRenderPipeline.Variant(null,descriptor(false),null,null,null);
        var endIndirect=new WorldRenderPipeline.Variant(null,descriptor(true),null,null,null);
        var normalMap=new HashMap<>(Map.of("shaders",separate,"shaders/world1",endSeparate));
        var indirectMap=new HashMap<>(Map.of("shaders",indirect,"shaders/world1",endIndirect));
        var constructor=WorldRenderPipeline.class.getDeclaredConstructor(Map.class,Map.class,
            dev.oculusforge.render.program.PackGpuTexture.class,dev.oculusforge.port.PipelineGeneration.class);
        constructor.setAccessible(true);
        // Empty CPU generation only; no driver/device initialization or owned GPU objects.
        try(var generation=new dev.oculusforge.port.PipelineGeneration<RenderPipeline>(8)) {
            var world=constructor.newInstance(normalMap,indirectMap,null,generation);
            normalMap.clear();indirectMap.clear();
            assertSame(separate,world.variant("shaders"));assertSame(separate,world.variant("unknown",false));
            assertSame(indirect,world.variant("unknown",true));
            assertSame(endSeparate,world.variant("shaders/world1",false));
            assertSame(endIndirect,world.variant("shaders/world1",true));
            assertNotSame(world.variant("shaders",false).opaque(),world.variant("shaders",true).opaque());
        }
    }

    @Test void oldGlobalsOrderingIsDetectedByRealReflection() throws Exception {
        var program=NativeTerrainProgram.adapt(V,F,List.of(0));
        var pair=ShadercInterfacePair.adapt(program.vertexSource(),program.fragmentSource());
        String invalid=pair.vertex().replace(TerrainShaderAbi.GLOBALS,"""
            layout(std140) uniform Globals { ivec3 CameraBlockPos; vec3 CameraOffset; vec2 ScreenSize;
                float GlintAlpha; float GameTime; int MenuBlurRadius; int UseRgss; };
            """);
        var offsets=reflect(invalid).blocks().get("Globals");
        assertNotEquals(GLOBAL_OFFSETS,offsets);assertEquals(40,offsets.get("GlintAlpha"));
        assertEquals(44,offsets.get("GameTime"));
    }

    @Test void unknownAndAmbiguousPreparedLayoutsReject() {
        String valid=TerrainShaderAbi.GLOBALS+TerrainShaderAbi.TERRAIN+TerrainShaderAbi.SECTION+"void main(){}";
        assertSame(valid,TerrainShaderAbi.vertex(valid,false));
        assertThrows(IllegalArgumentException.class,()->TerrainShaderAbi.vertex(valid+TerrainShaderAbi.SECTION,true));
        assertThrows(IllegalArgumentException.class,()->TerrainShaderAbi.vertex(valid.replace(TerrainShaderAbi.TERRAIN,""),true));
        assertThrows(IllegalArgumentException.class,()->TerrainShaderAbi.vertex(valid.replace(TerrainShaderAbi.SECTION,""),false));
    }

    @Test void nativeSkyTransformLayoutMatchesMinecraftWriterToo() throws Exception {
        var buffer=ByteBuffer.allocateDirect(160).order(ByteOrder.nativeOrder());
        new DynamicGpuData.Transform(new Matrix4f().translation(10,20,30),new Vector4f(0.2f,0.3f,0.4f,0.5f),
            new Vector3f(4,5,6),new Matrix4f().translation(7,8,9)).write(buffer);
        assertEquals(10,buffer.getFloat(48));assertEquals(7,buffer.getFloat(112));
        assertEquals(0.2f,buffer.getFloat(128));assertEquals(4,buffer.getFloat(144));
        var sky=NativeSkyProgram.adapt(V.replace("gl_MultiTexCoord0.xy","(gl_TextureMatrix[0]*gl_MultiTexCoord0).xy"),F,List.of(0));
        var pair=ShadercInterfacePair.adapt(SkyRenderStages.specializeEndSky(sky.vertex().source()),
            SkyRenderStages.specializeEndSky(sky.fragmentSource()));
        assertEquals(Map.of("ModelViewMat",0,"TextureMat",64,"ColorModulator",128,"ModelOffset",144),
            reflect(pair.vertex()).blocks().get("DynamicTransforms"));
    }

    private RenderPipeline descriptor(boolean indirect) {
        return TerrainPipelineLayout.builder(indirect,ExtendedTerrainMesh.format())
            .withLocation(Identifier.fromNamespaceAndPath("oculus","abi-test/"+indirect))
            .withColorTargetState(ColorTargetState.DEFAULT).build();
    }

    private void verifyBindings(Layout layout,boolean indirect) {
        var pipeline=descriptor(indirect);
        var uniforms=BindGroupLayout.flattenUniforms(pipeline.getBindGroupLayouts()).stream()
            .map(BindGroupLayout.UniformDescription::name).collect(java.util.stream.Collectors.toSet());
        assertTrue(uniforms.containsAll(layout.blocks().keySet()),layout.blocks().keySet().toString());
        for(var entry:layout.inputs().entrySet()) {
            var element=pipeline.getVertexFormatBindings().stream().filter(Objects::nonNull)
                .map(format->format.getElement(entry.getKey())).filter(Objects::nonNull).findFirst().orElseThrow();
            int expected=switch(element.format().componentType()) {
                case UINT_8,UINT_16,UINT_32->Spvc.SPVC_BASETYPE_UINT32;
                case SINT_8,SINT_16,SINT_32->Spvc.SPVC_BASETYPE_INT32;
                default->Spvc.SPVC_BASETYPE_FP32;
            };
            assertEquals(expected,entry.getValue().base(),entry.getKey());
            assertTrue(element.format().componentCount()>=entry.getValue().size(),entry.getKey());
        }
    }

    private Layout reflect(String source) throws Exception {
        long compiler=Shaderc.shaderc_compiler_initialize();assertNotEquals(0,compiler);
        long options=0,result=0;
        try {
            options=Shaderc.shaderc_compile_options_initialize();assertNotEquals(0,options);
            Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
            Shaderc.shaderc_compile_options_set_preserve_bindings(options,false);
            Shaderc.shaderc_compile_options_set_generate_debug_info(options);
            Shaderc.shaderc_compile_options_set_optimization_level(options,Shaderc.shaderc_optimization_level_zero);
            result=Shaderc.shaderc_compile_into_spv(compiler,source,Shaderc.shaderc_vertex_shader,"terrain-abi.glsl","main",options);
            assertNotEquals(0,result);
            assertEquals(Shaderc.shaderc_compilation_status_success,Shaderc.shaderc_result_get_compilation_status(result),
                Shaderc.shaderc_result_get_error_message(result));
            var bytes=Objects.requireNonNull(Shaderc.shaderc_result_get_bytes(result));
            var copy=MemoryUtil.memAlloc(bytes.remaining());MemoryUtil.memCopy(bytes,copy);
            try(var module=new SPIRVModule(copy,ShaderType.VERTEX)) {
                var inputs=new HashMap<String,Input>();
                for(var input:module.reflect().inputs()) inputs.put(input.name(),new Input(input.type().baseType(),input.type().vectorSize()));
                return new Layout(Map.copyOf(inputs),offsets(module.spv()));
            }
        } finally {
            if(result!=0)Shaderc.shaderc_result_release(result);
            if(options!=0)Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private Map<String,Map<String,Integer>> offsets(ByteBuffer spv) {
        long context=0;
        try(var stack=MemoryStack.stackPush()) {
            var pointer=stack.callocPointer(1);var count=stack.callocPointer(1);var offset=stack.callocInt(1);
            assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_context_create(pointer));context=pointer.get(0);
            var words=spv.asIntBuffer();
            assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_context_parse_spirv(context,words,words.remaining(),pointer));
            assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_context_create_compiler(context,Spvc.SPVC_BACKEND_NONE,pointer.get(0),Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP,pointer));
            long compiler=pointer.get(0);
            assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_compiler_create_shader_resources(compiler,pointer));
            assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_resources_get_resource_list_for_type(pointer.get(0),
                Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER,pointer,count));
            var resources=SpvcReflectedResource.create(pointer.get(0),Math.toIntExact(count.get(0)));
            var blocks=new HashMap<String,Map<String,Integer>>();
            for(var resource:resources) {
                long type=Spvc.spvc_compiler_get_type_handle(compiler,resource.base_type_id());
                var members=new HashMap<String,Integer>();
                for(int i=0;i<Spvc.spvc_type_get_num_member_types(type);i++) {
                    assertEquals(Spvc.SPVC_SUCCESS,Spvc.spvc_compiler_type_struct_member_offset(compiler,type,i,offset));
                    members.put(Spvc.spvc_compiler_get_member_name(compiler,resource.base_type_id(),i),offset.get(0));
                }
                blocks.put(resource.nameString(),Map.copyOf(members));
            }
            return Map.copyOf(blocks);
        } finally { if(context!=0)Spvc.spvc_context_destroy(context); }
    }
}
