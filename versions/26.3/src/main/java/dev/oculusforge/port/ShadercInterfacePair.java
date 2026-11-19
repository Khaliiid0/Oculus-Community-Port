package dev.oculusforge.port;

import java.util.*;
import java.util.regex.*;

/** Explicit locations for already-adapted, preprocessed scalar/vector stage interfaces. */
public record ShadercInterfacePair(String vertex, String fragment) {
    private static final Pattern PREFIX = Pattern.compile("(?s)\\s*(?:layout\\s*\\([^)]*\\)\\s*)?(?:(?:flat|smooth|noperspective|centroid|sample|lowp|mediump|highp)\\s+)*(in|out)\\b.*");
    private static final Pattern DECL = Pattern.compile("(?s)\\s*(?:layout\\s*\\(([^)]*)\\)\\s*)?((?:(?:flat|smooth|noperspective|centroid|sample|lowp|mediump|highp)\\s+)*)(in|out)\\s+(float|vec[234]|int|ivec[234]|uint|uvec[234])\\s+([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)\\s*;\\s*");
    private static final Pattern LOCATION = Pattern.compile("\\s*location\\s*=\\s*([0-9]+)\\s*");
    private static final Pattern VERSION = Pattern.compile("(?m)^[\\t ]*#\\s*version[^\\r\\n]*");
    private static final Pattern SUPPORTED_VERSION = Pattern.compile("[\\t ]*#\\s*version\\s+(?:330|450)(?:\\s+core)?\\s*");
    private record Decl(int start,int end,String qualifier,String direction,String type,String name,Integer explicit) {
        String interpolation() {
            var q=Set.of(qualifier.strip().isEmpty()?new String[0]:qualifier.strip().split("\\s+"));
            return (q.contains("flat")?"flat":q.contains("noperspective")?"noperspective":"smooth")
                +":"+q.contains("centroid")+":"+q.contains("sample");
        }
    }

    public static ShadercInterfacePair adapt(String vertex,String fragment) {
        Objects.requireNonNull(vertex);Objects.requireNonNull(fragment);
        if ((long)vertex.length()+fragment.length()>32L*1024*1024)
            throw new IllegalArgumentException("ShaderC pair source budget exceeded");
        var v=parse(vertex);var f=parse(fragment);
        var inputs=select(v,"in");var outputs=select(v,"out");
        var fragmentInputs=select(f,"in");var fragmentOutputs=select(f,"out");
        Map<String,Integer> inputLocations=locations(inputs,Map.of());
        Map<String,Integer> varyingLocations=locations(outputs,Map.of());
        for(var entry:fragmentInputs.entrySet()) {
            var from=outputs.get(entry.getKey());var to=entry.getValue();
            if(from==null||!from.type.equals(to.type)||!from.interpolation().equals(to.interpolation()))
                throw new IllegalArgumentException("ShaderC stage interface mismatch: "+entry.getKey());
        }
        Map<String,Integer> fragmentLocations=locations(fragmentInputs,varyingLocations);
        Map<String,Integer> finalLocations=locations(fragmentOutputs,Map.of());
        for(var decl:outputs.values()) validateIntegerInterpolation(decl);
        for(var decl:fragmentInputs.values()) validateIntegerInterpolation(decl);
        return new ShadercInterfacePair(rewrite(vertex,v,inputLocations,varyingLocations),
            rewrite(fragment,f,fragmentLocations,finalLocations));
    }

    private static void validateIntegerInterpolation(Decl decl) {
        if(!decl.type.matches("float|vec[234]")&&!decl.interpolation().startsWith("flat:"))
            throw new IllegalArgumentException("ShaderC integer varying requires flat interpolation: "+decl.name);
    }

    private static SortedMap<String,Decl> select(List<Decl> declarations,String direction) {
        var result=new TreeMap<String,Decl>();
        for(var decl:declarations) if(decl.direction.equals(direction)&&result.putIfAbsent(decl.name,decl)!=null)
            throw new IllegalArgumentException("Duplicate ShaderC interface: "+decl.name);
        return result;
    }

    private static Map<String,Integer> locations(SortedMap<String,Decl> declarations,Map<String,Integer> matched) {
        var result=new HashMap<String,Integer>();var occupied=new HashSet<Integer>();
        for(var decl:declarations.values()) {
            Integer forced=matched.get(decl.name);
            if(forced!=null&&decl.explicit!=null&&!forced.equals(decl.explicit))
                throw new IllegalArgumentException("Conflicting ShaderC varying location: "+decl.name);
            Integer location=forced!=null?forced:decl.explicit;
            if(location!=null) {
                if(location<0||location>=64||!occupied.add(location))
                    throw new IllegalArgumentException("Conflicting/out-of-range ShaderC interface location");
                result.put(decl.name,location);
            }
        }
        for(var decl:declarations.values()) if(!result.containsKey(decl.name)) {
            int location=0;while(occupied.contains(location))location++;
            if(location>=64)throw new IllegalArgumentException("ShaderC stage exceeds 64 interface slots");
            occupied.add(location);result.put(decl.name,location);
        }
        return result;
    }

    private static List<Decl> parse(String source) {
        String code=mask(source,true);var result=new ArrayList<Decl>();
        int braces=0,parentheses=0,boundary=0;
        for(int i=0;i<code.length();i++) {
            char c=code.charAt(i);
            if(c=='(')parentheses++;
            else if(c==')') { if(--parentheses<0)throw new IllegalArgumentException("Unbalanced ShaderC source"); }
            else if(c=='{') {
                if(braces==0&&PREFIX.matcher(code.substring(boundary,i)).matches())
                    throw new IllegalArgumentException("ShaderC interface blocks are not supported by this adapter");
                braces++;
            } else if(c=='}') {
                if(--braces<0)throw new IllegalArgumentException("Unbalanced ShaderC source");
                if(braces==0)boundary=i+1;
            } else if(c==';'&&braces==0&&parentheses==0) {
                var text=code.substring(boundary,i+1);
                if(PREFIX.matcher(text).matches()) {
                    var m=DECL.matcher(text);
                    if(!m.matches()) {
                        String detail=text.strip().replaceAll("\\s+"," ");
                        throw new IllegalArgumentException("Unsupported ShaderC interface declaration (arrays or matrices): "
                            +(detail.length()>160?detail.substring(0,160)+"...":detail));
                    }
                    Integer location=null;
                    if(m.group(1)!=null) {
                        var layout=LOCATION.matcher(m.group(1));
                        if(!layout.matches())throw new IllegalArgumentException("Unsupported ShaderC interface layout");
                        try {location=Integer.valueOf(layout.group(1));}
                        catch(NumberFormatException invalid) {throw new IllegalArgumentException("ShaderC location overflow",invalid);}
                    }
                    int start=boundary;while(start<i&&Character.isWhitespace(code.charAt(start)))start++;
                    String[] names=m.group(5).split("\\s*,\\s*");
                    // GLSL list locations advance once per scalar/vector, including
                    // unused outputs. Never give two variables the same slot.
                    if(location!=null && (long)location+names.length>64)
                        throw new IllegalArgumentException("Conflicting/out-of-range ShaderC interface location");
                    for(int name=0;name<names.length;name++) {
                        result.add(new Decl(start,i+1,m.group(2),m.group(3),m.group(4),names[name],
                            location==null?null:location+name));
                        if(result.size()>128)throw new IllegalArgumentException("ShaderC interface budget exceeded");
                    }
                }
                boundary=i+1;
            }
        }
        if(braces!=0||parentheses!=0)throw new IllegalArgumentException("Unbalanced ShaderC source");
        return result;
    }

    private static String rewrite(String original,List<Decl> declarations,Map<String,Integer> inputs,Map<String,Integer> outputs) {
        var result=new StringBuilder(original.length()+256);int previous=0;
        for(int index=0;index<declarations.size();) {
            var first=declarations.get(index);
            result.append(original,previous,first.start);
            do {
                var decl=declarations.get(index++);
                result.append("layout(location=")
                    .append((decl.direction.equals("in")?inputs:outputs).get(decl.name)).append(") ")
                    .append(decl.qualifier).append(decl.direction).append(' ').append(decl.type).append(' ').append(decl.name).append(';');
                if(index>=declarations.size()||declarations.get(index).start!=first.start)break;
                result.append('\n');
            }while(true);
            previous=first.end;
        }
        result.append(original,previous,original.length());
        String source=result.toString();String inspected=mask(source,false);
        var version=VERSION.matcher(inspected);
        if(version.find()) {
            int start=version.start(),end=version.end();
            if(!SUPPORTED_VERSION.matcher(version.group()).matches())
                throw new IllegalArgumentException("ShaderC adapter expects prepared GLSL 330/450 core; refusing to change another language version");
            if(version.find())throw new IllegalArgumentException("Multiple ShaderC version directives");
            source=source.substring(0,start)+"#version 450 core"+source.substring(end);
        } else source="#version 450 core\n"+source;
        inspected=mask(source,true);
        if(Pattern.compile("\\bgl_InstanceID\\b").matcher(inspected).find())
            throw new IllegalArgumentException("gl_InstanceID requires backend-specific base-instance conversion");
        var builtin=Pattern.compile("\\bgl_VertexID\\b").matcher(inspected);
        result=new StringBuilder(source.length());previous=0;
        while(builtin.find()) {result.append(source,previous,builtin.start()).append("gl_VertexIndex");previous=builtin.end();}
        return result.append(source,previous,source.length()).toString();
    }

    /** Preserve offsets/newlines while masking comments, quoted text and optionally directives. */
    private static String mask(String source,boolean directives) {
        var chars=source.toCharArray();
        for(int i=0;i<chars.length;i++) {
            int start=i;
            if(i+1<chars.length&&chars[i]=='/'&&chars[i+1]=='/') {
                while(i<chars.length&&chars[i]!='\n')i++;
            } else if(i+1<chars.length&&chars[i]=='/'&&chars[i+1]=='*') {
                i+=2;while(i+1<chars.length&&!(chars[i]=='*'&&chars[i+1]=='/'))i++;
                if(i+1>=chars.length)throw new IllegalArgumentException("Unterminated shader comment");i+=2;
            } else if(chars[i]=='"') {
                i++;while(i<chars.length&&chars[i]!='"') {if(chars[i]=='\\')i++;i++;}
                if(i>=chars.length)throw new IllegalArgumentException("Unterminated shader string");i++;
            } else continue;
            for(int j=start;j<i;j++)if(chars[j]!='\n'&&chars[j]!='\r')chars[j]=' ';i--;
        }
        if(directives) {
            for(int i=0;i<chars.length;i++) if(chars[i]=='#') {
                boolean start=true;for(int j=i-1;j>=0&&chars[j]!='\n';j--)if(!Character.isWhitespace(chars[j])){start=false;break;}
                if(!start)continue;
                boolean more;
                do {
                    int end=i;while(end<chars.length&&chars[end]!='\n')end++;
                    int last=end-1;while(last>=i&&chars[last]=='\r')last--;
                    more=last>=i&&chars[last]=='\\';
                    while(i<end){if(chars[i]!='\r')chars[i]=' ';i++;}
                    if(more&&i<chars.length)i++;
                }while(more&&i<chars.length);
            }
        }
        return new String(chars);
    }
}
