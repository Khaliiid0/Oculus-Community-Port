# Explicit shader options (26.3 development scope)

Shader preparation can now apply a bounded subset of declared `#define` options
without changing a pack's files. This is not a full Iris settings screen or a
release announcement. The publication/default-launch hold remains in force.

Place a Java-properties settings file beside the selected pack, named by adding
`.txt` to the complete pack filename: `Example.zip.txt`, or `ExampleDirectory.txt`.
Oculus only reads this file; it never generates or overwrites a user's settings.
No file means unchanged defaults, with no option-discovery scan.

```properties
# Examples only: names and values must actually be declared by the selected pack.
ADVANCED_MATERIALS=true
MATERIAL_FORMAT=1
REFLECTION_RAIN=false
```

Settings are captured when preparing a selection (Apply shaders or resource
reload). They are not watched or read every frame. Editing while preparation is
already running does not promise an atomic latest-on-disk snapshot; apply/reload
again after the edit. The applied selection is retained in the prepared pipeline
and logged as `Oculus selected shader options`.

The supported subset follows the [documented define-option syntax](https://shaders.properties/current/reference/shadersproperties/shader_settings/):

- Boolean `#define OPTION` / `//#define OPTION`, with an `#ifdef`/`#ifndef`
  reference found in the shader sources. OFF comments the declaration; it does
  not define the macro as `0` or `false`.
- Single-token valued defines with an explicit `// [allowed values]` list.
  Only a declared value is accepted. Valued `true`/`false` tokens retain their
  values; they are not mistaken for valueless switches.
- Repeated declarations must agree on type, default and allowed values. Conflicts,
  unknown names, malformed sources/settings, duplicate escaped property keys and
  reserved renderer/capability names reject. Block-comment examples are not options.
- Only matching declaration lines change, in a private bounded source snapshot.
  Shader macros are not globally predefined ahead of their declarations.
  Conditional custom uniforms and block/item properties receive the same explicit
  selections, including known-absent OFF switches; unselected options remain unknown.

Limits: 16 KiB settings, 32 selected options, 64-character literal values, 512
listed distinct values per declaration (its declared default is also valid),
4,096 discovered options and a 16 MiB shader-source
scan (or the smaller existing expanded-source budget). Existing pack, include,
UTF-8, image, sampler and GPU admission limits remain enforced. A linked or
non-regular settings file is rejected. There are no writes, extraction, code
expressions or renderer-capability overrides in this workflow.

`const` settings, profile inheritance, program-disable tokens, arbitrary imports
and a GUI for changing these settings are not implemented. Large Iris exports
may exceed this deliberately bounded subset; do not call this full settings parity.
Discovery is conservative across shader files, not a replacement for Iris's full
include-graph option merger. Complex block-comment declaration boundaries are
left unsupported instead of rewritten.

For headless diagnostics, `auditShadercPacks` accepts repeatable
`--option=NAME=VALUE` flags before pack paths. These explicit CLI selections do
not automatically load adjacent user files. The CLI reports actual prepared
normal/specular consumers as well as compiler results. Neither is GPU/visual proof.

Profile scope is recorded in the dated option-profile report and the newer
[shared-wetness qualification](WETNESS_QUALIFICATION_2026_11_16.md). Full BSL rain
reflections now pass the scoped native checks; this does not certify every
profile or optical path. Never enable every feature merely because its name
appears in a settings file.
