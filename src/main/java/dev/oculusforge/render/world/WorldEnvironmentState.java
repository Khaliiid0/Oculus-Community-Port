package dev.oculusforge.render.world;

public interface WorldEnvironmentState {
    WorldEnvironment oculus$getEnvironment();
    void oculus$setEnvironment(WorldEnvironment value);

    default WorldEnvironment oculus$requireEnvironment() {
        var environment = oculus$getEnvironment();
        if (environment == null) throw new IllegalStateException("Camera environment has not been extracted for this frame");
        return environment;
    }
}
