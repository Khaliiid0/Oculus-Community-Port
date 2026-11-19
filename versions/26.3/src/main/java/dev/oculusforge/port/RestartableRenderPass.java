package dev.oculusforge.port;

import com.mojang.renderpearl.api.buffers.*;
import com.mojang.renderpearl.api.commands.*;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import java.nio.*;
import java.util.*;
import java.util.function.*;
import org.lwjgl.PointerBuffer;

/**
 * Sequential pass boundary, never a nested render pass. The opener must preserve
 * attachments (no repeated clears). Render-thread confined, no GPU readbacks.
 * Draw calls stay on the original normal/indirect implementation.
 */
public final class RestartableRenderPass implements RenderPass {
    private final Supplier<RenderPass> opener;
    private RenderPass pass;
    private boolean closed;
    private boolean outside;
    private CompiledRenderPipeline pipeline;
    private final LinkedHashMap<String, Binding> uniforms = new LinkedHashMap<>();
    private final GpuBufferSlice[] vertices = new GpuBufferSlice[MAX_VERTEX_BUFFERS];
    private int vertexMask;
    private GpuBuffer indices;
    private IndexType indexType;
    private ByteBuffer constants;
    private int constantSize;
    private int[] scissor;
    private boolean scissorSet;
    private final ArrayList<Supplier<String>> groups = new ArrayList<>();
    private interface Binding { void apply(RenderPass pass, String name); }
    private record BufferBinding(GpuBufferSlice value) implements Binding {
        public void apply(RenderPass p, String name) { p.setUniform(name, value); }
    }
    private record TextureBinding(GpuTextureView view, GpuSampler sampler) implements Binding {
        public void apply(RenderPass p, String name) { p.setUniform(name, view, sampler); }
    }

    public RestartableRenderPass(Supplier<RenderPass> opener) {
        this.opener = Objects.requireNonNull(opener);
        pass = Objects.requireNonNull(opener.get(), "render pass");
    }

    /** Temporarily gives the GPU to a sequence of fully closed terrain passes. */
    public void runOutside(Runnable work) {
        current();
        outside = true;
        var previous = pass; pass = null;
        try {
            ResourceDisposal.closeAll(() -> {
                for (int i = groups.size() - 1; i >= 0; i--) previous.popDebugGroup();
            }, previous);
        } catch (RuntimeException | Error failure) { outside = false; closed = true; throw failure; }
        Throwable failure = null;
        int restoredGroups = 0;
        try { work.run(); }
        catch (RuntimeException | Error problem) { failure = problem; }
        try {
            pass = Objects.requireNonNull(opener.get(), "resumed render pass");
            if (pipeline != null) pass.setPipeline(pipeline);
            for (var entry : uniforms.entrySet()) entry.getValue().apply(pass, entry.getKey());
            if (constants != null) pass.pushConstants(constants.duplicate().limit(constantSize));
            for (int slot = 0; slot < vertices.length; slot++)
                if ((vertexMask & 1 << slot) != 0) pass.setVertexBuffer(slot, vertices[slot]);
            if (indices != null) pass.setIndexBuffer(indices, indexType);
            if (scissorSet) {
                if (scissor == null) pass.disableScissor();
                else pass.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            }
            for (var group : groups) { pass.pushDebugGroup(group); restoredGroups++; }
        } catch (RuntimeException | Error restore) {
            closed = true;
            if (failure == null) failure = restore; else if (failure != restore) failure.addSuppressed(restore);
            if (pass != null) {
                final int toPop = restoredGroups;
                final var failed = pass;
                try { ResourceDisposal.closeAll(() -> {
                    for (int i = toPop - 1; i >= 0; i--) failed.popDebugGroup();
                }, failed); } catch (RuntimeException | Error cleanup) {
                    if (failure != cleanup) failure.addSuppressed(cleanup);
                } finally { pass = null; }
            }
        } finally { outside = false; }
        if (failure instanceof RuntimeException problem) throw problem;
        if (failure instanceof Error problem) throw problem;
    }

    private RenderPass current() {
        if (closed || outside || pass == null) throw new IllegalStateException("Main render pass is not available");
        return pass;
    }
    public boolean isUsable() { return !closed && !outside && pass != null; }

    private void record(String name, Binding binding) {
        checkUniformBudget(name);
        uniforms.put(name, binding);
    }
    private void checkUniformBudget(String name) {
        if (!uniforms.containsKey(name) && uniforms.size() >= 256)
            throw new IllegalStateException("Main-pass uniform tracking budget exceeded");
    }
    private void rememberConstants(ByteBuffer value) {
        int count = value.remaining();
        if (count > 128) throw new IllegalArgumentException("Push constants exceed RenderPearl's 128-byte limit");
        if (constants == null) constants = ByteBuffer.allocateDirect(128);
        constants.clear().put(value.duplicate()).flip(); constantSize = count;
    }
    @Override public void pushDebugGroup(Supplier<String> label) {
        if (groups.size() >= 64) throw new IllegalStateException("Main-pass debug-group budget exceeded");
        current().pushDebugGroup(label); groups.add(label);
    }
    @Override public void popDebugGroup() {
        current();
        if (groups.isEmpty()) throw new IllegalStateException("No main-pass debug group to pop");
        pass.popDebugGroup(); groups.removeLast();
    }
    @Override public void writeTimestamp(GpuQueryPool pool, int index) { current().writeTimestamp(pool, index); }
    @Override public void setPipeline(CompiledRenderPipeline value) { current().setPipeline(value); pipeline = value; }
    @Override public void setUniform(String name, GpuTextureView view, GpuSampler sampler) {
        checkUniformBudget(name);
        current().setUniform(name, view, sampler); record(name, new TextureBinding(view, sampler));
    }
    @Override public void setUniform(String name, GpuBuffer value) { setUniform(name, value.slice()); }
    @Override public void setUniform(String name, GpuBufferSlice value) {
        checkUniformBudget(name);
        current().setUniform(name, value); record(name, new BufferBinding(value));
    }
    @Override public void pushConstants(ByteBuffer value) {
        if (value.remaining() > 128) throw new IllegalArgumentException("Push constants exceed RenderPearl's 128-byte limit");
        current().pushConstants(value); rememberConstants(value);
    }
    @Override public void enableScissor(int x, int y, int w, int h) {
        current().enableScissor(x, y, w, h); scissor = new int[]{x,y,w,h}; scissorSet = true;
    }
    @Override public void disableScissor() { current().disableScissor(); scissor = null; scissorSet = true; }
    @Override public void setVertexBuffer(int slot, GpuBufferSlice value) {
        if (slot < 0 || slot >= vertices.length) throw new IllegalArgumentException("Invalid vertex slot");
        current().setVertexBuffer(slot, value); vertices[slot] = value; vertexMask |= 1 << slot;
    }
    @Override public void setIndexBuffer(GpuBuffer value, IndexType type) {
        current().setIndexBuffer(value, type); indices = value; indexType = type;
    }
    @Override public void drawIndexed(int a,int b,int c,int d,int e) { current().drawIndexed(a,b,c,d,e); }
    @Override public void multiDrawIndexed(IntBuffer a,int b,int c,int d) { current().multiDrawIndexed(a,b,c,d); }
    @Override public void multiDrawIndexed(PointerBuffer a,IntBuffer b,IntBuffer c,int d) { current().multiDrawIndexed(a,b,c,d); }
    @Override public void drawIndexedIndirect(GpuBufferSlice value,int count) { current().drawIndexedIndirect(value,count); }
    @Override public void draw(int a,int b,int c,int d) { current().draw(a,b,c,d); }
    @Override public void multiDraw(IntBuffer a,int b,int c,int d) { current().multiDraw(a,b,c,d); }
    @Override public void multiDraw(IntBuffer a,IntBuffer b,int c) { current().multiDraw(a,b,c); }
    @Override public void drawIndirect(GpuBufferSlice value,int count) { current().drawIndirect(value,count); }

    @Override public <T> void drawMultipleIndexed(Collection<Draw<T>> draws, GpuBuffer buffer,
        IndexType type, Collection<String> dynamicUniforms, T argument) {
        // Vanilla's uploader bypasses public setUniform; preserve its last state
        // for subsequent segments too, without copying or reading vertex data.
        Collection<Draw<T>> tracked = new AbstractCollection<>() {
            @Override public int size() { return draws.size(); }
            @Override public Iterator<Draw<T>> iterator() {
                var iterator = draws.iterator();
                return new Iterator<>() {
                    public boolean hasNext() { return iterator.hasNext(); }
                    public Draw<T> next() {
                        var draw = iterator.next();
                        if (draw.uniformUploaderConsumer() == null) return draw;
                        return new Draw<>(draw.slot(),draw.vertexBuffer(),draw.indexBuffer(),draw.indexType(),
                            draw.firstIndex(),draw.indexCount(),draw.baseVertex(),(arg,uploader) ->
                                draw.uniformUploaderConsumer().accept(arg, new UniformUploader() {
                                    public void setUniform(String name, GpuBufferSlice value) {
                                        checkUniformBudget(name);
                                        uploader.setUniform(name,value); record(name,new BufferBinding(value));
                                    }
                                    public void pushConstants(ByteBuffer value) {
                                        uploader.pushConstants(value); rememberConstants(value);
                                    }
                                }));
                    }
                };
            }
        };
        current().drawMultipleIndexed(tracked,buffer,type,dynamicUniforms,argument);
    }

    @Override public void close() {
        if (closed) return;
        if (outside) throw new IllegalStateException("Cannot close suspended main pass");
        closed = true;
        var retired = pass; pass = null;
        uniforms.clear(); groups.clear(); pipeline = null;
        Arrays.fill(vertices,null); indices = null; constants = null;
        if (retired != null) retired.close();
    }
}
