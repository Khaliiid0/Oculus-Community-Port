package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.oculusforge.shaderpack.*;

class NativeInputAuditTest {
    @Test void vertexTextureReferencesAreReportedWithoutGrantingVertexSampling(@TempDir Path root) throws Exception {
        var dir=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(dir.resolve("shadow.vsh"),"uniform sampler2D noisetex;\nuniform sampler2D tex;\n"
            +"void main(){gl_Position=ftransform()+texture2D(noisetex,vec2(.5));}");
        var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        var inputs=compiler.auditNativeInputs(new ShaderPackDescriptor("audit","Audit",root,ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(java.util.List.of("sampler2D noisetex: REFERENCED","sampler2D tex: UNREFERENCED"),inputs.get("shaders/shadow.vsh"));
        assertEquals(0,compiler.cachedPipelineCount());
    }
    @Test void distinguishesDirectReferencesUnusedDefaultsAndHelperOnlyReferences(@TempDir Path root) throws Exception {
        var dir=Files.createDirectories(root.resolve("shaders"));
        Files.writeString(dir.resolve("shadow.vsh"),"uniform bool heavyFog=false;\nuniform float active;\n"
            +"uniform float helperOnly;\nfloat dead(){return helperOnly;}\nvoid main(){gl_Position=ftransform()*active;}");
        var compiler=new PackProgramCompiler(ShaderPackLimits.DEFAULT);
        var result=compiler.auditNativeInputs(new ShaderPackDescriptor("audit","Audit",root,ShaderPackDescriptor.Kind.DIRECTORY));
        assertEquals(java.util.List.of("bool heavyFog: UNREFERENCED","float active: REFERENCED",
            "float helperOnly: UNREACHABLE-HELPER-ONLY"),result.get("shaders/shadow.vsh"));
        assertEquals(0,compiler.cachedPipelineCount());
    }
}
