# Packaged-artifact smoke harness

This separate ForgeGradle project has no Oculus source set and no test mod. It stages one
already-built production JAR into an isolated run-<minecraft>/mods directory and launches
Forge with that file. This catches missing or incorrectly shaded classes, metadata errors and
differences hidden by the main project's source-set userdev launch.

Example from the repository root:

    .\gradlew.bat -p tools/artifact-smoke runClient -PsmokeMinecraft=26.2 -PsmokeForge=65.1.0 -PsmokeJava=25 -PoculusArtifact=../../build/libs/oculus-forge-26.2-0.4.0-beta.2.jar

Use the matching JDK. The harness is still Forge's development bootstrap, but only the packaged
Oculus JAR is loaded; it is stronger artifact evidence than the source-set lifecycle client and
does not claim to be a third-party launcher or modpack test.

Add `-PsmokeRunTag=world-20260904` to use a fresh run directory without replacing a previous
test artifact or world. Tags accept only letters, digits, underscores and hyphens.
