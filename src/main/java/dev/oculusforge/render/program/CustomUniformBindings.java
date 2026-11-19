package dev.oculusforge.render.program;

import java.util.*;
import java.util.regex.Pattern;

/** One bounded output layout shared by native and fullscreen programs in a dimension. */
public final class CustomUniformBindings {
    public static final int MAX_VALUES=64;
    private static final String PREFIX="oculusCustomValue";
    private static final Pattern UNIFORM=Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*;");
    private static final Pattern RESERVED=Pattern.compile("\\boculusCustomValue\\w*\\b");
    public static final CustomUniformBindings EMPTY=new CustomUniformBindings(
        CustomExpressionRuntime.compile(Map.of(),Set.of()));
    private final CustomExpressionRuntime.Program program;
    private final Map<String,Integer> slots;
    private final Set<String> declared;

    private CustomUniformBindings(CustomExpressionRuntime.Program program) {
        this(program,Set.of());
    }
    private CustomUniformBindings(CustomExpressionRuntime.Program program,Set<String> declared) {
        this.program=program;
        this.declared=Set.copyOf(declared);
        var layout=new LinkedHashMap<String,Integer>();
        program.outputs().keySet().forEach(name->layout.put(name,layout.size()));
        slots=Collections.unmodifiableMap(layout);
    }
    static CustomUniformBindings prepare(NativeCustomUniforms custom,Collection<String> sources)
        throws ShaderCompatibilityException {
        var names=new TreeSet<String>();
        var declared=new HashSet<String>();
        for(String source:sources) {
            String clean=normalized(source);
            if(RESERVED.matcher(clean).find()) throw new ShaderCompatibilityException("Reserved custom-uniform identifier");
            var found=new HashSet<String>();
            var declarations=UNIFORM.matcher(clean);
            String code=declarations.replaceAll("");
            declarations.reset();
            while(declarations.find()) {
                String name=declarations.group(2);
                if(!custom.declaresUniform(name)) continue;
                if(!declarations.group(1).equals("float")||!found.add(name))
                    throw new ShaderCompatibilityException("Invalid or duplicate custom uniform "+name);
                declared.add(name);
                if(Pattern.compile("\\b"+Pattern.quote(name)+"\\b").matcher(code).find()) names.add(name);
            }
        }
        if(names.isEmpty()&&declared.isEmpty()) return EMPTY;
        try { return new CustomUniformBindings(custom.prepareCpu(names),declared); }
        catch(IllegalArgumentException|NullPointerException invalid) {
            throw new ShaderCompatibilityException("Unsupported shared custom uniforms "+names+": "+invalid.getMessage());
        }
    }
    private static String normalized(String source) throws ShaderCompatibilityException {
        return NativeShaderDeclarations.expandLists(PostProcessCapabilities.withoutComments(source));
    }
    /** Preserve pack comments and render-target metadata. */
    String adapt(String source) throws ShaderCompatibilityException {
        String clean=normalized(source);
        if(RESERVED.matcher(clean).find()) throw new ShaderCompatibilityException("Reserved custom-uniform identifier");
        var replacements=new HashMap<String,String>();
        var declarations=UNIFORM.matcher(clean);
        var found=new HashSet<String>();
        while(declarations.find()) {
            String name=declarations.group(2);
            Integer slot=slots.get(name);
            if(slot==null) continue;
            if(!declarations.group(1).equals("float")||!found.add(name))
                throw new ShaderCompatibilityException("Invalid or duplicate custom uniform "+name);
            replacements.put(name,name(slot));
        }
        var comments=Pattern.compile("/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*").matcher(source);
        var preserved=new StringBuilder();int offset=0;
        while(comments.find()) {
            preserved.append(stripUnused(source.substring(offset,comments.start()))).append(comments.group());
            offset=comments.end();
        }
        preserved.append(stripUnused(source.substring(offset)));
        var tokens=Pattern.compile("/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*|[A-Za-z_][A-Za-z_0-9]*").matcher(preserved);
        var result=new StringBuilder();
        while(tokens.find()) tokens.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(
            replacements.getOrDefault(tokens.group(),tokens.group())));
        tokens.appendTail(result);
        return result.toString();
    }
    private String stripUnused(String source) {
        var matcher=Pattern.compile("\\buniform\\s+(\\w+)\\s+([A-Za-z_][A-Za-z_0-9]*(?:\\s*,\\s*[A-Za-z_][A-Za-z_0-9]*)*)\\s*;").matcher(source);
        var result=new StringBuilder();
        while(matcher.find()) {
            String type=matcher.group(1);
            var names=Arrays.stream(matcher.group(2).split("\\s*,\\s*"))
                .filter(name->!declared.contains(name)||slots.containsKey(name)).toList();
            String replacement=names.isEmpty()?"":"uniform "+type+" "+String.join(", ",names)+";";
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);return result.toString();
    }
    public static String name(int slot) {
        if(slot<0||slot>=MAX_VALUES) throw new IllegalArgumentException("Invalid custom slot");
        return PREFIX+slot;
    }
    static int index(String name) {
        if(!name.startsWith(PREFIX)) return -1;
        String suffix=name.substring(PREFIX.length());
        if(!suffix.matches("0|[1-9][0-9]?")) return -1;
        int slot=Integer.parseInt(suffix);
        return slot<MAX_VALUES?slot:-1;
    }
    public Map<String,Integer> slots() { return slots; }
    public State newState() { return new State(program.newRuntime(),slots.size()); }

    /** Render-thread-confined history, deduplicated by the shared animation snapshot identity. */
    public static final class State {
        private final CustomExpressionRuntime runtime;
        private final int size;
        private CustomExpressionRuntime.Snapshot previous;
        private Values values;
        private State(CustomExpressionRuntime runtime,int size) { this.runtime=runtime;this.size=size; }
        public Values update(Object world,AnimationClock.Snapshot animation,long ticks,
                             double x,double y,double z,int biomeCode) {
            return update(world,animation,ticks,x,y,z,biomeCode,0,0);
        }
        public Values update(Object world,AnimationClock.Snapshot animation,long ticks,
                             double x,double y,double z,int biomeCode,float blindness,float darknessFactor) {
            return update(world,animation,ticks,x,y,z,biomeCode,blindness,darknessFactor,0,
                dev.oculusforge.render.world.EyeBrightness.DARK);
        }
        public Values update(Object world,AnimationClock.Snapshot animation,long ticks,
                             double x,double y,double z,int biomeCode,float blindness,float darknessFactor,
                             float rainStrength,dev.oculusforge.render.world.EyeBrightness eyeBrightness) {
            return update(world,animation,ticks,x,y,z,biomeCode,blindness,darknessFactor,rainStrength,eyeBrightness,0);
        }
        public Values update(Object world,AnimationClock.Snapshot animation,long ticks,
                             double x,double y,double z,int biomeCode,float blindness,float darknessFactor,
                             float rainStrength,dev.oculusforge.render.world.EyeBrightness eyeBrightness,int precipitation) {
            var sample=new CustomExpressionRuntime.Sample(WorldClockUniforms.fromTicks(ticks).time(),
                animation.frameCounter(),(float)x,(float)y,(float)z,biomeCode,blindness,darknessFactor,rainStrength,eyeBrightness,precipitation);
            var snapshot=runtime.update(world,animation,sample,animation.frameTime());
            if(snapshot==previous) return values;
            float[] next=new float[MAX_VALUES];
            int slot=0;
            for(String name:runtime.programOutputs()) next[slot++]=snapshot.value(name);
            values=new Values(next,size);previous=snapshot;
            return values;
        }
        public void reset() { runtime.reset();previous=null;values=null; }
    }
    public static final class Values {
        public static final Values EMPTY=new Values(new float[MAX_VALUES],0);
        private final float[] values;
        private final int size;
        private Values(float[] values,int size) { this.values=values;this.size=size; }
        public float slot(int slot) {
            if(slot<0||slot>=MAX_VALUES) throw new IllegalArgumentException("Invalid custom slot");
            return values[slot];
        }
        float boundSlot(int slot) {
            if(slot>=size) throw new IllegalStateException("Unbound custom uniform slot");
            return slot(slot);
        }
    }
}
