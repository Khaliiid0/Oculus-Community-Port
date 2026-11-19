package dev.oculusforge.port;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ResourceDisposalFailureTest {
    @Test void cleanupDoesNotMaskTheOriginalDrawFailure() {
        var draw=new IllegalStateException("draw");var cleanup=new IllegalArgumentException("finish");
        assertDoesNotThrow(()->ResourceDisposal.closeAfter(draw,()->{throw cleanup;}));
        assertSame(cleanup,draw.getSuppressed()[0]);
    }
    @Test void fatalCleanupIsPreservedAsSuppressedWhenAnotherFailureAlreadyExists() {
        var draw=new AssertionError("draw");var cleanup=new AssertionError("finish");
        ResourceDisposal.closeAfter(draw,()->{throw cleanup;});assertSame(cleanup,draw.getSuppressed()[0]);
    }
    @Test void cleanupWithoutAnEarlierFailureStillPropagates() {
        var cleanup=new IllegalStateException("finish");
        assertSame(cleanup,assertThrows(IllegalStateException.class,()->ResourceDisposal.closeAfter(null,()->{throw cleanup;})));
        var fatal=new AssertionError("fatal");
        assertSame(fatal,assertThrows(AssertionError.class,()->ResourceDisposal.closeAfter(null,()->{throw fatal;})));
    }
    @Test void selfSuppressionAndSuccessfulOrNullCleanupAreSafe() {
        var error=new IllegalStateException("same");
        assertDoesNotThrow(()->ResourceDisposal.closeAfter(error,()->{throw error;}));assertEquals(0,error.getSuppressed().length);
        assertDoesNotThrow(()->ResourceDisposal.closeAfter(error,null));
        assertDoesNotThrow(()->ResourceDisposal.closeAfter(null,()->{}));
    }
}
