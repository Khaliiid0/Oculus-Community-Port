package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import dev.oculusforge.shaderpack.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PostDepthSnapshotTest {
    @Test void preHandDepthNeverCanonicalizesIntoLiveDepth() throws Exception {
        var shader=new LegacyFinalShaderAdapter().adapt("""
            uniform sampler2D depthtex0;
            uniform sampler2D depthtex1;
            uniform sampler2D depthtex2;
            uniform sampler2D gdepthtex;
            void main(){gl_FragColor=texture2D(depthtex0,vec2(.5))
                +texelFetch(depthtex1,ivec2(0),0)+textureLod(depthtex2,vec2(.5),0.0)
                +texture2D(gdepthtex,vec2(.5));}
            """);
        assertEquals(List.of(0,1,2),shader.samplers().stream().map(LegacyFinalShaderAdapter.SamplerBinding::colorIndex).toList());
        assertTrue(shader.source().contains("oculus_textureDepthLod(depthtex2,"));
        assertFalse(shader.source().contains("uniform sampler2D gdepthtex;"));
    }

    @Test void postOnlyPreHandDepthIsRejectedWithoutAWorldSnapshotProducer(@TempDir Path root) throws Exception {
        Files.createDirectory(root.resolve("shaders"));
        Files.writeString(root.resolve("shaders/final.fsh"),"#version 120\nuniform sampler2D depthtex2;\nvoid main(){gl_FragColor=texture2D(depthtex2,vec2(.5));}");
        var descriptor=new ShaderPackDescriptor("depth2","depth2",root,ShaderPackDescriptor.Kind.DIRECTORY);
        var error=assertThrows(ShaderCompatibilityException.class,()->new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(descriptor));
        assertTrue(error.getMessage().contains("depthtex2")&&error.getMessage().contains("snapshot bridge"),error.getMessage());
    }

    @Test void allPostDepthOperationsPreserveThePreHandIdentity() throws Exception {
        for(String call:List.of("texture2D(depthtex2,vec2(.5))","texture(depthtex2,vec2(.5))",
            "texture2DLod(depthtex2,vec2(.5),0.0)","textureLod(depthtex2,vec2(.5),0.0)","texelFetch(depthtex2,ivec2(0),0)")) {
            var shader=new LegacyFinalShaderAdapter().adapt("uniform sampler2D depthtex2;\nvoid main(){gl_FragColor="+call+";}");
            assertEquals(2,shader.samplers().getFirst().colorIndex());
            assertTrue(WorldDepthSamplers.requiresOpaqueSnapshot(shader.samplers().getFirst()));
            assertFalse(shader.source().contains("oculus_fetchDepth(depthtex0,"));
        }
    }

    @Test void liveAliasStaysLiveAndCustomDepthNamedImagesStayCustom() throws Exception {
        var adapter=new LegacyFinalShaderAdapter();
        var live=adapter.adapt("uniform sampler2D gdepthtex;\nvoid main(){gl_FragColor=texture2D(gdepthtex,vec2(.5));}").samplers().getFirst();
        assertEquals(0,live.colorIndex());assertFalse(WorldDepthSamplers.requiresOpaqueSnapshot(live));
        var custom=adapter.adapt("uniform sampler2D depthtex2;\nvoid main(){gl_FragColor=texture2D(depthtex2,vec2(.5));}",
            LegacyFinalVertexAdapter.DEFAULT_OUTPUTS,Map.of("depthtex2","local.png")).samplers().getFirst();
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CUSTOM,custom.texture());
        assertFalse(WorldDepthSamplers.requiresOpaqueSnapshot(custom));
    }

    @Test void physicalResolverNeverTouchesTheWrongResourceSupplier() {
        Object live=new Object(),opaque=new Object();
        assertSame(live,WorldDepthSamplers.resolve(0,()->live,()->{throw new AssertionError("No snapshot for post-only live depth");}));
        for(int index:new int[]{1,2})assertSame(opaque,WorldDepthSamplers.resolve(index,
            ()->{throw new AssertionError("Live depth contains translucent geometry");},()->opaque));
    }

    @Test void invalidIndicesAndMissingSnapshotsFailClosed() {
        for(int index:new int[]{-1,3,Integer.MAX_VALUE})assertThrows(IllegalArgumentException.class,
            ()->WorldDepthSamplers.resolve(index,()->{throw new AssertionError();},()->{throw new AssertionError();}));
        assertThrows(NullPointerException.class,()->WorldDepthSamplers.resolve(2,()->new Object(),()->null));
    }

    @Test void realCompilerAndDrawPathsUseTheSameSnapshotSelector() throws Exception {
        for(String resource:List.of("dev/oculusforge/render/program/PackProgramCompiler.class",
            "dev/oculusforge/render/pipeline/FullscreenProgramRunner.class")) {
            var calls=new HashSet<String>();
            try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
                new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override public org.objectweb.asm.MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                        if(!n.equals(resource.contains("PackProgramCompiler")?"compile":"execute"))return null;
                        return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf) {
                                if(owner.equals("dev/oculusforge/render/program/WorldDepthSamplers"))calls.add(name);
                            }
                            @Override public void visitInvokeDynamicInsn(String name,String desc,org.objectweb.asm.Handle bootstrap,Object... args) {
                                for(Object arg:args)if(arg instanceof org.objectweb.asm.Handle handle
                                    &&handle.getOwner().equals("dev/oculusforge/render/program/WorldDepthSamplers"))calls.add(handle.getName());
                            }
                        };
                    }
                },org.objectweb.asm.ClassReader.SKIP_DEBUG|org.objectweb.asm.ClassReader.SKIP_FRAMES);
            }
            assertTrue(calls.contains(resource.contains("PackProgramCompiler")?"requiresOpaqueSnapshot":"resolve"),resource);
        }
    }

    @Test void preHandOnlyPostReadIsAdmittedWithTerrainEvenWithoutNativeDepthRead(@TempDir Path root) throws Exception {
        var shaders=Files.createDirectory(root.resolve("shaders"));
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"),"#version 120\nvoid main(){gl_Position=ftransform();}");
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),"#version 120\nvoid main(){gl_FragColor=vec4(1);}");
        Files.writeString(shaders.resolve("final.fsh"),"#version 120\nuniform sampler2D depthtex2;\nvoid main(){gl_FragColor=texture2D(depthtex2,vec2(.5));}");
        var prepared=new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(
            new ShaderPackDescriptor("depth2","depth2",root,ShaderPackDescriptor.Kind.DIRECTORY));
        assertNotNull(prepared.world().variant("shaders"));
        assertFalse(prepared.world().variant("shaders").nativeTerrain().usesOpaqueWorldDepth());
        assertTrue(WorldDepthSamplers.requiresOpaqueSnapshot(prepared.programs().getFirst().fragment().samplers().getFirst()));
    }
}
