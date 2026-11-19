package dev.oculusforge.render.program;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable per-frame routing. Main color is read-only until the final copy.
 * Inputs come from this frame's world attachments or an earlier post stage.
 * Retained auxiliary outputs are pinned until they are committed after all stages.
 */
public record ColorBufferPlan(List<Step> steps, int scratchCount, int presentedSlot, Map<Integer, ColorBufferFormat> borrowedColorFormats,
                              List<ColorBufferFormat> scratchFormats, Map<Integer, Integer> retainedColorSlots) {
    public static final int MAIN = -1;
    public static final int SCREEN = -1;
    public static final int MAX_BUFFERS = 16;
    public static final long MAX_SCRATCH_BYTES = 128L * 1024 * 1024;

    public ColorBufferPlan {
        steps = List.copyOf(steps);
        borrowedColorFormats = Map.copyOf(borrowedColorFormats);
        scratchFormats = List.copyOf(scratchFormats);
        retainedColorSlots = Map.copyOf(retainedColorSlots);
        if (scratchFormats.size() != scratchCount) throw new IllegalArgumentException("Each scratch slot needs a format");
    }

    public ColorBufferPlan(List<Step> steps, int scratchCount, int presentedSlot,
                           Map<Integer, ColorBufferFormat> borrowedColorFormats, List<ColorBufferFormat> scratchFormats) {
        this(steps, scratchCount, presentedSlot, borrowedColorFormats, scratchFormats, Map.of());
    }

    public ColorBufferPlan(List<Step> steps, int scratchCount, int presentedSlot, Map<Integer, ColorBufferFormat> borrowedColorFormats) {
        this(steps, scratchCount, presentedSlot, borrowedColorFormats, java.util.Collections.nCopies(scratchCount, ColorBufferFormat.RGBA8));
    }

    public ColorBufferPlan(List<Step> steps, int scratchCount, int presentedSlot) {
        this(steps, scratchCount, presentedSlot, Map.of());
    }

    public static ColorBufferPlan create(List<PreparedFinalProgram> programs) throws ShaderCompatibilityException {
        return create(programs, java.util.Set.of());
    }

    public static ColorBufferPlan create(List<PreparedFinalProgram> programs, java.util.Set<Integer> initialBuffers)
        throws ShaderCompatibilityException {
        return create(programs, initialBuffers, programs.isEmpty() ? ColorBufferFormats.DEFAULT : programs.getFirst().formats());
    }

    public static ColorBufferPlan create(List<PreparedFinalProgram> programs, java.util.Set<Integer> initialBuffers,
                                         ColorBufferFormats formats) throws ShaderCompatibilityException {
        final int unset = Integer.MIN_VALUE;
        final int initialBase = -100;
        int[] latest = new int[MAX_BUFFERS];
        Arrays.fill(latest, unset);
        latest[0] = MAIN;
        initialBuffers.stream().sorted().forEach(buffer -> {
            if (buffer <= 0 || buffer >= MAX_BUFFERS) throw new IllegalArgumentException("Invalid initial color buffer " + buffer);
            latest[buffer] = initialBase - buffer;
        });

        List<Integer> lastUse = new ArrayList<>();
        Map<Integer, ColorBufferFormat> borrowed = new java.util.LinkedHashMap<>();
        List<ColorBufferFormat> versionFormats = new ArrayList<>();
        List<Map<String, Integer>> inputVersions = new ArrayList<>();
        List<List<Integer>> outputVersions = new ArrayList<>();
        int presentedVersion = MAIN;
        for (int index = 0; index < programs.size(); index++) {
            PreparedFinalProgram program = programs.get(index);
            Map<String, Integer> inputs = new HashMap<>();
            for (LegacyFinalShaderAdapter.SamplerBinding binding : program.fragment().samplers()) {
                if (binding.texture() != LegacyFinalShaderAdapter.TextureSemantic.COLOR) continue;
                if (binding.colorIndex() < 0 || binding.colorIndex() >= MAX_BUFFERS) {
                    throw new ShaderCompatibilityException("Invalid color input " + binding.name());
                }
                int version = latest[binding.colorIndex()];
                if (version == unset) {
                    throw new ShaderCompatibilityException(program.stageName() + " reads " + binding.name()
                        + " (colortex" + binding.colorIndex() + ") before a supported stage writes it; "
                        + "no initial world attachment is available here");
                }
                inputs.put(binding.name(), version);
                if (version >= 0) lastUse.set(version, index);
                else if (version <= initialBase) borrowed.put(initialBase - version, formats.get(initialBase - version));
            }
            inputVersions.add(inputs);
            List<Integer> stageVersions = new ArrayList<>();
            boolean screen = program.outputBuffers().contains(SCREEN);
            if (screen && (program.outputBuffers().size() != 1 || index != programs.size() - 1)) {
                throw new ShaderCompatibilityException("The final screen pass must be the only output of the last stage");
            }
            for (int output : program.outputBuffers()) {
                if (output < SCREEN || output >= MAX_BUFFERS) throw new ShaderCompatibilityException("Invalid color output for " + program.stageName());
                int version = lastUse.size();
                lastUse.add(index);
                versionFormats.add(program.formats().get(output));
                stageVersions.add(version);
                if (output == SCREEN) presentedVersion = version;
                else latest[output] = version;
            }
            if (!screen) presentedVersion = latest[0];
            outputVersions.add(List.copyOf(stageVersions));
        }
        if (presentedVersion >= 0) lastUse.set(presentedVersion, programs.size());
        Map<Integer, Integer> retainedVersions = new java.util.LinkedHashMap<>();
        for (int buffer : initialBuffers.stream().sorted().toList()) {
            if (!formats.shouldClear(buffer) && latest[buffer] >= 0) {
                retainedVersions.put(buffer, latest[buffer]);
                lastUse.set(latest[buffer], programs.size());
            }
        }

        // World attachments already exist and remain read-only during post processing.
        // Borrow them directly; only post outputs need scratch storage. A post write
        // changes the logical producer without ever overwriting the borrowed texture.
        int[] versionSlots = new int[lastUse.size()];
        List<Integer> slotLastUses = new ArrayList<>();
        List<ColorBufferFormat> slotFormats = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (int index = 0; index < programs.size(); index++) {
            Map<String, Integer> inputs = new HashMap<>();
            Map<String, Integer> worldInputs = new HashMap<>();
            for (var entry : inputVersions.get(index).entrySet()) {
                int version = entry.getValue();
                if (version == MAIN) inputs.put(entry.getKey(), MAIN);
                else if (version <= initialBase) worldInputs.put(entry.getKey(), initialBase - version);
                else inputs.put(entry.getKey(), versionSlots[version]);
            }
            List<Integer> outputs = new ArrayList<>();
            for (int version : outputVersions.get(index)) {
                int target = 0;
                while (target < slotLastUses.size() && (slotLastUses.get(target) >= index
                    || slotFormats.get(target) != versionFormats.get(version))) target++;
                if (target == slotLastUses.size()) {
                    slotLastUses.add(lastUse.get(version));
                    slotFormats.add(versionFormats.get(version));
                }
                else slotLastUses.set(target, lastUse.get(version));
                versionSlots[version] = target;
                outputs.add(target);
            }
            steps.add(new Step(inputs, outputs, worldInputs));
        }
        Map<Integer, Integer> retainedSlots = new java.util.LinkedHashMap<>();
        retainedVersions.forEach((buffer, version) -> retainedSlots.put(buffer, versionSlots[version]));
        return new ColorBufferPlan(steps, slotLastUses.size(),
            presentedVersion == MAIN ? MAIN : versionSlots[presentedVersion], borrowed, slotFormats, retainedSlots);
    }

    public long scratchBytes(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid post-processing target size");
        long bytesPerPixel = scratchFormats.stream().mapToLong(ColorBufferFormat::bytesPerPixel).sum();
        return Math.multiplyExact(Math.multiplyExact((long) width, height), bytesPerPixel);
    }

    public void checkMemoryBudget(int width, int height) {
        requireBudget(scratchBytes(width, height));
    }

    public void checkMemoryBudget(int width, int height, int bytesPerPixel) {
        if (width <= 0 || height <= 0 || bytesPerPixel <= 0) {
            throw new IllegalArgumentException("Invalid post-processing target size or format");
        }
        long pixels = Math.multiplyExact((long) width, height);
        long bytes = Math.multiplyExact(Math.multiplyExact(pixels, bytesPerPixel), scratchCount);
        requireBudget(bytes);
    }

    private static void requireBudget(long bytes) {
        if (bytes > MAX_SCRATCH_BYTES) {
            throw new IllegalStateException("Post-processing needs " + (bytes / (1024 * 1024))
                + " MiB of scratch color buffers; the safety limit is 128 MiB. Reduce the window resolution or use a simpler pack.");
        }
    }

    public record Step(Map<String, Integer> colorInputs, List<Integer> outputSlots, Map<String, Integer> worldColorInputs) {
        public Step(Map<String, Integer> colorInputs, List<Integer> outputSlots) { this(colorInputs, outputSlots, Map.of()); }
        public Step(Map<String, Integer> colorInputs, int outputSlot) { this(colorInputs, List.of(outputSlot)); }
        public Step {
            colorInputs = Map.copyOf(colorInputs);
            worldColorInputs = Map.copyOf(worldColorInputs);
            outputSlots = List.copyOf(outputSlots);
            if (outputSlots.isEmpty()) throw new IllegalArgumentException("A post stage needs at least one output slot");
            if (!java.util.Collections.disjoint(colorInputs.keySet(), worldColorInputs.keySet()))
                throw new IllegalArgumentException("A sampler cannot read two producers");
            if (worldColorInputs.values().stream().anyMatch(index -> index <= 0 || index >= MAX_BUFFERS))
                throw new IllegalArgumentException("Invalid borrowed world attachment");
            if (outputSlots.stream().anyMatch(slot -> slot < 0))
                throw new IllegalArgumentException("Post outputs must use scratch storage");
        }
        public int outputSlot() { return outputSlots.getFirst(); }
    }
}
