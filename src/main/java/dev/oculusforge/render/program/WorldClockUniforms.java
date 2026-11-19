package dev.oculusforge.render.program;

/** Legacy 24,000-tick day uniforms derived from the synchronized Overworld clock, not game age. */
public record WorldClockUniforms(int time, int day) {
    public static WorldClockUniforms fromTicks(long ticks) {
        long day = Math.floorDiv(ticks, 24000L);
        return new WorldClockUniforms((int) Math.floorMod(ticks, 24000L),
            Math.clamp(day, Integer.MIN_VALUE, Integer.MAX_VALUE));
    }
}
