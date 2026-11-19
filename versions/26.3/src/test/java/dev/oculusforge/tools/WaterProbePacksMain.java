package dev.oculusforge.tools;

import dev.oculusforge.shaderpack.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Local-only diagnostic derivatives. Original archives are read-only; new destinations only. */
public final class WaterProbePacksMain {
    private static final String HEADER="""
        #version 120
        /* DRAWBUFFERS:0 */
        """;
    private static final String FINAL="""
        #version 120
        const int colortex0Format=R11F_G11F_B10F;
        varying vec2 texcoord;
        uniform sampler2D colortex0;
        void main(){gl_FragColor=texture2D(colortex0,texcoord);}
        """;
    private static final Set<String> PROPERTIES=Set.of("shaders/shaders.properties","shaders/block.properties","shaders/item.properties");
    private WaterProbePacksMain() { }

    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass one existing source-pack ZIP");
        Path module=Path.of("").toRealPath();
        if(!Files.isRegularFile(module.resolve("RUNTIME_AUDIT.md")))throw new IllegalArgumentException("Run only from the 26.3 module");
        Path parent=module.resolve("run-runtime-audit/shaderpacks").toRealPath();
        if(!parent.startsWith(module)||Files.isSymbolicLink(parent))throw new IOException("Unsafe diagnostic root");
        Path source=Path.of(args[0]).toRealPath();
        var descriptor=new ShaderPackDescriptor("water-probe-source","Local water probe source",source,ShaderPackDescriptor.Kind.ZIP);
        try(var pack=descriptor.open(ShaderPackLimits.DEFAULT)) {
            var loader=new ShaderSourceLoader(pack,ShaderPackLimits.DEFAULT);
            var common=new TreeMap<String,byte[]>();
            for(String stage:List.of("gbuffers_terrain","shadow"))for(String extension:List.of("vsh","fsh")) {
                var path=VirtualPath.parse("shaders/world0/"+stage+"."+extension);
                common.put("shaders/"+stage+"."+extension,loader.load(path).source().getBytes(StandardCharsets.UTF_8));
            }
            for(var path:pack.files()) {
                String name=path.toString();
                if(PROPERTIES.contains(name)||name.startsWith("shaders/")&&name.endsWith(".png")
                    ||!name.startsWith("shaders/")&&(name.toLowerCase(Locale.ROOT).contains("license")||name.toLowerCase(Locale.ROOT).contains("readme")))
                    common.put(name,pack.read(path));
            }
            String vertex=loader.load(VirtualPath.parse("shaders/world0/gbuffers_water.vsh")).source();
            for(String kind:List.of("solid-fragment","neutral-position","material")) {
                Path destination=parent.resolve("oculus-water-probe-"+kind);
                if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Probe already exists; refusing overwrite: "+destination);
            }
            for(String kind:List.of("solid-fragment","neutral-position","material")) {
                var files=new TreeMap<>(common);
                boolean material=kind.equals("material");
                String probeVertex=kind.equals("solid-fragment")?vertex:neutralPosition(vertex,material);
                files.put("shaders/gbuffers_water.vsh",probeVertex.getBytes(StandardCharsets.UTF_8));
                String fragment=material?HEADER+"varying float waterProbeId;\nvoid main(){gl_FragColor=abs(waterProbeId-20000.)<.5?vec4(0,0,1,1):vec4(1,0,1,1);}\n"
                    :HEADER+"void main(){gl_FragColor=vec4(1,0,1,1);}\n";
                files.put("shaders/gbuffers_water.fsh",fragment.getBytes(StandardCharsets.UTF_8));
                files.put("shaders/final.fsh",FINAL.getBytes(StandardCharsets.UTF_8));
                files.put("LOCAL_DIAGNOSTIC_ONLY.txt",("DO NOT DISTRIBUTE. Local water isolation probe: "+kind
                    +"\nExpanded terrain/shadow/water vertex sources and assets from "+source.getFileName()
                    +". Original source archive is unchanged. Preserve upstream licensing; this is not a shader-pack release.\n").getBytes(StandardCharsets.UTF_8));
                writeNew(parent.resolve("oculus-water-probe-"+kind),files);
                System.out.println("WATER-PROBE "+kind+": "+files.size()+" files; local-only, no GPU qualification");
            }
        }
    }

    static String neutralPosition(String source,boolean material) {
        var version=Pattern.compile("(?m)^\\s*#version[^\\r\\n]*").matcher(source);
        if(!version.find())throw new IllegalArgumentException("Missing source version");
        String header=version.group()+"\n#define main waterProbePackMain\n";
        String result=version.replaceFirst(Matcher.quoteReplacement(header));
        return result+"\n#undef main\n"+(material?"varying float waterProbeId;\n":"")
            +"void main(){waterProbePackMain();gl_Position=ftransform();"+(material?"waterProbeId=mc_Entity.x;":"")+"}\n";
    }

    static Path target(Path root,String name) throws IOException {
        var path=VirtualPath.parse(name);
        for(String segment:path.toString().split("/"))
            if(!segment.matches("[A-Za-z0-9_.-]+")||segment.endsWith(".")
                ||segment.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
                throw new IOException("Nonportable diagnostic path");
        Path result=root.resolve(path.toString()).normalize();
        if(!result.startsWith(root)||result.equals(root))throw new IOException("Diagnostic path escaped its root");
        return result;
    }

    static void writeNew(Path destination,Map<String,byte[]> files) throws IOException {
        destination=destination.toAbsolutePath().normalize();
        long bytes=0;
        if(files.size()>512)throw new IOException("Diagnostic file count exceeded");
        for(var entry:files.entrySet()) {
            target(destination,entry.getKey());
            if(entry.getValue().length>ShaderPackLimits.DEFAULT.maxFileBytes())throw new IOException("Diagnostic file budget exceeded");
            bytes=Math.addExact(bytes,entry.getValue().length);
        }
        if(bytes>32L*1024*1024)throw new IOException("Diagnostic total budget exceeded");
        Files.createDirectory(destination); // Fails if anything, including a link, already exists.
        for(var entry:files.entrySet()) {
            Path output=target(destination,entry.getKey());
            Files.createDirectories(output.getParent());
            Files.write(output,entry.getValue(),StandardOpenOption.CREATE_NEW);
        }
    }
}
