package dev.oculusforge.render.program;

import java.util.Objects;

/** Render-thread state for one scalar channel; time constants are in 20 Hz ticks. */
final class ScalarSmoother {
    private final double riseTicks, fallTicks;
    private Object world, frame;
    private double current;

    ScalarSmoother(double riseTicks, double fallTicks) {
        if(!Double.isFinite(riseTicks)||riseTicks<0||!Double.isFinite(fallTicks)||fallTicks<0)
            throw new IllegalArgumentException("Invalid scalar smoothing half-life");
        this.riseTicks=riseTicks; this.fallTicks=fallTicks;
    }

    double update(Object worldIdentity,Object frameIdentity,double target,double deltaSeconds) {
        Objects.requireNonNull(worldIdentity,"worldIdentity");
        Objects.requireNonNull(frameIdentity,"frameIdentity");
        if(!Double.isFinite(target)||!Double.isFinite(deltaSeconds)||deltaSeconds<0)
            throw new IllegalArgumentException("Invalid scalar smoothing sample");
        if(world==worldIdentity&&frame==frameIdentity) return current;
        double next=target;
        if(world==worldIdentity&&frame!=null) {
            double ticks=target>current?riseTicks:fallTicks;
            next=blend(current,target,deltaSeconds,ticks);
        }
        if(!Double.isFinite(next)) throw new IllegalArgumentException("Nonfinite scalar smoothing result");
        current=next; world=worldIdentity; frame=frameIdentity;
        return current;
    }

    void reset() { world=null; frame=null; current=0; }

    static double blend(double current,double target,double deltaSeconds,double ticks) {
        if(ticks==0) return target;
        double blend=-Math.expm1(-Math.log(2)*deltaSeconds*20/ticks);
        // Weighted form avoids overflowing target-current for finite extremes.
        return current*(1-blend)+target*blend;
    }
}
