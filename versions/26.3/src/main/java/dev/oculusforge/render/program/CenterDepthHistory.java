package dev.oculusforge.render.program;

import java.util.Objects;
import java.util.regex.Pattern;

/** Pure frame/epoch bookkeeping; update history only after a successful GPU draw. */
public final class CenterDepthHistory {
    public static final float DEFAULT_HALF_LIFE=1.0f;
    private Object world,frame;
    private int width,height,slot;
    public record Advance(Object world,Object frame,int width,int height,int source,int destination,
                          boolean previous,float blend) {}
    public void reset(){world=frame=null;width=height=slot=0;}
    public boolean current(Object world,Object frame){return frame!=null&&this.world==world&&this.frame==frame;}
    public Advance advance(Object world,Object frame,int width,int height,float seconds,float halfLife) {
        Objects.requireNonNull(world);Objects.requireNonNull(frame);
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid center-depth viewport");
        float blend=blend(seconds,halfLife);
        if(current(world,frame)&&this.width==width&&this.height==height)return null;
        boolean previous=this.world==world&&this.frame!=null&&this.width==width&&this.height==height;
        return new Advance(world,frame,width,height,slot,1-slot,previous,blend);
    }
    public void commit(Advance next){
        Objects.requireNonNull(next);world=next.world;frame=next.frame;width=next.width;height=next.height;slot=next.destination;
    }
    public int slot(){if(frame==null)throw new IllegalStateException("Center depth has no completed frame");return slot;}
    public static float blend(float seconds,float halfLife) {
        if(!Float.isFinite(seconds)||seconds<0||!Float.isFinite(halfLife)||halfLife<0)
            throw new IllegalArgumentException("Invalid center-depth smoothing timing");
        // Iris's centerDepthHalflife unit is one tenth of a second.
        return halfLife==0?1:(float)-Math.expm1(-Math.log(2)*seconds*10.0/halfLife);
    }
    public static float halfLife(Iterable<String> sources) throws ShaderCompatibilityException {
        Float value=null;
        var declaration=Pattern.compile("\\bconst\\s+(\\w+)\\s+centerDepthHalflife\\s*([^;{}]*);");
        var literal=Pattern.compile("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[fF]?");
        for(String source:sources) {
            var matches=declaration.matcher(PostProcessCapabilities.withoutComments(source));
            while(matches.find()) {
                if(!matches.group(1).equals("float")||!matches.group(2).stripLeading().startsWith("="))
                    throw new ShaderCompatibilityException("centerDepthHalflife requires a scalar const float");
                String text=matches.group(2).stripLeading().substring(1).trim();
                if(!literal.matcher(text).matches())throw new ShaderCompatibilityException("centerDepthHalflife needs a nonnegative finite literal");
                float next;
                try{next=Float.parseFloat(text);}catch(NumberFormatException invalid){throw new ShaderCompatibilityException("Invalid centerDepthHalflife");}
                if(!Float.isFinite(next)||next<0)throw new ShaderCompatibilityException("Invalid centerDepthHalflife");
                if(value!=null&&Float.compare(value,next)!=0)throw new ShaderCompatibilityException("Conflicting centerDepthHalflife declarations");
                value=next;
            }
        }
        return value==null?DEFAULT_HALF_LIFE:value;
    }
}
