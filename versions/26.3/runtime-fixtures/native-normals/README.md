# Static normal material atlas control (source audit only)

The authored `oculus-normal-audit-20261110` resource pack supplies a four-quadrant
stone `_n.png` (independent normal-X/Y, AO, height data) and a deliberately distinct
constant `_s.png`. No other sprite has maps. These are original numeric fixtures.

Native terrain and shadow programs verify raw normal RGBA, level-4 data-channel
mips, independently reconstructed bilinear interpolation, original albedo atlas
dimensions and distinct specular binding. Nonmapped sprite regions must retain
the Iris flat-normal default, never zero-filled normals. The final shader samples
actual terrain and shadow markers on a fixed grid: top-left cyan = exercised/pass,
magenta = mismatch, yellow = insufficient terrain/shadow coverage (not a pass).
The rest of the scene remains ordinary lit terrain; no fullscreen green tint.

`native-normals-default` must pass with no resource pack active (normal and specular
each collapse to their separately defined four-byte default). `native-normals`
requires actual mapped samples in both terrain and shadow. `native-normals-negative`
adds a deliberate red-channel error to the filtered normal value and must fail.
Do not count an inactive texture input or an attractive screenshot as validation.

Only static exact-size maps are supported here. Animated/nonempty metadata,
resized maps, unknown material formats and stale resource generations fail closed.
This does not claim complete material/POM effects or full shader-pack compatibility.

Reproduce resource data from the repository root (choose a NEW basename on each
generation; existing directories are refused):

```powershell
./gradlew.bat -p versions/26.3 generateNormalAuditResources --args=oculus-normal-audit-new --warning-mode=fail
```

The test-only generator creates the two original numeric PNGs and copies the
versioned metadata template. It does not download, replace or publish any assets.
