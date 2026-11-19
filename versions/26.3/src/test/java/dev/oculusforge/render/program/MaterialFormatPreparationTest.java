package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialFormatPreparationTest {
    private static final String BRANCH="""
        #ifdef MC_TEXTURE_FORMAT_LAB_PBR_1_3
        const float resourceBranch=2.0;
        #elif defined(MC_TEXTURE_FORMAT_LAB_PBR)
        const float resourceBranch=1.0;
        #else
        const float resourceBranch=0.0;
        #endif
        """;
    static ShaderPackDescriptor fixture(Path root) throws Exception {
        var shaders=Files.createDirectories(root.resolve("shaders"));
        String vertex="#version 120\n"+BRANCH+"void main(){gl_Position=ftransform()+vec4(resourceBranch*0.0000001);}";
        String fragment="#version 120\n"+BRANCH+"uniform float formatCode;\nvoid main(){gl_FragColor=vec4(resourceBranch+formatCode*0.0000001);}";
        for(String stage:List.of("gbuffers_terrain","shadow","final")) {
            Files.writeString(shaders.resolve(stage+".vsh"),vertex);Files.writeString(shaders.resolve(stage+".fsh"),fragment);
        }
        String conditional="#ifdef MC_TEXTURE_FORMAT_LAB_PBR_1_3\n%s2%s\n#elif defined(MC_TEXTURE_FORMAT_LAB_PBR)\n%s1%s\n#else\n%s0%s\n#endif\n";
        Files.writeString(shaders.resolve("shaders.properties"),conditional.formatted("uniform.float.formatCode=",".0","uniform.float.formatCode=",".0","uniform.float.formatCode=",".0"));
        Files.writeString(shaders.resolve("block.properties"),conditional.formatted("block.10","=minecraft:stone","block.10","=minecraft:stone","block.10","=minecraft:stone"));
        Files.writeString(shaders.resolve("item.properties"),conditional.formatted("item.20","=minecraft:stone","item.20","=minecraft:stone","item.20","=minecraft:stone"));
        return new ShaderPackDescriptor("format","Format",root,ShaderPackDescriptor.Kind.DIRECTORY);
    }
    @Test void oneFormatControlsNativePostCustomAndMaterialPreprocessing(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        for(var format:MaterialFormat.values()) {
            var prepared=compiler.prepare(descriptor,format);
            assertEquals(format,prepared.materialFormat());
            String expected="resourceBranch\\s*=\\s*"+format.ordinal()+"\\.0";
            var nativeProgram=prepared.world().variant("shaders").nativeTerrain();assertNotNull(nativeProgram);
            for(String source:List.of(nativeProgram.vertexSource(),nativeProgram.fragmentSource(),
                prepared.world().variant("shaders").nativeShadow().fragmentSource(),
                prepared.programs().getFirst().vertexSource(),prepared.programs().getFirst().fragment().source()))
                assertTrue(java.util.regex.Pattern.compile(expected).matcher(source).find(),source);
            var custom=prepared.variants().get("shaders").customUniforms();
            var values=custom.newState().update(new Object(),new AnimationClock.Snapshot(1,.016f,1),0,0,0,0,0);
            assertEquals(format.ordinal(),values.slot(custom.slots().get("formatCode")));
            assertEquals(100+format.ordinal(),prepared.materials().rules().getFirst().materialId());
            assertEquals(200+format.ordinal(),prepared.items().id("minecraft:stone"));
        }
    }
    @Test void parallelFormatPreparationsDoNotMutateSharedEnvironment(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var work=new ArrayList<java.util.concurrent.Future<PreparedPackPipeline>>();
            for(int i=0;i<12;i++){var format=MaterialFormat.values()[i%3];work.add(executor.submit(()->compiler.prepare(descriptor,format)));}
            for(int i=0;i<work.size();i++)assertEquals(MaterialFormat.values()[i%3],work.get(i).get().materialFormat());
        }
        assertEquals(MaterialFormat.DEFAULT,compiler.prepare(descriptor).materialFormat());
    }
    @Test void contextSpecificSourcesGetDifferentNativeAndPostFingerprints(@TempDir Path root) throws Exception {
        var descriptor=fixture(root);var fingerprints=new HashSet<String>();
        for(var format:MaterialFormat.values()) {
            var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT,format).prepare(descriptor);
            fingerprints.add(prepared.world().variant("shaders").nativeTerrain().fingerprint()+prepared.programs().getFirst().fragment().source());
        }
        assertEquals(3,fingerprints.size());
    }
}
