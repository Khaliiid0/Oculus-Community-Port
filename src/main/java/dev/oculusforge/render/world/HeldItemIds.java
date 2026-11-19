package dev.oculusforge.render.world;

/** Pack-defined signed integer IDs, not registry IDs or held-light values. */
public record HeldItemIds(int mainHand, int offHand) {
    public static final HeldItemIds NONE = new HeldItemIds(-1, -1);
}
