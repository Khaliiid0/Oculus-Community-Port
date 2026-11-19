package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.buffers.*;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Fake passes test sequencing/state ownership, not a driver or rendered pixels. */
class RestartableRenderPassTest {
    record Event(int pass, String name, Object[] args) {}
    static final class Device {
        final List<Event> events = new ArrayList<>();
        final List<Backend> passes = new ArrayList<>();
        int active;
        int failOpen = -1;
        int failPipeline = -1;
        int failClose = -1;
        RenderPass open() {
            assertEquals(0,active,"nested pass creation");
            if (passes.size() == failOpen) throw new IllegalStateException("open");
            var backend = new Backend(this,passes.size()); passes.add(backend); active++;
            events.add(new Event(backend.id,"open",new Object[0]));
            return proxy(RenderPass.class,backend);
        }
    }
    static final class Backend implements InvocationHandler {
        final Device device; final int id;
        boolean closed; int groups;
        final Map<String,Object[]> uniforms = new HashMap<>();
        byte[] constants;
        Backend(Device device,int id) { this.device=device;this.id=id; }
        @Override public Object invoke(Object p,Method m,Object[] args) {
            if(m.getDeclaringClass()==Object.class) return objectMethod(p,m,args);
            assertFalse(closed,"command after close");
            var values=args==null?new Object[0]:args.clone();
            device.events.add(new Event(id,m.getName(),values));
            switch(m.getName()) {
                case "close" -> {
                    closed=true; device.active--; assertEquals(0,groups,"unbalanced debug groups");
                    if(device.failClose==id)throw new IllegalStateException("close");
                }
                case "pushDebugGroup" -> groups++;
                case "popDebugGroup" -> { assertTrue(groups>0); groups--; }
                case "setPipeline" -> { if(device.failPipeline==id) throw new IllegalStateException("pipeline restore"); }
                case "setUniform" -> uniforms.put((String)args[0],Arrays.copyOfRange(args,1,args.length));
                case "pushConstants" -> { var bytes=((ByteBuffer)args[0]).duplicate(); constants=new byte[bytes.remaining()];bytes.get(constants); }
                case "drawMultipleIndexed" -> {
                    @SuppressWarnings("unchecked") var draws=(Collection<RenderPass.Draw<Object>>)args[0];
                    for(var draw:draws) if(draw.uniformUploaderConsumer()!=null)
                        draw.uniformUploaderConsumer().accept(args[4],new RenderPass.UniformUploader() {
                            public void setUniform(String name,GpuBufferSlice value) { uniforms.put(name,new Object[]{value}); }
                            public void pushConstants(ByteBuffer value) { var b=value.duplicate();constants=new byte[b.remaining()];b.get(constants); }
                        });
                }
            }
            return null;
        }
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    static Object objectMethod(Object p,Method m,Object[] args) {
        return switch(m.getName()) {case "equals" -> p==args[0];case "hashCode" -> System.identityHashCode(p);default -> "fake";};
    }
    static <T> T dummy(Class<T> type) { return proxy(type,(p,m,a)->{
        if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
        throw new AssertionError("GPU object inspected: "+m.getName());
    }); }
    static GpuBufferSlice slice() { return new GpuBufferSlice(dummy(GpuBuffer.class),0,256); }

    @Test void terrainRunsOnlyAfterMainClosesAndMainResumesAfterTerrainCloses() {
        var device=new Device();
        try(var pass=new RestartableRenderPass(device::open)) {
            pass.runOutside(()->{assertEquals(0,device.active);try(var terrain=device.open()){terrain.draw(3,1,0,0);}});
            assertEquals(1,device.active);pass.draw(4,1,0,0);
        }
        assertEquals(0,device.active);
        assertEquals(List.of("open","close","open","draw","close","open","draw","close"),
            device.events.stream().map(Event::name).toList());
    }

    @Test void mainStateIsRestoredWithoutReadingGpuData() {
        var device=new Device();var pipeline=dummy(CompiledRenderPipeline.class);
        var data=slice();var indices=dummy(GpuBuffer.class);
        var texture=dummy(GpuTextureView.class);var sampler=dummy(GpuSampler.class);
        try(var pass=new RestartableRenderPass(device::open)) {
            pass.setPipeline(pipeline);pass.setUniform("Fog",data);pass.setUniform("Sampler0",texture,sampler);
            pass.setVertexBuffer(1,data);pass.setVertexBuffer(2,null);pass.setIndexBuffer(indices,IndexType.INT);
            pass.enableScissor(1,2,30,40);pass.pushDebugGroup(()->"main");
            var constants=ByteBuffer.allocateDirect(4).putInt(123).flip();pass.pushConstants(constants);
            byte[] expected=device.passes.getFirst().constants.clone();constants.putInt(0,999);
            pass.runOutside(()->assertEquals(0,device.active));
            var restored=device.passes.getLast();
            assertSame(data,restored.uniforms.get("Fog")[0]);
            assertSame(texture,restored.uniforms.get("Sampler0")[0]);
            assertSame(sampler,restored.uniforms.get("Sampler0")[1]);assertArrayEquals(expected,restored.constants);
            assertEquals(1,restored.groups);pass.popDebugGroup();
            assertTrue(device.events.stream().anyMatch(e->e.pass()==1&&e.name().equals("setPipeline")&&e.args()[0]==pipeline));
            assertTrue(device.events.stream().anyMatch(e->e.pass()==1&&e.name().equals("setVertexBuffer")&&e.args()[0].equals(2)&&e.args()[1]==null));
            assertTrue(device.events.stream().anyMatch(e->e.pass()==1&&e.name().equals("setIndexBuffer")&&e.args()[0]==indices));
            assertTrue(device.events.stream().anyMatch(e->e.pass()==1&&e.name().equals("enableScissor")&&e.args()[3].equals(40)));
        }
    }

    @Test void lastUniformAndScissorStateWinsIncludingNullTexture() {
        var device=new Device();var first=slice();var last=slice();
        try(var pass=new RestartableRenderPass(device::open)) {
            pass.setUniform("Fog",first);pass.setUniform("Fog",last);
            pass.setUniform("Sampler0",null,null);pass.enableScissor(0,0,2,2);pass.disableScissor();
            pass.runOutside(()->{});
            assertSame(last,device.passes.getLast().uniforms.get("Fog")[0]);
            assertNull(device.passes.getLast().uniforms.get("Sampler0")[0]);
            assertTrue(device.events.stream().anyMatch(e->e.pass()==1&&e.name().equals("disableScissor")));
        }
    }

    @Test void normalAndIndirectDrawParametersAreForwardedUnchanged() {
        var device=new Device();var commands=slice();var params=IntBuffer.wrap(new int[]{1,2,3});
        try(var pass=new RestartableRenderPass(device::open)) {
            pass.drawIndexed(11,12,13,14,15);pass.drawIndexedIndirect(commands,17);
            pass.drawIndirect(commands,19);pass.multiDraw(params,2,3,4);
            pass.multiDrawIndexed(params,5,6,7);
        }
        assertArrayEquals(new Object[]{11,12,13,14,15},device.events.get(1).args());
        assertSame(commands,device.events.get(2).args()[0]);assertEquals(17,device.events.get(2).args()[1]);
        assertSame(commands,device.events.get(3).args()[0]);assertSame(params,device.events.get(4).args()[0]);
        assertSame(params,device.events.get(5).args()[0]);
    }

    @Test void dynamicUploaderStateSurvivesMainRestart() {
        var device=new Device();var data=slice();var bytes=ByteBuffer.allocateDirect(4).putInt(321).flip();
        var seen=new AtomicInteger();Object arg=new Object();
        var draw=new RenderPass.Draw<Object>(0,dummy(GpuBuffer.class),null,null,7,8,9,(a,u)->{
            assertSame(arg,a);seen.incrementAndGet();u.setUniform("ChunkSection",data);u.pushConstants(bytes);
        });
        try(var pass=new RestartableRenderPass(device::open)) {
            pass.drawMultipleIndexed(List.of(draw),null,null,List.of("ChunkSection"),arg);
            byte[] expected=device.passes.getFirst().constants.clone();bytes.putInt(0,0);
            pass.runOutside(()->{});
            assertSame(data,device.passes.getLast().uniforms.get("ChunkSection")[0]);
            assertArrayEquals(expected,device.passes.getLast().constants);assertEquals(1,seen.get());
        }
    }

    @Test void workFailureRestoresMainButIsNeverReplayedOrHidden() {
        var device=new Device();var original=new IllegalStateException("terrain");var calls=new AtomicInteger();
        try(var pass=new RestartableRenderPass(device::open)) {
            var thrown=assertThrows(IllegalStateException.class,()->pass.runOutside(()->{calls.incrementAndGet();throw original;}));
            assertSame(original,thrown);assertTrue(pass.isUsable());assertEquals(1,calls.get());pass.draw(3,1,0,0);
        }
    }

    @Test void failedResumePreservesTerrainFailureAndClosesMain() {
        var device=new Device();device.failOpen=1;var original=new IllegalStateException("terrain");
        var pass=new RestartableRenderPass(device::open);
        var thrown=assertThrows(IllegalStateException.class,()->pass.runOutside(()->{throw original;}));
        assertSame(original,thrown);assertEquals("open",thrown.getSuppressed()[0].getMessage());
        assertFalse(pass.isUsable());assertEquals(0,device.active);pass.close();
    }

    @Test void failedStateRestoreClosesReplacementAndRejectsFurtherDrawing() {
        var device=new Device();var pass=new RestartableRenderPass(device::open);
        pass.setPipeline(dummy(CompiledRenderPipeline.class));device.failPipeline=1;
        assertThrows(IllegalStateException.class,()->pass.runOutside(()->{}));
        assertEquals(0,device.active);assertFalse(pass.isUsable());
        assertThrows(IllegalStateException.class,()->pass.draw(3,1,0,0));pass.close();
    }

    @Test void mainCannotBeDrawnClosedOrNestedWhileSuspended() {
        var device=new Device();try(var pass=new RestartableRenderPass(device::open)) {
            pass.runOutside(()->{
                assertThrows(IllegalStateException.class,()->pass.draw(3,1,0,0));
                assertThrows(IllegalStateException.class,pass::close);
                assertThrows(IllegalStateException.class,()->pass.runOutside(()->fail("nested work")));
            });
            assertTrue(pass.isUsable());
        }
    }

    @Test void closeIsIdempotentAndClosedPassRejectsCommands() {
        var device=new Device();var pass=new RestartableRenderPass(device::open);pass.close();pass.close();
        assertEquals(0,device.active);assertThrows(IllegalStateException.class,()->pass.draw(3,1,0,0));
        assertThrows(IllegalStateException.class,()->pass.runOutside(()->fail("closed work")));
    }

    @Test void stateTrackingLimitsRejectInsteadOfGrowingWithoutBound() {
        var device=new Device();try(var pass=new RestartableRenderPass(device::open)) {
            assertThrows(IllegalArgumentException.class,()->pass.setVertexBuffer(16,null));
            assertThrows(IllegalArgumentException.class,()->pass.pushConstants(ByteBuffer.allocateDirect(129)));
            var data=slice();for(int i=0;i<256;i++)pass.setUniform("u"+i,data);
            var events=device.events.size();
            assertThrows(IllegalStateException.class,()->pass.setUniform("overflow",data));
            assertEquals(events,device.events.size(),"budget rejection must precede GPU commands");
            pass.setUniform("u0",data);
        }
    }

    @Test void zeroLengthPushConstantsAreNotLostAcrossRestart() {
        var device=new Device();try(var pass=new RestartableRenderPass(device::open)) {
            pass.pushConstants(ByteBuffer.allocateDirect(0));pass.runOutside(()->{});
            assertArrayEquals(new byte[0],device.passes.getLast().constants);
        }
    }

    @Test void failedMainCloseDoesNotStartTerrainOrLeakAnotherPass() {
        var device=new Device();device.failClose=0;var pass=new RestartableRenderPass(device::open);
        assertThrows(IllegalStateException.class,()->pass.runOutside(()->fail("must not start terrain")));
        assertFalse(pass.isUsable());assertEquals(0,device.active);assertEquals(1,device.passes.size());pass.close();
    }

    @Test void fatalTerrainFailureStillRestoresMainBeforePropagating() {
        var device=new Device();var failure=new AssertionError("fatal");
        try(var pass=new RestartableRenderPass(device::open)) {
            assertSame(failure,assertThrows(AssertionError.class,()->pass.runOutside(()->{throw failure;})));
            assertTrue(pass.isUsable());assertEquals(1,device.active);
        }
    }
}
