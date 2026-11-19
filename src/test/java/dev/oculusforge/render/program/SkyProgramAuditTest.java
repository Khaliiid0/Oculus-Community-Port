package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SkyProgramAuditTest {
    @TempDir Path directory;
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragData[0] = vec4(1); }";
    private ShaderPackDescriptor pack() {
        return new ShaderPackDescriptor("sky", "sky", directory, ShaderPackDescriptor.Kind.DIRECTORY);
    }
    private Path root() throws Exception { return Files.createDirectories(directory.resolve("shaders/world1")); }
    @Test void missingFragmentIsExplicitNotPrepared() throws Exception {
        Files.writeString(root().resolve("gbuffers_skytextured.vsh"), V);
        var result = new PackProgramCompiler(ShaderPackLimits.DEFAULT).auditSkyPrograms(pack());
        assertTrue(result.get("shaders/world1/gbuffers_skytextured.vsh").startsWith("UNSUPPORTED: Missing paired"));
    }
    @Test void reportsPairWithoutImplyingLiveSupport() throws Exception {
        Files.writeString(root().resolve("gbuffers_skytextured.vsh"), V);
        Files.writeString(root().resolve("gbuffers_skytextured.fsh"), F);
        var result = new PackProgramCompiler(ShaderPackLimits.DEFAULT).auditSkyPrograms(pack());
        assertEquals(1, result.size());
        assertTrue(result.values().iterator().next().startsWith("PAIR CPU PREPARED"));
        assertTrue(result.values().iterator().next().contains("no runtime"));
    }
    @Test void honorsDeclaredFragmentOutputLayout() throws Exception {
        Files.writeString(root().resolve("gbuffers_skytextured.vsh"), V);
        Files.writeString(root().resolve("gbuffers_skytextured.fsh"), "/* DRAWBUFFERS:1 */\n" + F);
        var result = new PackProgramCompiler(ShaderPackLimits.DEFAULT).auditSkyPrograms(pack());
        assertTrue(result.values().iterator().next().startsWith("UNSUPPORTED: Native sky requires only colortex0"));
    }
}
