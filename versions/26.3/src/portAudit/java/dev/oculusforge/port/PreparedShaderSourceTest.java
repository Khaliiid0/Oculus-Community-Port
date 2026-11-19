package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PreparedShaderSourceTest {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("oculus", "audit");

    @Test void sameIdentifierCanSelectDifferentStages() {
        try (var source = PreparedShaderSource.pair(ID, "vertex", ID, "fragment")) {
            assertEquals("vertex", source.getShader(ID, ShaderType.VERTEX));
            assertEquals("fragment", source.getShader(ID, ShaderType.FRAGMENT));
            assertNull(source.getShader(Identifier.withDefaultNamespace("unknown"), ShaderType.VERTEX));
        }
    }

    @Test void callerMutationsCannotChangePreparedSources() {
        var shaders = new HashMap<Identifier, String>();
        shaders.put(ID, "original");
        var stages = new HashMap<ShaderType, Map<Identifier, String>>();
        stages.put(ShaderType.VERTEX, shaders);
        try (var source = new PreparedShaderSource(stages)) {
            shaders.put(ID, "changed");
            stages.clear();
            assertEquals("original", source.getShader(ID, ShaderType.VERTEX));
        }
    }

    @Test void includesNeverEscapeToGlobalResources() {
        try (var source = PreparedShaderSource.pair(ID, "vertex", ID, "fragment")) {
            assertNull(source.getInclude(Identifier.fromNamespaceAndPath("minecraft", "shaders/include/fog.glsl")));
            assertNull(source.getInclude(ID));
        }
    }

    @Test void closeIsIdempotentAndRejectsStaleReads() {
        var source = PreparedShaderSource.pair(ID, "vertex", ID, "fragment");
        source.close();
        source.close();
        assertTrue(source.isClosed());
        assertThrows(IllegalStateException.class, () -> source.getShader(ID, ShaderType.VERTEX));
        assertThrows(IllegalStateException.class, () -> source.getInclude(ID));
    }

    @Test void rejectsNullSourcesInsteadOfDeferringFailureToNativeCompiler() {
        var invalid = new HashMap<Identifier, String>();
        invalid.put(ID, null);
        assertThrows(NullPointerException.class, () -> new PreparedShaderSource(Map.of(ShaderType.VERTEX, invalid)));
    }

    @Test void enforcesCombinedCharacterBudgetAcrossStages() {
        String half = "x".repeat(PreparedShaderSource.MAX_SOURCE_CHARS / 2);
        try (var source = PreparedShaderSource.pair(ID, half, ID, half)) {
            assertEquals(half.length(), source.getShader(ID, ShaderType.FRAGMENT).length());
        }
        assertThrows(IllegalArgumentException.class,
            () -> PreparedShaderSource.pair(ID, half, ID, half + "x"));
    }

    @Test void enforcesStageCountBudget() {
        var stages = new HashMap<Identifier, String>();
        for (int i = 0; i <= PreparedShaderSource.MAX_STAGES; i++)
            stages.put(Identifier.fromNamespaceAndPath("oculus", "stage_" + i), "");
        assertThrows(IllegalArgumentException.class,
            () -> new PreparedShaderSource(Map.of(ShaderType.FRAGMENT, stages)));
    }
}
