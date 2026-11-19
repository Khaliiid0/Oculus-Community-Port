package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShaderOptionPreparationTest {
    private static ShaderPackDescriptor fixture(Path root) throws Exception {
        Path shaders=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(shaders.resolve("options.glsl"),"""
            #ifdef FEATURE
            const float early=1.0;
            #else
            const float early=0.0;
            #endif
            //#define FEATURE
            #define MODE 1 //[1 2]
            #ifdef FEATURE
            const float flagPart=10.0;
            #else
            const float flagPart=0.0;
            #endif
            const float optionBranch=float(MODE)+flagPart+early;
            """);
        String vertex="#version 120\n#include \"/options.glsl\"\nvoid main(){gl_Position=ftransform()+vec4(optionBranch*0.0000001);}";
        String fragment="#version 120\n#include \"/options.glsl\"\nuniform float optionCode;\nvoid main(){gl_FragColor=vec4(optionBranch+optionCode*0.0000001);}";
        for(String stage:List.of("gbuffers_terrain","shadow","final")) {
            Files.writeString(shaders.resolve(stage+".vsh"),vertex);Files.writeString(shaders.resolve(stage+".fsh"),fragment);
        }
        Files.writeString(shaders.resolve("shaders.properties"),"""
            #if MODE == 2
            #ifdef FEATURE
            uniform.float.optionCode=12.0
            #else
            uniform.float.optionCode=2.0
            #endif
            #else
            #ifdef FEATURE
            uniform.float.optionCode=11.0
            #else
            uniform.float.optionCode=1.0
            #endif
            #endif
            """);
        for(String type:List.of("block","item"))Files.writeString(shaders.resolve(type+".properties"),
            "#if MODE == 2\n"+type+".102=minecraft:stone\n#else\n"+type+".101=minecraft:stone\n#endif\n");
        return new ShaderPackDescriptor("option-fixture","Option fixture",root,ShaderPackDescriptor.Kind.DIRECTORY);
    }
    @Test void optionsReachNativePostCustomUniformsAndMaterialIds(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        for(int mode:new int[]{1,2})for(boolean feature:new boolean[]{false,true}) {
            var options=Map.of("MODE",Integer.toString(mode),"FEATURE",Boolean.toString(feature));
            var prepared=compiler.prepare(descriptor,MaterialFormat.LAB_PBR_1_3,options);
            assertEquals(options,prepared.optionValues());assertEquals(MaterialFormat.LAB_PBR_1_3,prepared.materialFormat());
            assertThrows(UnsupportedOperationException.class,()->prepared.optionValues().clear());
            var nativeProgram=prepared.world().variant("shaders").nativeTerrain();assertNotNull(nativeProgram);
            for(String source:List.of(nativeProgram.vertexSource(),nativeProgram.fragmentSource(),
                prepared.world().variant("shaders").nativeShadow().fragmentSource(),
                prepared.programs().getFirst().vertexSource(),prepared.programs().getFirst().fragment().source())) {
                assertTrue(source.matches("(?s).*optionBranch\\s*=\\s*float\\("+mode+"\\).*"),source);
                assertTrue(source.matches("(?s).*early\\s*=\\s*0\\.0.*"),"Do not globally predefine a selected macro before its source declaration");
                assertTrue(source.matches("(?s).*flagPart\\s*=\\s*"+(feature?"10":"0")+"\\.0.*"),source);
            }
            var custom=prepared.variants().get("shaders").customUniforms();
            var values=custom.newState().update(new Object(),new AnimationClock.Snapshot(1,.016f,1),0,0,0,0,0);
            assertEquals(mode+(feature?10:0),values.slot(custom.slots().get("optionCode")));
            assertEquals(100+mode,prepared.materials().rules().getFirst().materialId());
            assertEquals(100+mode,prepared.items().id("minecraft:stone"));
        }
        assertTrue(Files.readString(root.resolve("shaders/options.glsl")).contains("//#define FEATURE\n#define MODE 1"));
    }
    @Test void parallelSelectionsDoNotMutateTheSharedCompiler(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var work=new ArrayList<java.util.concurrent.Future<PreparedPackPipeline>>();
            for(int i=0;i<9;i++) {
                var options=Map.of("MODE",Integer.toString(1+i%2),"FEATURE",Boolean.toString(i%3==0));
                work.add(executor.submit(()->compiler.prepare(descriptor,MaterialFormat.DEFAULT,options)));
            }
            for(int i=0;i<work.size();i++)assertEquals(Map.of("MODE",Integer.toString(1+i%2),"FEATURE",Boolean.toString(i%3==0)),work.get(i).get().optionValues());
        }
    }
    @Test void unknownOrInvalidSelectionsFailBeforePipelinePreparation(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        assertThrows(ShaderPackException.class,()->compiler.prepare(descriptor,MaterialFormat.DEFAULT,Map.of("MODE","99")));
        assertThrows(ShaderPackException.class,()->compiler.prepare(descriptor,MaterialFormat.DEFAULT,Map.of("UNKNOWN","true")));
        assertEquals(Map.of("MODE","2","FEATURE","false"),compiler.prepare(descriptor,MaterialFormat.DEFAULT,Map.of("MODE","2","FEATURE","false")).optionValues());
    }
    @Test void unselectedOptionsRemainUnknownInCustomProperties() {
        var environment=ShaderOptionOverrides.environment(Map.of("OCULUS_POST_PROCESSING","1"),Map.of("FEATURE","false"),Set.of("FEATURE"));
        String selected=CustomUniformProperties.select("#ifdef FEATURE\nuniform.float.code=10\n#else\nuniform.float.code=0\n#endif",environment);
        assertTrue(selected.contains("uniform.float.code=0"));assertFalse(selected.contains("UNRESOLVED"));
        assertTrue(CustomUniformProperties.select("#ifdef UNKNOWN\nuniform.float.code=0\n#endif",environment).contains("UNRESOLVED"));
    }
}
