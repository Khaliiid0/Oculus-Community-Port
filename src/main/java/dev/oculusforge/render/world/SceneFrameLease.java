package dev.oculusforge.render.world;

/** CPU-only lifetime guard: an in-flight framebuffer owner closes only after scope release. */
final class SceneFrameLease<T> {
    private final T owner;
    private boolean closeRequested,released;
    SceneFrameLease(T owner) { this.owner=java.util.Objects.requireNonNull(owner); }
    boolean deferClose(T requestedOwner) {
        if(released||owner!=requestedOwner)return false;
        closeRequested=true;return true;
    }
    boolean release() {
        if(released)throw new IllegalStateException("HDR scene lifetime already released");
        released=true;return closeRequested;
    }
}
