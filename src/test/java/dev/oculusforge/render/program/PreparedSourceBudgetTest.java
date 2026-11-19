package dev.oculusforge.render.program;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class PreparedSourceBudgetTest {
    @Test void exactAggregateLimitIsAcceptedAndOneMoreCharacterIsRejected() throws Exception {
        long limit = PackProgramCompiler.MAX_PREPARED_SOURCE_CHARS;
        assertEquals(limit, PackProgramCompiler.addPreparedSourceChars(limit - 2, 1, 1));
        assertThrows(ShaderCompatibilityException.class,
            () -> PackProgramCompiler.addPreparedSourceChars(limit - 1, 1, 1));
    }

    @Test void invalidOrOverflowingAccountingCannotWrapTheBudget() {
        assertThrows(IllegalArgumentException.class, () -> PackProgramCompiler.addPreparedSourceChars(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackProgramCompiler.addPreparedSourceChars(0, -1, 0));
        assertThrows(ShaderCompatibilityException.class,
            () -> PackProgramCompiler.addPreparedSourceChars(Long.MAX_VALUE, 1, 0));
    }
}
