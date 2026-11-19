package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeMultilineParametersTest {
    @Test void leadingParameterWhitespaceIsIncludedInValidationOffsets() throws Exception {
        for(String space:List.of(" ","\n                  ","\n\t\t")) {
            String source="void material(out float first,"+space+"inout float emission,"+space+"inout float subsurface,"+space+"out vec3 normalMap)"
                +"{first=1.;emission+=1.;subsurface+=1.;normalMap=vec3(1);}\n"
                +"void main(){float a=0.,b=0.,c=0.;vec3 n;material(a,b,c,n);gl_FragColor=vec4(n+a+b+c,1);}";
            var stage=NativeStageInterface.parse(source,false);
            assertEquals(source,stage.source(),"Validation must not alter executable function qualifiers");
            String expected=java.util.regex.Pattern.compile("\\b(?:in|out|inout)\\b").matcher(source).replaceAll(m->" ".repeat(m.group().length()));
            assertEquals(expected,stage.validationSource(),"Only the exact qualifier characters are masked");
            assertNotNull(NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",source,List.of(0)));
        }
    }
    @Test void multilineMalformedParametersAndRealStageInputsStillReject() {
        for(String parameter:List.of("in out vec3 value","const out vec3 value","in inout vec3 value","in uniform vec3 value"))
            assertThrows(ShaderCompatibilityException.class,()->NativeStageInterface.parse("void helper(float a,\n    "+parameter+"){}\nvoid main(){}",false));
        assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",
            "void helper(float a,\n    inout float b){b+=a;}\nin vec3 unbound;\nvoid main(){float a=1.;helper(1.,a);gl_FragColor=vec4(unbound*a,1);}",List.of(0)));
        var missing=assertThrows(ShaderCompatibilityException.class,()->NativeTerrainProgram.adapt("void main(){gl_Position=ftransform();}",
            "uniform float thunderStrength;\nvoid main(){gl_FragColor=vec4(thunderStrength);}",List.of(0)));
        assertTrue(missing.getMessage().contains("thunderStrength"),"A valid parameter fix must not fabricate unsupported native weather inputs");
    }
}
