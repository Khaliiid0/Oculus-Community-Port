package dev.oculusforge.render.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeTextureBindingCapacityTest {
    private static final String V = "void main(){gl_Position=ftransform();}";

    private static String fragment(int colors, boolean specular) {
        var source = new StringBuilder();
        for (int i = 1; i <= colors; i++) source.append("uniform sampler2D colortex").append(i).append(";\n");
        if (specular) source.append("uniform sampler2D specular;\n");
        source.append("void main(){vec4 c=vec4(0);");
        for (int i = 1; i <= colors; i++) source.append("c+=texture2D(colortex").append(i).append(",vec2(.5));");
        if (specular) source.append("c+=texture2D(specular,vec2(.5));");
        return source.append("gl_FragColor=c;}").toString();
    }

    @Test void sixteenSlotsIncludeBothInheritedTerrainBindingsInBothMeshes() throws Exception {
        for (boolean extended : List.of(false, true)) {
            var program = NativeTerrainProgram.adaptWithMesh(V, fragment(13, true), List.of(0), false, extended);
            assertEquals(new TextureBindingCapacity.NativeCounts(16, 14, 0), TextureBindingCapacity.nativeCounts(program));
            assertEquals(NativeTerrainProgram.hash(program.vertexSource() + '\0' + program.fragmentSource() + '\0' + List.of(0)),
                program.fingerprint()); // Admission does not rewrite generated GLSL.
        }
    }

    @Test void aSeventeenthBindingRejectsBeforeNativeActivationInBothMeshes() {
        for (boolean extended : List.of(false, true)) {
            var failure = assertThrows(ShaderCompatibilityException.class,
                () -> NativeTerrainProgram.adaptWithMesh(V, fragment(14, true), List.of(0), false, extended));
            assertTrue(failure.getMessage().contains("distinct=17"));
        }
    }

    @Test void vertexMaterialLookupConsumesItsOwnSlot() throws Exception {
        String vertex = "attribute vec2 mc_Entity;\nvoid main(){gl_Position=ftransform()+vec4(mc_Entity,0,0);}";
        var program = NativeTerrainProgram.adaptWithMesh(vertex, fragment(12, true), List.of(0), false, true);
        assertEquals(new TextureBindingCapacity.NativeCounts(16, 13, 1), TextureBindingCapacity.nativeCounts(program));
        var failure = assertThrows(ShaderCompatibilityException.class,
            () -> NativeTerrainProgram.adaptWithMesh(vertex, fragment(13, true), List.of(0), false, true));
        assertTrue(failure.getMessage().contains("distinct=17"));
    }

    @Test void unusedSpecularAndCommentedSamplersDoNotConsumeExtraSlots() throws Exception {
        var program = NativeTerrainProgram.adapt(V, "uniform sampler2D specular;\n"
            + "/* uniform sampler2D fake; */\n" + fragment(14, false), List.of(0));
        assertFalse(program.usesSpecular());
        assertEquals(new TextureBindingCapacity.NativeCounts(16, 14, 0), TextureBindingCapacity.nativeCounts(program));
    }

    @Test void inheritedNamesAreDeduplicatedAndShadowIsCounted() throws Exception {
        var program = NativeTerrainProgram.adaptShadow(V, "uniform sampler2D texture;\nuniform sampler2D lightmap;\n"
            + "uniform sampler2D specular;\nvoid main(){gl_FragColor=texture2D(texture,vec2(.5))"
            + "+texture2D(lightmap,vec2(.5))+texture2D(specular,vec2(.5));}", List.of(0));
        assertEquals(new TextureBindingCapacity.NativeCounts(3, 3, 0), TextureBindingCapacity.nativeCounts(program));
    }
}
