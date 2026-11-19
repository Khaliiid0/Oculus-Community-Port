package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;

class ShadercInterfacePairTest {
    private static final String V="""
        #version 330 core
        out vec2 texcoord;
        out vec2 texCoord;
        void main(){vec2 uv=vec2((gl_VertexID<<1)&2,gl_VertexID&2);texcoord=uv;texCoord=uv;gl_Position=vec4(uv*2.-1.,0.,1.);}
        """;
    private static final String F="""
        #version 330 core
        uniform sampler2D image;
        in vec2 texcoord;
        layout(location=0) out vec4 outputColor;
        void main(){outputColor=texture(image,texcoord);}
        """;

    @Test void stableLocationsMatchNamesRatherThanDeclarationOrder() {
        var pair=ShadercInterfacePair.adapt(V,F);
        assertTrue(pair.vertex().contains("layout(location=0) out vec2 texCoord;"));
        assertTrue(pair.vertex().contains("layout(location=1) out vec2 texcoord;"));
        assertTrue(pair.fragment().contains("layout(location=1) in vec2 texcoord;"));
        assertTrue(pair.fragment().contains("layout(location=0) out vec4 outputColor;"));
        assertTrue(pair.vertex().contains("gl_VertexIndex"));assertFalse(pair.vertex().contains("gl_VertexID"));
        assertTrue(pair.vertex().startsWith("#version 450 core"));
    }

    @Test void functionParametersAndCommentsAreNotInterfaceDeclarations() {
        String vertex=V.replace("out vec2 texcoord;","/* out vec4 fake; */\nout vec2 texcoord;")
            .replace("void main()", "void helper(in vec2 x,out vec2 y){y=x;}\nvoid main()");
        var pair=ShadercInterfacePair.adapt(vertex,F);
        assertTrue(pair.vertex().contains("void helper(in vec2 x,out vec2 y)"));
        assertTrue(pair.vertex().contains("/* out vec4 fake; */"));
        assertFalse(pair.vertex().contains("layout(location=2)"));
    }

    @Test void explicitLocationsArePreservedIncludingReorderedMrtOutputs() {
        var pair=ShadercInterfacePair.adapt("#version 330 core\nlayout(location=7) out vec2 uv;void main(){uv=vec2(1);gl_Position=vec4(1);}",
            "#version 330 core\nin vec2 uv;layout(location=2) out vec4 b;layout(location=0) out vec4 a;void main(){a=vec4(uv,0,1);b=a;}");
        assertTrue(pair.fragment().contains("layout(location=7) in vec2 uv;"));
        assertTrue(pair.fragment().contains("layout(location=2) out vec4 b;"));
        assertTrue(pair.fragment().contains("layout(location=0) out vec4 a;"));
    }

    @Test void mismatchedOrAmbiguousInterfacesReject() {
        for(String invalid:new String[]{F.replace("in vec2 texcoord;","in vec3 texcoord;"),
            F.replace("in vec2 texcoord;","flat in vec2 texcoord;"),
            F.replace("in vec2 texcoord;","layout(location=3) in vec2 texcoord;"),
            F.replace("in vec2 texcoord;","in vec2 missing;"),
            F.replace("in vec2 texcoord;","in vec2 texcoord;in vec2 texcoord;")}) {
            assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(V,invalid));
        }
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(
            V.replace("out vec2 texcoord;","layout(location=0) out vec2 texcoord;")
                .replace("out vec2 texCoord;","layout(location=0) out vec2 texCoord;"),F));
    }

    @Test void unsupportedBlocksArraysMatricesAndBaseInstanceSemanticsDoNotGetGuessed() {
        for(String declaration:new String[]{"out vec2 value[2];","out mat3 value;","out Interface {vec2 a;} value;"})
            assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt("#version 330 core\n"+declaration+"\nvoid main(){gl_Position=vec4(1);}","void main(){}"));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(V.replace("gl_VertexID","gl_InstanceID"),F));
    }

    @Test void bslStyleListsExpandAndMatchByNameAcrossDifferentOrders() {
        var pair=ShadercInterfacePair.adapt("""
            #version 330 core
            out vec3 sunVec, upVec, eastVec;
            void main(){sunVec=vec3(1);upVec=vec3(2);eastVec=vec3(3);gl_Position=vec4(1);}
            """, """
            #version 330 core
            in vec3 upVec, sunVec;
            layout(location=0) out vec4 color;
            void main(){color=vec4(upVec+sunVec,1);}
            """);
        assertTrue(pair.vertex().contains("layout(location=0) out vec3 eastVec;"));
        assertTrue(pair.vertex().contains("layout(location=1) out vec3 sunVec;"));
        assertTrue(pair.fragment().contains("layout(location=1) in vec3 sunVec;"));
        assertTrue(pair.fragment().contains("layout(location=2) in vec3 upVec;"));
        assertEquals(pair,ShadercInterfacePair.adapt(pair.vertex(),pair.fragment()));
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.vertex(),Shaderc.shaderc_vertex_shader));
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.fragment(),Shaderc.shaderc_fragment_shader));
    }

    @Test void complementaryFlatListsPreserveInterpolationAndIntegerSemantics() {
        var pair=ShadercInterfacePair.adapt("flat out vec3 upVec, sunVec;flat out int a,b;void main(){gl_Position=vec4(1);}",
            "flat in int b,a;flat in vec3 sunVec,upVec;layout(location=0) out vec4 color;void main(){color=vec4(a+b)+vec4(sunVec+upVec,1);}");
        assertTrue(pair.vertex().contains("layout(location=0) flat out int a;"));
        assertTrue(pair.fragment().contains("layout(location=0) flat in int a;"));
        assertTrue(pair.fragment().contains("flat in vec3 sunVec;"));
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.vertex(),Shaderc.shaderc_vertex_shader));
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.fragment(),Shaderc.shaderc_fragment_shader));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt("out int a,b;void main(){}","void main(){}"));
    }

    @Test void explicitListLocationsReserveEverySlotAndKeepCommentsAndParameters() {
        var pair=ShadercInterfacePair.adapt("""
            // out vec3 fake, ignored;
            layout(location=4) out vec2 uv, lightUv;
            out vec2 other;
            void helper(in vec2 x,out vec2 y){y=x;}
            void main(){gl_Position=vec4(1);}
            """, "in vec2 lightUv,uv;layout(location=0) out vec4 color;void main(){color=vec4(uv+lightUv,0,1);}");
        assertTrue(pair.vertex().contains("layout(location=4) out vec2 uv;"));
        assertTrue(pair.vertex().contains("layout(location=5) out vec2 lightUv;"));
        assertTrue(pair.vertex().contains("layout(location=0) out vec2 other;"));
        assertTrue(pair.fragment().contains("layout(location=5) in vec2 lightUv;"));
        assertTrue(pair.vertex().contains("// out vec3 fake, ignored;"));
        assertTrue(pair.vertex().contains("void helper(in vec2 x,out vec2 y)"));
    }

    @Test void duplicateOverlappingOverflowingAndAmbiguousListsReject() {
        for(String declaration:new String[]{"out vec2 a,a;", "layout(location=63) out vec2 a,b;",
            "layout(location=2147483647) out vec2 a,b;", "layout(location=3) out vec2 a,b;layout(location=4) out vec2 c;",
            "out vec2 a[2],b;", "out mat3 a,b;", "out vec2 a=vec2(1),b;"})
            assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(declaration+"void main(){}","void main(){}"));
        var many=new StringBuilder("out vec2 ");
        for(int i=0;i<65;i++)many.append(i==0?"":",").append("v").append(i);
        many.append(";void main(){}");
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(many.toString(),"void main(){}"));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt("flat out vec2 a,b;void main(){}",
            "in vec2 a,b;void main(){}"));
    }

    @Test void flatIntegerInterfacesAreAcceptedButSmoothIntegersReject() {
        var pair=ShadercInterfacePair.adapt("flat out int material;void main(){material=1;gl_Position=vec4(1);}",
            "flat in int material;layout(location=0) out vec4 color;void main(){color=vec4(material);}");
        assertTrue(pair.fragment().contains("layout(location=0) flat in int material;"));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt("out int i;void main(){}","in int i;void main(){}"));
    }

    @Test void convertedPairsAreIdempotentAndDoNotDowngradeUnknownLanguageVersions() {
        var pair=ShadercInterfacePair.adapt(V,F);
        assertEquals(pair,ShadercInterfacePair.adapt(pair.vertex(),pair.fragment()));
        for(String version:new String[]{"460 core","300 es","120","450 compatibility","potato"})
            assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(
                V.replace("330 core",version),F));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(V+"\n#version 450 core\n",F));
        assertEquals(pair,ShadercInterfacePair.adapt(V.replace("#version 330 core","#version 330 core // prepared"),F));
    }

    @Test void declarationAndLocationBudgetsRejectInsteadOfOverflowing() {
        for(String location:new String[]{"64","999999999999999999999999999999999999"})
            assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(
                "layout(location="+location+") out vec2 uv;void main(){}","void main(){}"));
        var declarations=new StringBuilder();
        for(int i=0;i<65;i++)declarations.append("out vec2 uv").append(i).append(';');
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt(declarations.toString(),"void main(){}"));
        assertThrows(IllegalArgumentException.class,()->ShadercInterfacePair.adapt("/* unterminated","void main(){}"));
    }

    @Test void actualShadercCompilesConvertedFullscreenPairToSpirvWithoutAGpu() {
        var pair=ShadercInterfacePair.adapt(V,F);
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.vertex(),Shaderc.shaderc_vertex_shader));
        assertEquals(Shaderc.shaderc_compilation_status_success,compile(pair.fragment(),Shaderc.shaderc_fragment_shader));
    }

    @Test void actualShadercNegativeControlsDetectMissingLocationsAndBrokenGlsl() {
        assertNotEquals(Shaderc.shaderc_compilation_status_success,compile(V,Shaderc.shaderc_vertex_shader));
        var pair=ShadercInterfacePair.adapt(V,F);
        assertNotEquals(Shaderc.shaderc_compilation_status_success,
            compile(pair.fragment().replace("texture(image,texcoord)","missingSymbol"),Shaderc.shaderc_fragment_shader));
    }

    private int compile(String source,int kind) {
        long compiler=Shaderc.shaderc_compiler_initialize();assertNotEquals(0,compiler);
        long options=0,result=0;
        try {
            options=Shaderc.shaderc_compile_options_initialize();assertNotEquals(0,options);
            // Match the bundled 66.1.2 GlslCompiler target/options, not a newer SPIR-V environment.
            Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
            Shaderc.shaderc_compile_options_set_preserve_bindings(options,false);
            Shaderc.shaderc_compile_options_set_generate_debug_info(options);
            Shaderc.shaderc_compile_options_set_optimization_level(options,Shaderc.shaderc_optimization_level_zero);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options,false);
            result=Shaderc.shaderc_compile_into_spv(compiler,source,kind,"oculus-test.glsl","main",options);
            assertNotEquals(0,result);
            int status=Shaderc.shaderc_result_get_compilation_status(result);
            if(status==Shaderc.shaderc_compilation_status_success) {
                var bytes=java.util.Objects.requireNonNull(Shaderc.shaderc_result_get_bytes(result));
                assertTrue(bytes.remaining()>=20);assertEquals(0x07230203,bytes.order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(0));
            }
            return status;
        } finally {
            if(result!=0)Shaderc.shaderc_result_release(result);
            if(options!=0)Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }
}
