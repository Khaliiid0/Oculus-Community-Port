package dev.oculusforge.port;

/** Attempts every cleanup, retaining the first failure and suppressing later ones. */
public final class ResourceDisposal {
    private ResourceDisposal() {}

    /** A finally-block cleanup must not replace the draw/prepare failure. */
    public static void closeAfter(Throwable primary,AutoCloseable resource) {
        if(primary==null){closeAll(resource);return;}
        try{closeAll(resource);}catch(RuntimeException|Error cleanup){if(cleanup!=primary)primary.addSuppressed(cleanup);}
    }

    public static void closeAll(AutoCloseable... resources) {
        Throwable failure = null;
        for (var resource : resources) if (resource != null) {
            try { resource.close(); }
            catch (Throwable cleanup) {
                if (failure == null) failure = cleanup;
                else if (failure != cleanup) failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw new IllegalStateException("Resource cleanup failed", failure);
    }
}
