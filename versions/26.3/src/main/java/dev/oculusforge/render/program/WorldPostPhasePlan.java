package dev.oculusforge.render.program;

import java.util.*;

/** The opaque -> deferred -> translucent -> composite handoff, not a second frame. */
public record WorldPostPhasePlan(int deferredCount, ColorBufferPlan deferred, ColorBufferPlan composite,
                                 Set<Integer> worldBuffers) {
    public WorldPostPhasePlan { worldBuffers = Set.copyOf(worldBuffers); }

    public static WorldPostPhasePlan create(List<PreparedFinalProgram> programs, Set<Integer> initial,
                                            ColorBufferFormats formats) throws ShaderCompatibilityException {
        int count = 0;
        while (count < programs.size() && isDeferred(programs.get(count).stageName())) count++;
        for (int index = count; index < programs.size(); index++)
            if (isDeferred(programs.get(index).stageName()))
                throw new ShaderCompatibilityException("Deferred stages must precede composite/final stages");
        var world = new TreeSet<>(initial);
        var exports = new TreeSet<Integer>();
        for (var program : programs.subList(0, count)) for (int output : program.outputBuffers()) {
            if (output == ColorBufferPlan.SCREEN)
                throw new ShaderCompatibilityException("Deferred stages cannot write the final screen");
            if (output > 0) exports.add(output);
        }
        world.addAll(exports);
        // Pin every deferred auxiliary result until the phase boundary, including
        // clear-each-frame buffers. These allocator-only flags NEVER change the
        // real world attachments' clearing/history policy.
        var pinFlags = new HashMap<>(formats.clearFlags());
        for (int output : exports) pinFlags.put(output, false);
        var pinFormats = new ColorBufferFormats(formats.declared(), pinFlags, formats.clearColors());
        return new WorldPostPhasePlan(count,
            ColorBufferPlan.create(programs.subList(0, count), world, pinFormats),
            ColorBufferPlan.create(programs.subList(count, programs.size()), world, formats), world);
    }

    static boolean isDeferred(String stage) { return stage.matches("deferred(?:[1-9][0-9]?)?"); }

    public void checkMemoryBudget(int width, int height) {
        long bytes = Math.addExact(deferred.scratchBytes(width, height), composite.scratchBytes(width, height));
        if (bytes > ColorBufferPlan.MAX_SCRATCH_BYTES)
            throw new IllegalStateException("Deferred + composite scratch buffers exceed the 128 MiB safety limit; reduce resolution");
    }
}
