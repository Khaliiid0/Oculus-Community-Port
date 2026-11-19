package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.GlslPreprocessor;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Selects known environment branches without guessing unresolved shader options. */
final class CustomUniformProperties {
    private static final int MAX_SOURCE = 1024 * 1024;
    private static final Pattern DIRECTIVE = Pattern.compile("#\\s*(if|ifdef|ifndef|elif|else|endif)\\b(.*)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
    private enum Truth { YES, NO, UNKNOWN;
        Truth not() { return this == YES ? NO : this == NO ? YES : UNKNOWN; }
        Truth and(Truth other) { return this == NO || other == NO ? NO : this == YES && other == YES ? YES : UNKNOWN; }
    }
    private static final class Branch {
        final Truth parent;
        Truth remaining;
        boolean hadElse;
        Branch(Truth parent, Truth condition) { this.parent=parent; this.remaining=condition.not(); }
    }
    private CustomUniformProperties() { }

    static String select(String source, Map<String,String> environment) {
        environment=dev.oculusforge.shaderpack.ShaderOptionOverrides.forProperties(environment);
        if (source.length()>MAX_SOURCE || source.indexOf('\0')>=0 || environment.size()>64)
            throw new IllegalArgumentException("Custom property source exceeds bounds");
        var stack=new ArrayDeque<Branch>();
        var cache=new HashMap<String,Truth>();
        Truth active=Truth.YES;
        var result=new StringBuilder();
        var logical=new StringBuilder();
        int lines=0, directives=0;
        for(String physical:source.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
            if(++lines>32768) throw new IllegalArgumentException("Too many property lines");
            // Java-properties continuation: only an odd trailing backslash continues.
            int slashes=0;
            for(int i=physical.length()-1;i>=0&&physical.charAt(i)=='\\';i--) slashes++;
            boolean continued=(slashes&1)!=0;
            // Comment lines do not consume the following property via a trailing slash.
            if(logical.isEmpty() && (physical.stripLeading().startsWith("#") || physical.stripLeading().startsWith("!"))) {
                if(DIRECTIVE.matcher(physical.trim()).matches() && continued)
                    throw new IllegalArgumentException("Continued directives are unsupported");
                continued=false;
            }
            String part=logical.isEmpty()?physical:physical.stripLeading();
            logical.append(continued?part.substring(0,part.length()-1):part);
            if(logical.length()>16384) throw new IllegalArgumentException("Property line too long");
            if(continued) continue;
            String line=logical.toString().trim(); logical.setLength(0);
            var directive=DIRECTIVE.matcher(line);
            if(directive.matches()) {
                if(++directives>1024) throw new IllegalArgumentException("Too many property directives");
                String kind=directive.group(1), expression=directive.group(2).trim();
                if(Set.of("if","ifdef","ifndef").contains(kind)) {
                    if(stack.size()>=32) throw new IllegalArgumentException("Property nesting limit");
                    Truth condition=condition(kind,expression,environment,cache);
                    stack.push(new Branch(active,condition)); active=active.and(condition);
                } else {
                    if(stack.isEmpty()) throw new IllegalArgumentException("Unmatched property directive");
                    Branch branch=stack.peek();
                    if(kind.equals("endif")) {
                        if(!expression.isEmpty()) throw new IllegalArgumentException("Invalid endif");
                        active=stack.pop().parent;
                    } else {
                        if(branch.hadElse) throw new IllegalArgumentException("Branch after else");
                        if(kind.equals("else")) {
                            if(!expression.isEmpty()) throw new IllegalArgumentException("Invalid else");
                            active=branch.parent.and(branch.remaining); branch.hadElse=true;
                        } else {
                            Truth condition=condition("if",expression,environment,cache);
                            active=branch.parent.and(branch.remaining).and(condition);
                            branch.remaining=branch.remaining.and(condition.not());
                        }
                    }
                }
            } else if(line.startsWith("#")) {
                String word=line.substring(1).stripLeading().split("\\s+",2)[0];
                if(active!=Truth.NO && Set.of("include","define","undef","error","pragma","version","extension").contains(word))
                    throw new IllegalArgumentException("Unsupported custom property directive");
            } else if(!line.startsWith("!") && active!=Truth.NO) {
                // Retain unknown definitions behind a sentinel conditional. The strict parser
                // blocks them (including collisions with otherwise unconditional definitions).
                if(active==Truth.UNKNOWN) result.append("#if OCULUS_UNRESOLVED_PROPERTY\n");
                result.append(line).append('\n');
                if(active==Truth.UNKNOWN) result.append("#endif\n");
                if(result.length()>MAX_SOURCE*2) throw new IllegalArgumentException("Expanded properties too large");
            }
        }
        if(!stack.isEmpty() || !logical.isEmpty()) throw new IllegalArgumentException("Unfinished property input");
        return result.toString();
    }

    private static Truth condition(String kind,String expression,Map<String,String> environment,Map<String,Truth> cache) {
        if(expression.isEmpty() || expression.length()>256) throw new IllegalArgumentException("Invalid property condition");
        if(!kind.equals("if")) {
            if(!IDENTIFIER.matcher(expression).matches()) throw new IllegalArgumentException("Invalid macro name");
            if(!environment.containsKey(expression)) {
                if(!knownAbsent(expression,environment)) return Truth.UNKNOWN;
                return kind.equals("ifdef")?Truth.NO:Truth.YES;
            }
            return kind.equals("ifdef")?Truth.YES:Truth.NO;
        }
        if(!expression.matches("[A-Za-z0-9_\\s()!<>=&|+*/%.-]+") || expression.contains("/*") || expression.contains("//"))
            throw new IllegalArgumentException("Unsupported property condition");
        var identifiers=IDENTIFIER.matcher(expression);
        while(identifiers.find()) if(!identifiers.group().equals("defined") && !environment.containsKey(identifiers.group())
            && !knownAbsent(identifiers.group(),environment))
            return Truth.UNKNOWN;
        if(cache.containsKey(expression)) return cache.get(expression);
        if(cache.size()>=128) throw new IllegalArgumentException("Too many unique conditions");
        try {
            String output=GlslPreprocessor.preprocess("#if "+expression+"\n1\n#else\n0\n#endif\n",environment).trim();
            Truth answer=switch(output) {case "1"->Truth.YES; case "0"->Truth.NO; default->throw new IllegalArgumentException("Invalid condition result");};
            cache.put(expression,answer); return answer;
        } catch(java.io.IOException invalid) { throw new IllegalArgumentException("Invalid property condition",invalid); }
    }

    /** OS macros are mutually exclusive in the actual compiler environment, not shader options. */
    private static boolean knownAbsent(String name,Map<String,String> environment) {
        if(dev.oculusforge.shaderpack.ShaderOptionOverrides.knownAbsent(environment,name))return true;
        // This renderer has no DH geometry bridge. Match actual GLSL preprocessing,
        // where this integration macro is absent; do not define it to zero (#ifdef).
        if (name.equals("DISTANT_HORIZONS") && "1".equals(environment.get("OCULUS_POST_PROCESSING"))) return true;
        // These are fully resolved from the selected resource declaration in this
        // renderer. An absent format macro is false, not an unknown shader option.
        if("1".equals(environment.get("OCULUS_POST_PROCESSING"))
            && Set.of("MC_TEXTURE_FORMAT_LAB_PBR","MC_TEXTURE_FORMAT_LAB_PBR_1_3").contains(name))return true;
        var operatingSystems=Set.of("MC_OS_WINDOWS","MC_OS_MAC","MC_OS_LINUX");
        return operatingSystems.contains(name) && !environment.containsKey(name)
            && operatingSystems.stream().filter(environment::containsKey).count()==1;
    }
}
