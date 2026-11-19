# Resource material-format control (local audit only)

This authored control checks the selected Minecraft resource pack's format
macros in native terrain, terrain shadows, post vertex/fragment code and custom
uniform properties. It is not a shader pack for distribution or PBR lighting.

Copy into a new `oculus-26.3-material-format` runtime shader-pack directory.
Keep the same shader selected while changing `minecraft:optifine/texture.properties`
through resource reloads. The small upper-left panel requires actual terrain AND
shadow marker coverage and consistent resource-format codes across every stage:

| Winning resource declaration | Required panel |
| --- | --- |
| Absent / empty `format` | Cyan |
| `format=lab-pbr` | Orange |
| `format=lab-pbr/1.3` | Violet |
| Invalid/unsupported nonempty format | Rejected, vanilla fallback after asset reload |

Magenta means a mismatch; yellow means insufficient coverage and is not a pass.
The LabPBR branches also sample the real normal atlas, accepting only flat
missing-map defaults or the original four-quadrant numeric normal audit data.
The ordinary scene is not color multiplied. No FPS or optical parity is measured.

The sibling `material-format-negative` uses the same shaders except
`NEGATIVE_FORMAT=1`. It must turn magenta on both real terrain backends; a positive
panel after selecting that sibling would invalidate the detector.

Use only the isolated disposable audit world/resources, not player saves. Restore
the original resource declaration, resource-pack selection, backend and shader-off
settings afterward. Runtime fixtures must never be bundled in the mod JAR.
