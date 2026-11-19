package dev.oculusforge.render.program;

import dev.oculusforge.render.world.NativeBiomeSymbols;
import java.util.*;

/** CPU expression plan and transactional history consumed by shared variant bindings. */
final class CustomExpressionRuntime {
    private static final int MAX_CHANNELS=64, MAX_OUTPUTS=64, MAX_STEPS=8192;
    record Sample(int worldTime,int frameCounter,float cameraX,float cameraY,float cameraZ,int biomeCode,
                  float blindness,float darknessFactor,float rainStrength,dev.oculusforge.render.world.EyeBrightness eyeBrightness,int precipitation) {
        Sample(int worldTime,int frameCounter,float cameraX,float cameraY,float cameraZ,int biomeCode,float blindness,float darknessFactor,
               float rainStrength,dev.oculusforge.render.world.EyeBrightness eyeBrightness) {
            this(worldTime,frameCounter,cameraX,cameraY,cameraZ,biomeCode,blindness,darknessFactor,rainStrength,eyeBrightness,0);
        }
        Sample(int worldTime,int frameCounter,float cameraX,float cameraY,float cameraZ,int biomeCode,float blindness,float darknessFactor) {
            this(worldTime,frameCounter,cameraX,cameraY,cameraZ,biomeCode,blindness,darknessFactor,0,
                dev.oculusforge.render.world.EyeBrightness.DARK);
        }
        Sample(int worldTime,int frameCounter,float cameraX,float cameraY,float cameraZ,int biomeCode) {
            this(worldTime,frameCounter,cameraX,cameraY,cameraZ,biomeCode,0,0);
        }
        Sample {
            Objects.requireNonNull(eyeBrightness,"eyeBrightness");
            if(worldTime<0||worldTime>=24000||frameCounter<0||!Float.isFinite(cameraX)
                ||!Float.isFinite(cameraY)||!Float.isFinite(cameraZ)||!NativeBiomeSymbols.valid(biomeCode)
                ||!Float.isFinite(blindness)||blindness<0||blindness>1
                ||!Float.isFinite(darknessFactor)||darknessFactor<0||darknessFactor>1
                ||!Float.isFinite(rainStrength)||rainStrength<0||rainStrength>1||precipitation<0||precipitation>2)
                throw new IllegalArgumentException("Invalid custom-uniform frame sample");
        }
    }
    private record Channel(int id,CustomFloatExpression.Value input,float rise,float fall) { }
    record Program(Map<String,CustomFloatExpression.Value> outputs,List<Channel> channels) {
        Program { outputs=Collections.unmodifiableMap(new LinkedHashMap<>(outputs)); channels=List.copyOf(channels); }
        CustomExpressionRuntime newRuntime() { return new CustomExpressionRuntime(this); }
    }
    record Snapshot(Map<String,Float> values) {
        Snapshot { values=Map.copyOf(values); }
        float value(String name) { return Objects.requireNonNull(values.get(name),"Unknown custom output"); }
    }

    static Program compile(Map<String,String> definitions,Set<String> requestedOutputs) {
        return compile(definitions,requestedOutputs,Set.of());
    }
    static Program compile(Map<String,String> definitions,Set<String> requestedOutputs,Set<String> blockedNames) {
        Set<String> blocked=Set.copyOf(blockedNames);
        if(definitions.size()>256||requestedOutputs.size()>MAX_OUTPUTS)
            throw new IllegalArgumentException("Custom definition/output limit");
        Map<String,String> source=Map.copyOf(definitions);
        source.forEach((name,text)->{
            if(!name.matches("[A-Za-z_][A-Za-z_0-9]{0,127}")||text.length()>2048
                ||NativeWorldBindings.supports(name)||name.equals("blindness")||name.equals("darknessFactor")
                ||name.equals("biome")||name.equals("biome_precipitation")||name.startsWith("BIOME_")
                ||name.startsWith("gl_")||name.startsWith("oculus")||name.startsWith("Oculus"))
                throw new IllegalArgumentException("Invalid custom definition");
        });
        var channels=new ArrayList<Channel>();
        CustomFloatExpression.Smoothing smoothing=(id,input,rise,fall)->{
            // Iris keeps history per parsed call, not per compatibility ID. Named
            // references still share their resolved node through the resolver cache.
            if(channels.size()>=MAX_CHANNELS) throw new IllegalArgumentException("Custom smoothing channel limit");
            int index=channels.size();
            channels.add(new Channel(id,input,rise,fall));
            return new CustomFloatExpression.Value("oculusCpuSmooth"+index,input.dependencies(),null,c->c.smoothed(index));
        };
        var resolved=new HashMap<String,CustomFloatExpression.Value>();
        var resolving=new HashSet<String>();
        class Resolver implements java.util.function.Function<String,CustomFloatExpression.Value> {
            public CustomFloatExpression.Value apply(String name) {
                if(blocked.contains(name)||(name.startsWith("cameraPosition.")&&blocked.contains("cameraPosition"))
                    ||name.startsWith("eyeBrightness.")&&blocked.contains("eyeBrightness"))
                    throw new IllegalArgumentException("Blocked custom dependency");
                if(name.equals("maxBlindnessDarkness")) {
                    if(blocked.contains("blindness")||blocked.contains("darknessFactor"))
                        throw new IllegalArgumentException("Blocked effect dependency");
                    return new CustomFloatExpression.Value("max(blindness, darknessFactor)",Set.of("blindness","darknessFactor"),null,
                        c->Math.max(c.input("blindness"),c.input("darknessFactor")));
                }
                if(Set.of("worldTime","frameCounter","eyeAltitude","cameraPosition.x","cameraPosition.y","cameraPosition.z",
                    "blindness","darknessFactor","rainStrength","frameTime","eyeBrightness.x","eyeBrightness.y").contains(name))
                    return new CustomFloatExpression.Value(name,Set.of(name),null,c->c.input(name));
                if(name.startsWith("@biome:")) {
                    if(blocked.contains("biome")||blocked.contains("oculusBiomeCode")||blocked.contains(name.substring(7)))
                        throw new IllegalArgumentException("Blocked biome dependency");
                    int code=NativeBiomeSymbols.fromSymbol(name.substring(7));
                    if(code==0) throw new IllegalArgumentException("Unknown biome symbol");
                    return new CustomFloatExpression.Value("(biomeCode == "+code+")",Set.of("biomeCode"),null,
                        c->c.input("biomeCode")==code?1:0);
                }
                if(name.startsWith("@precipitation:")) {
                    if(blocked.contains("biome_precipitation")) throw new IllegalArgumentException("Blocked precipitation dependency");
                    String code=name.substring(15);
                    if(!code.matches("[012]")) throw new IllegalArgumentException("Unknown precipitation symbol");
                    int expected=Integer.parseInt(code);
                    return new CustomFloatExpression.Value("(biome_precipitation == "+code+")",Set.of("biome_precipitation"),null,
                        c->c.input("biome_precipitation")==expected?1:0);
                }
                if(resolved.containsKey(name)) return resolved.get(name);
                if(!source.containsKey(name)||resolving.size()>=24||!resolving.add(name))
                    throw new IllegalArgumentException("Missing or cyclic custom dependency "+name);
                try {
                    String expression=source.get(name);
                    var remainder=java.util.regex.Pattern.compile("(frameCounter|worldTime)\\s*%\\s*([0-9]{1,7})").matcher(expression.trim());
                    CustomFloatExpression.Value value;
                    if(remainder.matches()) {
                        String input=remainder.group(1);
                        apply(input);
                        int divisor=Integer.parseInt(remainder.group(2));
                        if(divisor<1||divisor>1_000_000) throw new IllegalArgumentException("Invalid clock remainder divisor");
                        value=new CustomFloatExpression.Value("float("+input+" % "+divisor+")",Set.of(input),null,
                            c->c.integerRemainder(input,divisor));
                    } else value=CustomFloatExpression.parse(expression,this,smoothing);
                    resolved.put(name,value); return value;
                } catch(IllegalArgumentException|NullPointerException invalid) {
                    throw new IllegalArgumentException("Custom definition "+name+": "+invalid.getMessage(),invalid);
                } finally { resolving.remove(name); }
            }
        }
        var resolver=new Resolver();
        var outputs=new LinkedHashMap<String,CustomFloatExpression.Value>();
        for(String name:requestedOutputs.stream().sorted().toList()) {
            if(!source.containsKey(name)) throw new IllegalArgumentException("Missing custom output");
            outputs.put(name,resolver.apply(name));
        }
        return new Program(outputs,channels);
    }

    private final Program program;
    private Object world,frame;
    private double[] history;
    private Snapshot current;
    private CustomExpressionRuntime(Program program) { this.program=program; }
    Set<String> programOutputs() { return program.outputs.keySet(); }

    Snapshot update(Object worldIdentity,Object frameIdentity,Sample sample,double deltaSeconds) {
        Objects.requireNonNull(worldIdentity); Objects.requireNonNull(frameIdentity); Objects.requireNonNull(sample);
        if(!Double.isFinite(deltaSeconds)||deltaSeconds<0) throw new IllegalArgumentException("Invalid frame duration");
        if(world==worldIdentity&&frame==frameIdentity) return current;
        // All work is staged. Overflow, budget exhaustion or a bad output cannot partially
        // advance earlier channels or install a new frame/world identity.
        double[] next=new double[program.channels.size()];
        class Evaluation implements CustomFloatExpression.Evaluation {
            int steps,available;
            public void step() { if(++steps>MAX_STEPS) throw new IllegalArgumentException("Custom frame evaluation budget"); }
            public float integerRemainder(String name,int divisor) {
                return switch(name) {
                    case "worldTime"->sample.worldTime%divisor;
                    case "frameCounter"->sample.frameCounter%divisor;
                    default->throw new IllegalArgumentException("Unknown integer frame input");
                };
            }
            public float smoothed(int slot) {
                if(slot<0||slot>=available) throw new IllegalArgumentException("Invalid smoothing dependency order");
                return (float)next[slot];
            }
            public float input(String name) {
                return switch(name) {
                    case "worldTime"->sample.worldTime; case "frameCounter"->sample.frameCounter;
                    case "cameraPosition.x"->sample.cameraX;
                    case "cameraPosition.y","eyeAltitude"->sample.cameraY;
                    case "cameraPosition.z"->sample.cameraZ; case "biomeCode"->sample.biomeCode;
                    case "blindness"->sample.blindness;case "darknessFactor"->sample.darknessFactor;
                    case "rainStrength"->sample.rainStrength;case "frameTime"->(float)deltaSeconds;
                    case "eyeBrightness.x"->sample.eyeBrightness.blockLightmap();
                    case "eyeBrightness.y"->sample.eyeBrightness.skyLightmap();
                    case "biome_precipitation"->sample.precipitation;
                    default->throw new IllegalArgumentException("Unknown frame input");
                };
            }
        }
        var evaluation=new Evaluation();
        for(int i=0;i<next.length;i++) {
            Channel channel=program.channels.get(i);
            double target=channel.input.evaluate(evaluation),value=target;
            if(world==worldIdentity&&history!=null) {
                // Iris fade units are 0.1 seconds; ScalarSmoother uses 20 Hz ticks.
                // Promote before conversion so finite float fades cannot overflow.
                double ticks=2.0*(target>history[i]?channel.rise:channel.fall);
                value=ScalarSmoother.blend(history[i],target,deltaSeconds,ticks);
            }
            if(!Float.isFinite((float)value)) throw new IllegalArgumentException("Nonfinite custom history");
            next[i]=value; evaluation.available=i+1;
        }
        var values=new LinkedHashMap<String,Float>();
        for(var entry:program.outputs.entrySet()) values.put(entry.getKey(),entry.getValue().evaluate(evaluation));
        Snapshot result=new Snapshot(values);
        history=next; current=result; world=worldIdentity; frame=frameIdentity;
        return result;
    }

    void reset() { world=null; frame=null; history=null; current=null; }
}
