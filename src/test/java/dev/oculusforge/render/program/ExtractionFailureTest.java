package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.oculusforge.render.pipeline.OculusCompositePass;
import dev.oculusforge.render.pipeline.ShaderPipelineManager;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class ExtractionFailureTest {
    @Test void extractionFailureBeforeResourceWritesCannotBeCulled() throws Exception {
        var manager = new ShaderPipelineManager(null);
        var active = ShaderPipelineManager.class.getDeclaredField("active");
        active.setAccessible(true);
        active.set(manager, new PackShaderPipeline("test", Map.of()));
        var composite = new OculusCompositePass(manager);
        var graph = new FrameGraphBuilder();
        var pass = graph.addPass("extraction failure");
        // No Minecraft singleton exists in this CPU test: dimension lookup fails
        // before any resource writes, taking the same recovery path as budget checks.
        composite.extracts(null, pass, null);
        var failure = OculusCompositePass.class.getDeclaredField("extractionFailure");
        failure.setAccessible(true);
        assertInstanceOf(RuntimeException.class, failure.get(composite));
        var ran = new AtomicBoolean();
        pass.executes(() -> ran.set(true));
        graph.execute(GraphicsResourceAllocator.UNPOOLED);
        assertTrue(ran.get(), "The recovery callback must survive framegraph culling");
    }
}
