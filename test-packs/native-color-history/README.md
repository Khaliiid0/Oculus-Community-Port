# Native auxiliary history probe

Terrain reads gaux2/colortex5, which only composite writes, with Clear=false.
First frame may be red (initialized empty); subsequent terrain should be green.
Change copied composite output to red for a negative control: terrain must become
red after reload. Restore green, reload and verify green again. Also resize and
re-enter copied world to test history initialization. Inspect actual terrain, not HUD
or sky. This is a diagnostic, not real BSL reflection or material certification.

Packaged AMD GPU run passed positive/negative/reload, resize and world re-entry on
2026-10-14. See NATIVE_COLOR_GPU_PROGRESS.md for evidence and limitations.
