package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class CenterDepthTest {
    @Test void fragmentCenterDepthBecomesAGpuSamplerInsteadOfACpuUniform() throws Exception {
        var shader=new LegacyFinalShaderAdapter().adapt("#version 120\nuniform float centerDepthSmooth;\nvoid main(){gl_FragColor=vec4(centerDepthSmooth);}");
        assertTrue(shader.uniforms().isEmpty());assertEquals(1,shader.samplers().size());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CENTER_DEPTH,shader.samplers().getFirst().texture());
        assertTrue(shader.source().contains("texelFetch("+CenterDepthInput.SAMPLER+", ivec2(0), 0).r"));
        assertTrue(shader.source().startsWith("#version 330 core"));
    }
    @Test void vertexCanUseTheSameGpuInputAndMixedUniformListsStillWork() throws Exception {
        var vertex=new LegacyFinalVertexAdapter().adapt("#version 120\nuniform float near, centerDepthSmooth;\nvoid main(){gl_Position=ftransform()+vec4(near*centerDepthSmooth);}");
        assertEquals(1,vertex.uniforms().size());assertEquals("near",vertex.uniforms().getFirst().name());
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CENTER_DEPTH,vertex.samplers().getFirst().texture());
    }
    @Test void noVersionAndUnusedCenterDeclarationsDoNotBreakAdmission() throws Exception {
        var adapter=new LegacyFinalShaderAdapter();
        assertEquals(1,adapter.adapt("uniform float centerDepthSmooth;\nvoid main(){gl_FragColor=vec4(centerDepthSmooth);}").samplers().size());
        var unused=adapter.adapt("uniform float centerDepthSmooth;\nvoid main(){gl_FragColor=vec4(1);}");
        assertTrue(unused.samplers().isEmpty());assertTrue(unused.uniforms().isEmpty());
    }
    @Test void malformedOrReservedCenterDeclarationsFailClosed() {
        for(String declaration:List.of("uniform int centerDepthSmooth;","uniform float centerDepthSmooth[2];",
            "uniform float centerDepthSmooth=1.0;","uniform float centerDepthSmooth;\nuniform float centerDepthSmooth;",
            "uniform sampler2D "+CenterDepthInput.SAMPLER+";"))
            assertThrows(ShaderCompatibilityException.class,()->new LegacyFinalShaderAdapter().adapt(
                declaration+"\nvoid main(){gl_FragColor=vec4(1);}"),declaration);
    }
    @Test void halfLifeUsesIrisTenthsOfASecondAndIsFrameRateIndependent() {
        assertEquals(.5f,CenterDepthHistory.blend(.1f,1),1e-7);
        assertEquals(0,CenterDepthHistory.blend(0,1));assertEquals(1,CenterDepthHistory.blend(0,0));
        double remaining=1;
        for(int i=0;i<60;i++)remaining*=1-CenterDepthHistory.blend(1f/60,10);
        assertEquals(.5,remaining,1e-6);
        assertEquals(1,CenterDepthHistory.blend(Float.MAX_VALUE,Float.MIN_VALUE));
    }
    @Test void invalidTimingCannotPoisonGpuHistory() {
        for(float invalid:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->CenterDepthHistory.blend(invalid,1));
            assertThrows(IllegalArgumentException.class,()->CenterDepthHistory.blend(1,invalid));
        }
    }
    @Test void halfLifeDeclarationsAreValidatedAcrossAllPostStages() throws Exception {
        assertEquals(1,CenterDepthHistory.halfLife(List.of("void main(){}")));
        assertEquals(.5f,CenterDepthHistory.halfLife(List.of("const float centerDepthHalflife=.5;","const float centerDepthHalflife=5e-1f;")));
        assertEquals(0,CenterDepthHistory.halfLife(List.of("const float centerDepthHalflife=0.0;")));
        for(String value:List.of("-1.0","1e99","otherUniform","1.0+2.0","nan"))
            assertThrows(ShaderCompatibilityException.class,()->CenterDepthHistory.halfLife(List.of("const float centerDepthHalflife="+value+";")));
        assertThrows(ShaderCompatibilityException.class,()->CenterDepthHistory.halfLife(List.of(
            "const float centerDepthHalflife=1.0;","const float centerDepthHalflife=2.0;")));
        assertEquals(1,CenterDepthHistory.halfLife(List.of("// const float centerDepthHalflife=9.0;\n")));
        for(String invalid:List.of("const int centerDepthHalflife=1;","const float centerDepthHalflife[2]=1;","const float centerDepthHalflife;"))
            assertThrows(ShaderCompatibilityException.class,()->CenterDepthHistory.halfLife(List.of(invalid)));
    }
    @Test void historyAdvancesOnlyAfterCommitAndExactlyOncePerFrame() {
        var state=new CenterDepthHistory();var world=new Object();var frame=new Object();
        assertFalse(state.current(null,null));
        var first=state.advance(world,frame,960,540,.016f,1);
        assertFalse(first.previous());assertNotEquals(first.source(),first.destination());
        assertFalse(state.current(world,frame));assertThrows(IllegalStateException.class,state::slot);
        assertEquals(first,state.advance(world,frame,960,540,.016f,1)); // A failed draw must not advance.
        state.commit(first);assertTrue(state.current(world,frame));assertEquals(first.destination(),state.slot());
        assertNull(state.advance(world,frame,960,540,.016f,1));
        var second=state.advance(world,new Object(),960,540,.016f,1);
        assertTrue(second.previous());assertEquals(first.destination(),second.source());
    }
    @Test void worldIdentityResizeAndResetDiscardOldHistory() {
        var state=new CenterDepthHistory();var world=new String("world");
        state.commit(state.advance(world,new Object(),960,540,.016f,1));
        assertFalse(state.advance(new String("world"),new Object(),960,540,.016f,1).previous());
        assertFalse(state.advance(world,new Object(),800,600,.016f,1).previous());
        state.reset();assertThrows(IllegalStateException.class,state::slot);
        assertFalse(state.advance(world,new Object(),960,540,.016f,1).previous());
        assertThrows(IllegalArgumentException.class,()->state.advance(world,new Object(),0,540,.016f,1));
    }
    @Test void postUniformWriterNoLongerConsultsTheInteractionHitResult() throws Exception {
        var accesses=new ArrayList<String>();
        try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/oculusforge/render/program/PackFinalProgram.class"))) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int o,String owner,String name,String desc){if(name.equals("hitResult"))accesses.add(n);}
                    };
                }
            },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        assertTrue(accesses.isEmpty(),accesses.toString());
    }

    @Test void realGpuPathNeverReadsDepthBackToTheCpuAndCapturesBeforeDeferred() throws Exception {
        var forbidden=new ArrayList<String>();var boundary=new ArrayList<String>();
        for(String resource:List.of("pipeline/CenterDepthPass","world/WorldRenderHooks"))
            try(var input=Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/oculusforge/render/"+resource+".class"))) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int o,String owner,String name,String desc,boolean itf) {
                                if(resource.endsWith("CenterDepthPass")&&List.of("copyTextureToBuffer","queueFencedTask","glReadPixels","glGetTexImage").contains(name))forbidden.add(name);
                                if(n.startsWith("lambda$captureOpaqueDepthBeforeTransparency$")&&name.equals("capture")&&owner.endsWith("CenterDepthPass"))boundary.add("center");
                                if(n.startsWith("lambda$captureOpaqueDepthBeforeTransparency$")&&name.equals("execute")&&owner.endsWith("WorldDeferredPass"))boundary.add("deferred");
                            }
                        };
                    }
                },ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
        assertTrue(forbidden.isEmpty(),forbidden.toString());assertEquals(List.of("center","deferred"),boundary);
    }

    @Test void cpuPackAdmissionValidatesHalfLifeBeforeCreatingGpuResources(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        var shaders=java.nio.file.Files.createDirectory(root.resolve("shaders"));
        var file=shaders.resolve("final.fsh");
        java.nio.file.Files.writeString(file,"#version 120\nuniform float centerDepthSmooth;\nconst float centerDepthHalflife=-1.0;\nvoid main(){gl_FragColor=vec4(centerDepthSmooth);}");
        var descriptor=new dev.oculusforge.shaderpack.ShaderPackDescriptor("center","center",root,dev.oculusforge.shaderpack.ShaderPackDescriptor.Kind.DIRECTORY);
        var compiler=new PackProgramCompiler(dev.oculusforge.shaderpack.ShaderPackLimits.DEFAULT);
        assertThrows(ShaderCompatibilityException.class,()->compiler.prepare(descriptor));
        java.nio.file.Files.writeString(file,"#version 120\nuniform float centerDepthSmooth;\nvoid main(){gl_FragColor=vec4(centerDepthSmooth);}");
        assertEquals(LegacyFinalShaderAdapter.TextureSemantic.CENTER_DEPTH,compiler.prepare(descriptor).programs().getFirst().fragment().samplers().getFirst().texture());
    }
}
