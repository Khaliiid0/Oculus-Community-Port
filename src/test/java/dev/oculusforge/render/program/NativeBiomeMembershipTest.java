package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.render.world.*;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class NativeBiomeMembershipTest {
    @Test void completePackPreparationSelectsModernBiomeBranch(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var shaders=java.nio.file.Files.createDirectory(root.resolve("shaders"));
        java.nio.file.Files.writeString(shaders.resolve("shaders.properties"),
            "#if MC_VERSION>=11800\nuniform.float.probe=if(in(biome, BIOME_DESERT),1,0)\n"
            +"#else\nuniform.float.probe=if(in(biome,2),1,0)\n#endif\n");
        for(String stage:List.of("gbuffers_terrain","shadow")) {
            java.nio.file.Files.writeString(shaders.resolve(stage+".vsh"),
                "uniform float probe; void main(){gl_Position=ftransform()+vec4(probe);}");
            java.nio.file.Files.writeString(shaders.resolve(stage+".fsh"),
                "uniform float probe; void main(){gl_FragColor=vec4(probe);}");
        }
        java.nio.file.Files.writeString(shaders.resolve("final.fsh"),"void main(){gl_FragColor=vec4(1);}");
        var prepared=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT).prepare(
            new dev.oculusforge.shaderpack.ShaderPackDescriptor("biome","Biome",root,
                dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY));
        var world=prepared.world().variant("shaders");
        assertNotNull(world.nativeTerrain(),world.nativeTerrainIssue());
        assertNotNull(world.nativeShadow(),world.nativeShadowIssue());
        assertTrue(world.nativeTerrain().fragmentSource().contains("oculusNative_oculusBiomeCode"));
    }
    @Test void exactKeysAndSymbolsShareCodesWithoutNamespaceOrSubstringGuessing() {
        for(String name:List.of("desert","badlands","grove","sulfur_caves","soul_sand_valley","the_end")) {
            int code=NativeBiomeSymbols.fromKey("minecraft:"+name);
            assertTrue(code>0);
            assertEquals(code,NativeBiomeSymbols.fromSymbol("BIOME_"+name.toUpperCase(java.util.Locale.ROOT)));
        }
        assertEquals(0,NativeBiomeSymbols.fromKey("mod:desert"));
        assertEquals(0,NativeBiomeSymbols.fromKey("minecraft:desert_hills"));
        assertEquals(0,NativeBiomeSymbols.fromSymbol("BIOME_DOES_NOT_EXIST"));
        assertEquals(0,NativeBiomeSymbols.fromSymbol("BIOME_desert"));
        assertNotEquals(NativeBiomeSymbols.fromSymbol("BIOME_SWAMP"),NativeBiomeSymbols.fromSymbol("BIOME_MANGROVE_SWAMP"));
    }
    @Test void allVanillaConstantsHaveDistinctMatchingCodes() throws Exception {
        var seen=new java.util.HashSet<Integer>();
        for(var field:net.minecraft.world.level.biome.Biomes.class.getFields()) {
            if(field.getType()!=net.minecraft.resources.ResourceKey.class) continue;
            var key=(net.minecraft.resources.ResourceKey<?>)field.get(null);
            int code=NativeBiomeSymbols.fromKey(key.identifier().toString());
            assertTrue(code>0,field.getName());
            assertTrue(seen.add(code),field.getName());
            assertEquals(code,NativeBiomeSymbols.fromSymbol("BIOME_"+field.getName()));
        }
        assertFalse(seen.isEmpty());
    }
    @Test void selectedMultilineMembershipBindsBothNativeStages() throws Exception {
        var custom=NativeCustomUniforms.parse("#if MC_VERSION>=11800\n"
            +"uniform.float.probe=if(in(biome, BIOME_DESERT, \\\n BIOME_BADLANDS),if(cameraPosition.y>=113,1,0),0)\n"
            +"#else\nuniform.float.probe=if(in(biome,2),1,0)\n#endif",Map.of("MC_VERSION","260200"));
        for(boolean shadow:new boolean[]{false,true}) {
            var program=NativeTerrainProgram.adaptWithCustomUniforms(
                "uniform float probe; void main(){gl_Position=ftransform()+vec4(probe);}",
                "uniform float probe; void main(){gl_FragColor=vec4(probe);}",List.of(0),shadow,false,custom);
            for(String source:List.of(program.vertexSource(),program.fragmentSource())) {
                assertTrue(source.contains("oculusNative_oculusBiomeCode == "+NativeBiomeSymbols.fromSymbol("BIOME_DESERT")));
                assertTrue(source.contains(" || "));
                assertTrue(source.contains("oculusNative_cameraPosition.xyz.y >= 113.0"));
            }
        }
    }
    @Test void numericUnknownAndMalformedMembershipRemainUnsupported() throws Exception {
        for(String input:List.of("in(biome,2)","in(biome,BIOME_UNKNOWN)","in(biome)",
            "in(other,BIOME_DESERT)","in(biome,BIOME_DESERT,)","in(biome,BIOME_DESERT.x)")) {
            var custom=NativeCustomUniforms.parse("uniform.float.probe=if("+input+",1,0)");
            assertTrue(custom.adapt("uniform float probe; float x=probe;").contains("uniform float probe;"),input);
        }
        for(String reserved:List.of("biome","BIOME_DESERT","oculusBiomeCode")) {
            var custom=NativeCustomUniforms.parse("variable.float."+reserved+"=1\nuniform.float.probe=if(in(biome,BIOME_DESERT),1,0)");
            assertTrue(custom.adapt("uniform float probe;").contains("uniform float probe;"));
        }
    }
    @Test void biomeSnapshotUsesPreviousPaddingWithoutChangingExistingOffsets() {
        int code=NativeBiomeSymbols.fromSymbol("BIOME_DESERT");
        var env=new WorldEnvironment(3,.5f,.25f,new EyeBrightness(7,11),code);
        var matrix=new Matrix4f();
        var world=new WorldMatrixData(matrix,matrix,matrix,matrix,10,20,30,854,480,0,
            AnimationClock.ZERO,RelativeEyePosition.ZERO,HeldLight.NONE,env);
        var bytes=ByteBuffer.allocate(NativeWorldBindings.BYTE_SIZE+8).order(ByteOrder.nativeOrder());
        bytes.position(8); world.write(bytes);
        assertEquals(976, NativeWorldBindings.BYTE_SIZE);
        assertEquals(code,bytes.getInt(8+616));
        assertEquals(0,bytes.getInt(8+620));
        assertEquals(20f,bytes.getFloat(8+516));
        assertEquals(112,bytes.getInt(8+600));
        assertEquals(176,bytes.getInt(8+604));
        assertThrows(IllegalArgumentException.class,()->new WorldEnvironment(0,0,0,EyeBrightness.DARK,-1));
    }
}
