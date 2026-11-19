package dev.oculusforge.render.world;

/** Nest-safe state identity capture; never holds pack data or mutable BlockPos references. */
public final class MaterialVertexContext {
    private static final ThreadLocal<int[]> STATE = ThreadLocal.withInitial(() -> new int[1]);
    private MaterialVertexContext() { }

    public static int encode(int stateId, boolean fluid) {
        if (stateId < 0) return 0;
        if (stateId >= MaterialIdTable.MAX_STATES - 1) throw new IllegalArgumentException("Too many registered block states for material lookup");
        return fluid ? -(stateId + 1) : stateId + 1;
    }

    public static int enter(int encoded) { int[] state = STATE.get(); int previous = state[0]; state[0] = encoded; return previous; }
    public static void restore(int previous) { STATE.get()[0] = previous; }
    public static int current() { return STATE.get()[0]; }
}
