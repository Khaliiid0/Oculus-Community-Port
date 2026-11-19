# Auxiliary history GPU probe

Internal 26.2 world-bridge test, not a shader pack for publication.
The scene is blue for 120 frames after allocation/reset, then returns to normal.
Persistent blue means the counter is not retained; magenta means a clear value,
RGB implicit alpha, default colortex1 value, or stored HDR channel is incorrect.

Exercise shader reload, resize, and leaving/re-entering the disposable test world.
Each should allocate fresh storage and restart the blue warm-up. No diagnostic
overlay is enabled by the production mod; this fixture is excluded from its JAR.
