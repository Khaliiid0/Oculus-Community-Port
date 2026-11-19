# Minecraft 26.3 module — license and source notes

This unofficial community port is designated `LGPL-3.0-only`. The binary and
sources JARs carry the full LGPLv3 text (`LICENSE-oculus-forge.txt`), its
accompanying GPLv3 text (`LICENSE-GPL-3.0.txt`), this file and the module NOTICE.
Including GPLv3's text is not a relicensing of the project to GPL-only.

## Recorded source provenance

- Supplied Oculus 1.16.5 source: LGPL-3.0.
- Oculus `1.20.1-new`, inspected for Forge integration patterns:
  `b3b278134f719afe32ba8b6b5d3a93f052175afc`.
- Iris `26.2`, inspected for preprocessing, rendering semantics and mappings:
  `8f3a7a35d780fe80c8cd3c8517f3fa3c4df3f18a`.
- `GlslPreprocessor` adapts the marker/listener technique from Iris
  `JcppProcessor` / `GlslCollectingListener` at that LGPL-3.0-only revision.
- JCPP 1.4.14 is bundled/relocated for conditional preprocessing under Apache-2.0;
  its complete license is at `META-INF/licenses/JCPP-LICENSE.txt` in both JARs.
- Embeddium `20.1/forge` was inspected as an architecture reference:
  `dee0ebde6aea8b158613cd3b8ea5cdf0c1f6a9bf`.

No Sodium implementation, AGPL glsl-transformer implementation/binary, Rubidium,
prebuilt JARs from the supplied archive, or external shader-pack assets are
bundled. No NeoForge artifact is represented as a Forge dependency. New source
adaptations must record their origin, revision, files and license.

The safety code, frame-graph integration, GPU resource management, uniform block
and bounded legacy adapters were independently written for this port. No file
in render/program or render/pipeline is a verbatim copy of the inspected projects.
The icon is original AI-assisted branding, not the upstream Oculus/Iris logo.

## Exact corresponding-source context

This module targets **Minecraft 26.3 / Forge 66.1.2 / Java 25**. Build the module
under `versions/26.3` together with shared root Java/resources, the Gradle wrapper,
authored tests and third-party notices. See `versions/26.3/SOURCE_BUILD.md` in the
complete source tree. Root 26.2 and versions/1.21.11 are separate targets, not
substitutes for the complete corresponding 26.3 build inputs.

For 0.4.0-beta.4, the corresponding source target is the separate branch
https://github.com/Khaliiid0/Oculus-Community-Port/tree/release/26.3-0.4.0-beta.4
Keep the full repository layout from that branch, not just this module. A local
source ZIP or sources JAR alone does not prove public availability. Distribution
requires verifying this exact source revision and final binary. Private checkpoint
artifacts remain private and must not be renamed into public release files.

Upstream repository links provide attribution, not support/issue ownership for
this unofficial port. This packaging check is not a general legal-compliance
certification.
