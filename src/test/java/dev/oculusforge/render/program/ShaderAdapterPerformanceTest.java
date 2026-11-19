package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Guards the multiline-whitespace backtracking that stalled real pack loading. */
final class ShaderAdapterPerformanceTest {
    private static final String PADDING = "\t  \n".repeat(12_000)
        + "/* comment-only include line                                      */\n".repeat(2_000);

    @Test void vertexAdapterHandlesLongRunsOfBlankAndCommentLines() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var result = new LegacyFinalVertexAdapter().adapt("#version 120\n" + PADDING + """
                varying vec2 texcoord;
                void main() {
                    texcoord = gl_MultiTexCoord0.xy;
                    gl_Position = ftransform();
                }
                """);
            assertTrue(result.source().startsWith("#version 330 core"));
        });
    }

    @Test void fragmentAdapterHandlesLongRunsOfBlankAndCommentLines() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            var result = new LegacyFinalShaderAdapter().adapt("#version 120\n" + PADDING + """
                varying vec2 texcoord;
                uniform sampler2D colortex0;
                void main() { gl_FragColor = texture2D(colortex0, texcoord); }
                """);
            assertTrue(result.source().startsWith("#version 330 core"));
        });
    }
}

