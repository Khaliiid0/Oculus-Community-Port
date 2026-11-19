package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GlslCallArgumentsTest {
    @Test void balancesNestedCallsConstructorsArraysAndCommaExpressions() throws Exception {
        String source="foo(vec3(1,2,3), array[index(1,2)], (1,2), depthtex1)";
        var call=GlslCallArguments.find(source,Set.of("foo")).getFirst();
        assertEquals(4,call.arguments().size());
        assertEquals("foo",source.substring(call.start(),call.nameEnd()));
        assertEquals("array[index(1,2)]",call.arguments().get(1).text(source));
        assertEquals("(1,2)",call.arguments().get(2).text(source));
        assertEquals("depthtex1",call.arguments().get(3).text(source));
    }

    @Test void retainsNestedSelectedCallSpansAndIgnoresMemberAndPrefixLookalikes() throws Exception {
        String source="texture(depthtex1,vec2(texture(depthtex1,uv).r)); x.texture(depthtex1,uv); not_texture(depthtex1,uv);";
        var calls=GlslCallArguments.find(source,Set.of("texture"));
        assertEquals(2,calls.size());
        assertEquals("vec2(texture(depthtex1,uv).r)",calls.getFirst().arguments().get(1).text(source));
        assertTrue(calls.get(1).start()>calls.getFirst().start());
    }

    @Test void acceptsExactlyTheArgumentLimitAndHandlesWhitespaceAndEmptyCalls() throws Exception {
        String source="foo("+"v,".repeat(31)+"v);foo(  );";
        var calls=GlslCallArguments.find(source,Set.of("foo"));
        assertEquals(32,calls.getFirst().arguments().size());
        assertTrue(calls.getLast().arguments().isEmpty());
        assertThrows(ShaderCompatibilityException.class,()->GlslCallArguments.find("foo("+"v,".repeat(32)+"v)",Set.of("foo")));
    }

    @Test void rejectsUnclosedUnbalancedExcessivelyNestedAndOversizedInputs() {
        for(String source:new String[]{"foo(depthtex1", "foo(array[0)","foo(v])","foo(v})",
            "foo("+"(".repeat(64)+"depthtex1"+")".repeat(65)})
            assertThrows(ShaderCompatibilityException.class,()->GlslCallArguments.find(source,Set.of("foo")),source);
        assertThrows(ShaderCompatibilityException.class,()->GlslCallArguments.find(" ".repeat(GlslCallArguments.MAX_SOURCE+1),Set.of("foo")));
        assertThrows(ShaderCompatibilityException.class,()->GlslCallArguments.find("foo();".repeat(GlslCallArguments.MAX_CALLS+1),Set.of("foo")));
    }
}
