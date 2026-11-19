package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NativeBindingDiagnosticsTest {
    private static final String V = "void main() { gl_Position = ftransform(); }";
    private static final String F = "void main() { gl_FragColor = vec4(1); }";

    @Test void vertexReportsUnboundNamesButNotSupportedWorldBindingsOrComments() {
        var error = assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            "// uniform float misleading;\nuniform int worldTime, worldDay;\n"
                + "uniform float frameTime, timeAngle, rainStrength;\n" + V.replace("ftransform()","ftransform()*timeAngle"), F, List.of(0)));
        assertTrue(error.getMessage().endsWith(": timeAngle"), error.getMessage());
    }

    @Test void fragmentReportsMissingUniformWithoutListingBoundAtlas() {
        var error = assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(V,
            "uniform sampler2D texture;\nuniform float timeAngle;\nuniform float rainStrength;\n"
                + "void main() { gl_FragColor = texture2D(texture, vec2(timeAngle)); }", List.of(0)));
        assertTrue(error.getMessage().endsWith(": timeAngle"), error.getMessage());
    }

    @Test void largeDeclarationListsHaveBoundedDiagnostics() {
        var declarations = new StringBuilder();
        var references = new StringBuilder("0.0");
        for (int i = 0; i < 100; i++) {
            declarations.append("uniform float missing").append(i).append(";\n");
            references.append("+missing").append(i);
        }
        var error = assertThrows(ShaderCompatibilityException.class, () -> NativeTerrainProgram.adapt(
            declarations + V.replace("ftransform()","ftransform()*("+references+")"), F, List.of(0)));
        assertTrue(error.getMessage().contains("missing11"));
        assertFalse(error.getMessage().contains("missing12"));
        assertTrue(error.getMessage().endsWith(", ..."));
        assertTrue(error.getMessage().length() < 400);
    }
}
