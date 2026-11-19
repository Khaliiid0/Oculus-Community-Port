package dev.oculusforge.render.program;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/** Bounded parser for a pure scalar subset, never an evaluator of arbitrary pack code. */
final class CustomFloatExpression {
    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile(
        "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    interface Evaluation {
        void step(); float input(String name); float smoothed(int slot);
        default float integerRemainder(String name, int divisor) {
            throw new IllegalArgumentException("Missing integer frame binding");
        }
    }
    @FunctionalInterface interface Node { float evaluate(Evaluation context); }
    @FunctionalInterface interface Smoothing { Value register(int id, Value input, float rise, float fall); }
    record Value(String glsl, Set<String> dependencies, Float constant, Node node) {
        Value(String glsl, Set<String> dependencies, Float constant) {
            this(glsl, dependencies, constant, context -> {
                if(constant==null) throw new IllegalStateException("Expression has no CPU binding");
                return constant;
            });
        }
        Value {
            if (glsl.length() > 8192) throw new IllegalArgumentException("Expanded expression too large");
            dependencies = Set.copyOf(dependencies);
            if (constant != null && !Float.isFinite(constant)) throw new IllegalArgumentException("Nonfinite constant");
        }
        float evaluate(Evaluation context) {
            context.step();
            float result=node.evaluate(context);
            if(!Float.isFinite(result)) throw new IllegalArgumentException("Nonfinite custom expression result");
            return result;
        }
    }
    private final String source;
    private final Function<String, Value> resolver;
    private final Smoothing smoothing;
    private int position, nodes, depth;

    private CustomFloatExpression(String source, Function<String, Value> resolver, Smoothing smoothing) {
        if (source.length() > 2048) throw new IllegalArgumentException("Expression too large");
        this.source = source;
        this.resolver = resolver;
        this.smoothing = smoothing;
    }

    static Value parse(String source, Function<String, Value> resolver) {
        return parse(source,resolver,null);
    }
    static Value parse(String source, Function<String, Value> resolver, Smoothing smoothing) {
        var parser = new CustomFloatExpression(source, resolver,smoothing);
        Value result = parser.sum();
        parser.space();
        if (parser.position != source.length()) throw new IllegalArgumentException("Unsupported expression token");
        return result;
    }

    private void space() { while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++; }
    private boolean take(char c) {
        space();
        if (position < source.length() && source.charAt(position) == c) { position++; return true; }
        return false;
    }
    private void require(char c) { if (!take(c)) throw new IllegalArgumentException("Expected " + c); }
    private Value sum() {
        Value value = product();
        while (true) {
            if (take('+')) value = binary(value, product(), '+');
            else if (take('-')) value = binary(value, product(), '-');
            else return value;
        }
    }
    private Value product() {
        Value value = atom();
        while (true) {
            if (take('*')) value = binary(value, atom(), '*');
            else if (take('/')) value = binary(value, atom(), '/');
            else return value;
        }
    }
    private Value atom() {
        if (++nodes > 128 || ++depth > 24) throw new IllegalArgumentException("Expression complexity limit");
        try {
            if (take('+')) return atom();
            if (take('-')) {
                Value value = atom();
                return new Value("(-" + value.glsl + ")", value.dependencies, value.constant == null ? null : -value.constant,
                    context -> -value.evaluate(context));
            }
            if (take('(')) { Value value = sum(); require(')'); return value; }
            space();
            if (position == source.length()) throw new IllegalArgumentException("Missing operand");
            int start = position;
            char first = source.charAt(position);
            if (Character.isDigit(first) || first == '.') {
                var number = NUMBER.matcher(source).region(position, source.length());
                if (!number.lookingAt()) throw new IllegalArgumentException("Invalid number");
                position = number.end();
                float value = Float.parseFloat(number.group());
                return new Value(Float.toString(value), Set.of(), value);
            }
            if (!(first >= 'A' && first <= 'Z' || first >= 'a' && first <= 'z' || first == '_'))
                throw new IllegalArgumentException("Unsupported operand");
            while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')) position++;
            String name = source.substring(start, position);
            if (take('.')) {
                // Only named-vector single components; no arbitrary member access or swizzles.
                if (!name.equals("cameraPosition") && !name.equals("eyeBrightness"))
                    throw new IllegalArgumentException("Unsupported vector dependency");
                space();
                String components=name.equals("eyeBrightness")?"xy":"xyz";
                if (position == source.length() || components.indexOf(source.charAt(position)) < 0)
                    throw new IllegalArgumentException("Unsupported vector component");
                name += "." + source.charAt(position++);
                return java.util.Objects.requireNonNull(resolver.apply(name), "Unsupported dependency " + name);
            }
            if (!take('(')) return java.util.Objects.requireNonNull(resolver.apply(name), "Unsupported dependency " + name);
            if (name.equals("if")) return selection();
            if(name.equals("smooth")) {
                if(smoothing==null) throw new IllegalArgumentException("Smoothing needs CPU state");
                Value id=sum(); require(','); Value input=sum(); require(','); Value rise=sum(); require(','); Value fall=sum(); require(')');
                if(id.constant==null || id.constant<0 || id.constant>65535 || id.constant!=Math.rint(id.constant)
                    || rise.constant==null || rise.constant<0 || fall.constant==null || fall.constant<0)
                    throw new IllegalArgumentException("Smoothing requires constant ID and nonnegative half-lives");
                return smoothing.register(id.constant.intValue(),input,rise.constant,fall.constant);
            }
            int count = switch (name) { case "sin", "abs" -> 1; case "min", "max" -> 2; case "clamp" -> 3; default -> throw new IllegalArgumentException("Unsupported function"); };
            var args = new ArrayList<Value>();
            for (int i = 0; i < count; i++) { if (i > 0) require(','); args.add(sum()); }
            require(')');
            var deps = new LinkedHashSet<String>();
            args.forEach(arg -> deps.addAll(arg.dependencies));
            Float constant = null;
            if (args.stream().allMatch(arg -> arg.constant != null)) {
                float x = args.getFirst().constant;
                constant = switch (name) {
                    case "sin" -> (float) Math.sin(x);
                    case "abs" -> Math.abs(x);
                    case "min" -> Math.min(x, args.get(1).constant);
                    case "max" -> Math.max(x, args.get(1).constant);
                    default -> Math.max(args.get(1).constant, Math.min(x, args.get(2).constant));
                };
            }
            if (name.equals("clamp") && (args.get(1).constant == null || args.get(2).constant == null
                || args.get(1).constant > args.get(2).constant)) throw new IllegalArgumentException("Unsupported clamp bounds");
            String functionName=name;
            return new Value(name + "(" + String.join(", ", args.stream().map(Value::glsl).toList()) + ")", deps, constant,
                context -> {
                    float x=args.getFirst().evaluate(context);
                    return switch(functionName) {
                        case "sin" -> (float)Math.sin(x);
                        case "abs" -> Math.abs(x);
                        case "min" -> Math.min(x,args.get(1).evaluate(context));
                        case "max" -> Math.max(x,args.get(1).evaluate(context));
                        default -> Math.max(args.get(1).evaluate(context),Math.min(x,args.get(2).evaluate(context)));
                    };
                });
        } finally { depth--; }
    }
    /** Three-argument selection with one ordered scalar comparison, not boolean coercion. */
    private Value selection() {
        Value condition = condition();
        require(',');
        Value yes = sum();
        require(',');
        Value no = sum();
        require(')');
        if (++nodes > 128) throw new IllegalArgumentException("Expression complexity limit");
        var dependencies = new LinkedHashSet<>(condition.dependencies);
        dependencies.addAll(yes.dependencies);
        dependencies.addAll(no.dependencies);
        Float constant = condition.constant == null ? null : condition.constant != 0 ? yes.constant : no.constant;
        return new Value("(" + condition.glsl + " ? " + yes.glsl + " : " + no.glsl + ")", dependencies, constant,
            context -> condition.evaluate(context)!=0 ? yes.evaluate(context) : no.evaluate(context));
    }

    private Value condition() {
        space();
        var membership = java.util.regex.Pattern.compile("in\\s*\\(").matcher(source).region(position, source.length());
        if(membership.lookingAt()) {
            position=membership.end();
            space();
            int inputStart=position;
            while(position<source.length()&&(Character.isLetterOrDigit(source.charAt(position))||source.charAt(position)=='_'))position++;
            String input=source.substring(inputStart,position);
            if(!input.equals("biome")&&!input.equals("biome_precipitation"))
                throw new IllegalArgumentException("Unsupported membership input");
            require(',');
            var clauses=new ArrayList<String>();
            var values=new ArrayList<Value>();
            var dependencies=new LinkedHashSet<String>();
            do {
                space();
                int start=position;
                while(position<source.length() && (input.equals("biome_precipitation")?Character.isDigit(source.charAt(position)):
                    source.charAt(position)>='A'&&source.charAt(position)<='Z'||source.charAt(position)=='_')) position++;
                String symbol=source.substring(start,position);
                if(!(input.equals("biome_precipitation")?symbol.matches("[012]"):symbol.startsWith("BIOME_")) || ++nodes>128 || clauses.size()>=64)
                    throw new IllegalArgumentException("Unsupported biome membership");
                Value value=java.util.Objects.requireNonNull(resolver.apply((input.equals("biome_precipitation")?"@precipitation:":"@biome:")+symbol),"Unknown biome symbol");
                clauses.add(value.glsl); dependencies.addAll(value.dependencies);
                values.add(value);
            } while(take(','));
            require(')');
            return new Value("("+String.join(" || ",clauses)+")",dependencies,null,context -> {
                for(Value value:values) if(value.evaluate(context)!=0) return 1;
                return 0;
            });
        }
        Value left = sum();
        space();
        if (position == source.length() || (source.charAt(position) != '<' && source.charAt(position) != '>'))
            throw new IllegalArgumentException("Selection requires an ordered scalar comparison");
        char direction = source.charAt(position++);
        // The two-character operator must be contiguous, like a GLSL token.
        boolean inclusive = position < source.length() && source.charAt(position) == '=';
        if (inclusive) position++;
        Value right = sum();
        if (++nodes > 128) throw new IllegalArgumentException("Expression complexity limit");
        var dependencies = new LinkedHashSet<>(left.dependencies);
        dependencies.addAll(right.dependencies);
        Float constant = null;
        if (left.constant != null && right.constant != null) {
            boolean matches = direction == '<'
                ? (inclusive ? left.constant <= right.constant : left.constant < right.constant)
                : (inclusive ? left.constant >= right.constant : left.constant > right.constant);
            constant = matches ? 1f : 0f;
        }
        // Validate both branches even for constant conditions; unsupported code cannot hide
        // in an inactive branch. Value enforces the same bounded expansion as arithmetic.
        return new Value("(" + left.glsl + " " + direction + (inclusive ? "=" : "") + " "
            + right.glsl + ")", dependencies, constant,context -> {
                float a=left.evaluate(context),b=right.evaluate(context);
                return (direction=='<' ? (inclusive?a<=b:a<b) : (inclusive?a>=b:a>b))?1:0;
            });
    }

    private Value binary(Value left, Value right, char operator) {
        if (++nodes > 128) throw new IllegalArgumentException("Expression complexity limit");
        if (operator == '/' && (right.constant == null || right.constant == 0))
            throw new IllegalArgumentException("Division requires a nonzero constant denominator");
        var deps = new LinkedHashSet<>(left.dependencies);
        deps.addAll(right.dependencies);
        Float constant = null;
        if (left.constant != null && right.constant != null) constant = switch (operator) {
            case '+' -> left.constant + right.constant;
            case '-' -> left.constant - right.constant;
            case '*' -> left.constant * right.constant;
            default -> left.constant / right.constant;
        };
        return new Value("(" + left.glsl + " " + operator + " " + right.glsl + ")", deps, constant,context -> {
            float a=left.evaluate(context),b=right.evaluate(context);
            return switch(operator) {case '+'->a+b; case '-'->a-b; case '*'->a*b; default->a/b;};
        });
    }
}
