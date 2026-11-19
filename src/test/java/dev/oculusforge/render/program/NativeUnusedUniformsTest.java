package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeUnusedUniformsTest {
    @Test void excessiveUniformTablesFailBeforeBindingWork() {
        var source=new StringBuilder();
        for(int i=0;i<513;i++)source.append("uniform float unused").append(i).append(";\n");
        source.append(V);
        var error=assertThrows(ShaderCompatibilityException.class,()->NativeUnusedUniforms.adapt(source.toString()));
        assertTrue(error.getMessage().contains("512 uniform declarations"));
    }
    private static final String V="void main(){gl_Position=ftransform();}";
    private static final String F="void main(){gl_FragColor=vec4(1);}";

    @Test void unusedSharedDeclarationsAreRemovedWithoutChangingLinePositions() throws Exception {
        String prefix="uniform bool heavyFog = false;\nuniform float unusedCloudLimit=192.0;\nuniform int unusedItem;\nuniform sampler2D specular;\n";
        String pruned=NativeUnusedUniforms.adapt(prefix+V);
        assertEquals((prefix+V).length(),pruned.length());
        assertEquals((prefix+V).lines().count(),pruned.lines().count());
        assertEquals(V,pruned.strip());
        var p=NativeTerrainProgram.adapt(prefix+V,prefix+F,List.of(0));
        assertFalse(p.usesWorldMatrices());
        assertFalse(p.vertexSource().contains("heavyFog"));
    }

    @Test void activeInputsAndConservativeDeclarationReferencesAreNotInvented() throws Exception {
        for(String code:List.of("void main(){gl_Position=ftransform()*missing;}",
            "float global=missing;\n"+V,
            "struct Item{float missing;};\n"+V)) {
            String source="uniform float missing;\n"+code;
            assertEquals(source,NativeUnusedUniforms.adapt(source));
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(source,F,List.of(0)));
        }
    }

    @Test void invalidTypesArraysComplexDefaultsAndDuplicatesStillFail() {
        for(String decl:List.of("uniform int screenBrightness;", "uniform float sunAngle[2];",
            "uniform sampler2D shadowcolor0[2];", "uniform float shadowcolor1;",
            "uniform float missing[2];", "uniform float missing=someFunction();",
            "uniform float missing;\nuniform float missing;", "uniform float blindness=0.0;"))
            assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(decl+"\n"+V,F,List.of(0)),decl);
    }

    @Test void knownBindingsAndNonPlainConstructsAreNotPruned() throws Exception {
        for(String source:List.of("uniform float screenBrightness;\n"+V,
            "uniform sampler3D voxel;\n"+V,"uniform Example value;\n"+V,
            "#define INPUT missing\nuniform float missing;\n"+V))
            assertEquals(source,NativeUnusedUniforms.adapt(source));
        assertTrue(NativeUnusedUniforms.adapt("uniform float needed;\nuniform float other=needed;\n"+V).contains("uniform float needed;"));
    }

    @Test void activeTextureFeedbackRemainsForbiddenButUnusedVertexTexturesAreHarmless() throws Exception {
        var p=NativeTerrainProgram.adapt("uniform sampler2D shadowcolor1;\n"+V,F,List.of(0));
        assertFalse(p.usesShadowTextures());
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adaptShadow(V,
            "uniform sampler2D shadowcolor1;\nvoid main(){gl_FragColor=texture2D(shadowcolor1,vec2(.5));}",List.of(1)));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt(V,
            "uniform sampler2D colortex0;\nvoid main(){gl_FragColor=texture2D(colortex0,vec2(.5));}",List.of(0)));
    }
}
