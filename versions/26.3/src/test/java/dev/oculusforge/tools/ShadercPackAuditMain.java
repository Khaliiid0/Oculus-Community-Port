package dev.oculusforge.tools;

import dev.oculusforge.port.ShadercInterfacePair;
import dev.oculusforge.port.TerrainShaderAbi;
import dev.oculusforge.render.program.NativeTerrainProgram;
import dev.oculusforge.render.program.PackProgramCompiler;
import dev.oculusforge.render.program.TerrainLayerPlan;
import dev.oculusforge.shaderpack.ShaderPackDescriptor;
import dev.oculusforge.shaderpack.ShaderPackLimits;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Test-classpath CLI: actual frontend compilation, never a GPU/device bootstrap. */
public final class ShadercPackAuditMain {
    private final Map<String, Result> results = new HashMap<>();
    private final Path dump;
    private int requested, failed, admissionFailures, dumped;
    private long dumpBytes;

    private ShadercPackAuditMain(Path dump) { this.dump = dump; }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args.length > 138)
            throw new IllegalArgumentException("Pass up to eight pack paths, optionally preceded by --dump-failures=NEW_DIRECTORY, --material-format=default|lab-pbr|lab-pbr/1.3 and --option=NAME=VALUE");
        Path dump = null;
        var format=dev.oculusforge.render.program.MaterialFormat.DEFAULT;
        int first = 0;
        boolean formatSpecified=false;
        var selectedOptions=new java.util.LinkedHashMap<String,String>();
        while(first<args.length&&args[first].startsWith("--")) {
            String option=args[first++];
            if(option.startsWith("--dump-failures=")) {
                if(dump!=null)throw new IllegalArgumentException("Duplicate dump option");
                String value=option.substring("--dump-failures=".length());
                if(value.isBlank())throw new IllegalArgumentException("Empty diagnostic directory");
                dump=Files.createDirectory(Path.of(value).toAbsolutePath().normalize());
            } else if(option.startsWith("--material-format=")) {
                if(formatSpecified)throw new IllegalArgumentException("Duplicate material format");formatSpecified=true;
                String value=option.substring("--material-format=".length());
                if(!java.util.Set.of("default","lab-pbr","lab-pbr/1.3").contains(value))throw new IllegalArgumentException("Unsupported audit material format");
                format=dev.oculusforge.render.program.MaterialFormat.parse((value.equals("default")?"":"format="+value).getBytes(StandardCharsets.ISO_8859_1));
            } else if(option.startsWith("--option=")) {
                String value=option.substring("--option=".length());
                int separator=value.indexOf('=');
                if(separator<=0||selectedOptions.putIfAbsent(value.substring(0,separator),value.substring(separator+1))!=null)
                    throw new IllegalArgumentException("Invalid or duplicate selected option");
            } else throw new IllegalArgumentException("Unknown audit option");
        }
        if (first == args.length || args.length - first > 8)
            throw new IllegalArgumentException("At least one and at most eight pack paths are required");
        int rejected = 0;
        var options=dev.oculusforge.shaderpack.ShaderOptionOverrides.validateValues(selectedOptions);
        System.out.println("SHADERC-MATERIAL-FORMAT "+format.label());
        System.out.println("SHADERC-SELECTED-OPTIONS "+new java.util.TreeMap<>(options));
        for (int i = first; i < args.length; i++) {
            Path path = Path.of(args[i]).toAbsolutePath().normalize();
            if (!Files.exists(path)) throw new IllegalArgumentException("Pack does not exist: " + path);
            var audit = new ShadercPackAuditMain(dump);
            try (var compiler = new Compiler()) {
                var descriptor = new ShaderPackDescriptor(path.getFileName().toString(), path.getFileName().toString(),
                    path, Files.isDirectory(path) ? ShaderPackDescriptor.Kind.DIRECTORY : ShaderPackDescriptor.Kind.ZIP);
                var prepared = new PackProgramCompiler(ShaderPackLimits.DEFAULT).prepare(descriptor,format,options);
                System.out.println("SHADERC-PACK " + path.getFileName());
                var nativePrograms=prepared.world().variants().values().stream().flatMap(world->world.nativePrograms().stream()).toList();
                System.out.printf("SHADERC-NATIVE-MATERIALS %s: normals=%d specular=%d (prepared native programs, not GPU samples)%n",
                    path.getFileName(),nativePrograms.stream().filter(NativeTerrainProgram::usesNormals).count(),
                    nativePrograms.stream().filter(NativeTerrainProgram::usesSpecular).count());
                for (var entry : new java.util.TreeMap<>(prepared.variants()).entrySet())
                    for (var program : entry.getValue().programs())
                        audit.pair(compiler, entry.getKey() + "/post/" + program.stageName(), program.vertexSource(), program.fragment().source());
                for (var entry : new java.util.TreeMap<>(prepared.world().variants()).entrySet()) {
                    var world = entry.getValue();
                    if (!world.terrainProgram().isBlank()) {
                        for (var layer : TerrainLayerPlan.Layer.values()) {
                            var program = world.terrainLayers().program(layer);
                            if (program.nativeProgram() == null) audit.reject(entry.getKey() + "/" + layer, program.issue());
                            else audit.terrain(compiler, entry.getKey() + "/" + layer, program.nativeProgram(),
                                world.terrainLayers().alphaTests().defines(program.name(),switch(layer) {case SOLID->null;case CUTOUT->.5f;case TRANSLUCENT->.1f;}));
                        }
                    }
                    if (world.shadowProgram()) {
                        if (world.nativeShadow() == null) audit.reject(entry.getKey() + "/shadow", world.nativeShadowIssue());
                        else audit.terrain(compiler, entry.getKey() + "/shadow", world.nativeShadow(),
                            world.terrainLayers().alphaTests().defines("shadow",.1f));
                    }
                }
            }
            System.out.printf("SHADERC-SUMMARY %s: requested=%d unique=%d failed=%d admissionFailures=%d (no GPU/link/visual validation)%n",
                path.getFileName(), audit.requested, audit.results.size(), audit.failed, audit.admissionFailures);
            if (audit.failed != 0 || audit.admissionFailures != 0) rejected++;
        }
        if (rejected != 0) throw new IllegalStateException(rejected + " pack(s) failed the ShaderC audit");
    }

    private void reject(String label, String issue) {
        admissionFailures++;
        System.out.println("ADMISSION-FAIL " + label + ": " + issue);
    }

    private void terrain(Compiler compiler, String label, NativeTerrainProgram program, Map<String,String> defines) throws Exception {
        for (boolean indirect : new boolean[] {false, true})
            pair(compiler, label + (indirect ? "/indirect" : "/separate"),
                TerrainShaderAbi.vertex(program.vertexSource(), indirect), program.fragmentSource(),defines);
    }

    private void pair(Compiler compiler, String label, String vertex, String fragment) throws Exception {
        pair(compiler,label,vertex,fragment,Map.of());
    }
    private void pair(Compiler compiler, String label, String vertex, String fragment, Map<String,String> defines) throws Exception {
        ShadercInterfacePair pair;
        try { pair = ShadercInterfacePair.adapt(vertex, fragment); }
        catch (IllegalArgumentException invalid) { reject(label, invalid.getMessage()); return; }
        for (boolean zeroToOne : new boolean[] {false, true}) {
            stage(compiler, label, pair.vertex(), Shaderc.shaderc_vertex_shader, zeroToOne,defines);
            stage(compiler, label, pair.fragment(), Shaderc.shaderc_fragment_shader, zeroToOne,defines);
        }
    }

    private void stage(Compiler compiler, String label, String source, int kind, boolean zeroToOne, Map<String,String> defines) throws Exception {
        requested++;
        String key = digest(kind + ":" + zeroToOne + ":" + new java.util.TreeMap<>(defines) + ":" + source);
        Result result = results.get(key);
        if (result == null) {
            if (results.size() >= 2048) throw new IllegalStateException("ShaderC audit compile budget exceeded");
            result = compiler.compile(source, kind, zeroToOne,defines);
            results.put(key, result);
            if (!result.success()) {
                System.out.printf("SHADERC-FAIL %s %s clip=%s:%n%s%n", label,
                    kind == Shaderc.shaderc_vertex_shader ? "vertex" : "fragment", zeroToOne ? "0..1" : "-1..1", result.message());
                byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
                if (dump != null && dumped < 64 && dumpBytes + bytes.length <= 16L * 1024 * 1024) {
                    Path file = dump.resolve(key + (kind == Shaderc.shaderc_vertex_shader ? ".vsh" : ".fsh"));
                    // Same failed source can recur in another pack; never overwrite it.
                    if (!Files.exists(file)) Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
                    dumped++; dumpBytes += bytes.length;
                    System.out.println("SHADERC-SOURCE " + file.getFileName());
                }
            }
        }
        if (!result.success()) failed++;
    }

    private static String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    record Result(boolean success, String message) { }

    static final class Compiler implements AutoCloseable {
        private long compiler = Shaderc.shaderc_compiler_initialize();
        Compiler() { if (compiler == 0) throw new IllegalStateException("Cannot initialize ShaderC"); }

        Result compile(String source, int kind, boolean zeroToOne) {
            return compile(source,kind,zeroToOne,Map.of());
        }
        Result compile(String source, int kind, boolean zeroToOne, Map<String,String> defines) {
            if (compiler == 0) throw new IllegalStateException("ShaderC audit compiler is closed");
            java.util.Objects.requireNonNull(source);
            if (source.length() > 32 * 1024 * 1024) throw new IllegalArgumentException("Shader source budget exceeded");
            if (kind != Shaderc.shaderc_vertex_shader && kind != Shaderc.shaderc_fragment_shader)
                throw new IllegalArgumentException("Only vertex/fragment stages are audited");
            long options = 0, result = 0;
            try {
                options = Shaderc.shaderc_compile_options_initialize();
                if (options == 0) throw new IllegalStateException("Cannot allocate ShaderC options");
                // Match the production frontend and ShadercProductionInputsTest.
                Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_2);
                Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
                Shaderc.shaderc_compile_options_set_preserve_bindings(options, false);
                Shaderc.shaderc_compile_options_set_generate_debug_info(options);
                Shaderc.shaderc_compile_options_set_optimization_level(options, Shaderc.shaderc_optimization_level_zero);
                Shaderc.shaderc_compile_options_set_auto_map_locations(options, false);
                for(var define:defines.entrySet())
                    Shaderc.shaderc_compile_options_add_macro_definition(options,define.getKey(),define.getValue());
                if (zeroToOne) for (String define : java.util.List.of("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE", "OCULUS_FRAGMENT_ZERO_TO_ONE", "OCULUS_SHADOW_ZERO_TO_ONE"))
                    Shaderc.shaderc_compile_options_add_macro_definition(options, define, "");
                // The CharSequence overload places the entire UTF-8 source on
                // LWJGL's small native stack. Real pack shaders can exceed it.
                var encoded = MemoryUtil.memUTF8(source, false);
                try (var stack = MemoryStack.stackPush()) {
                    result = Shaderc.shaderc_compile_into_spv(compiler, encoded, kind,
                        stack.UTF8("audited-pack.glsl"), stack.UTF8("main"), options);
                } finally { MemoryUtil.memFree(encoded); }
                if (result == 0) return new Result(false, "ShaderC returned no result");
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success) {
                    String message = Shaderc.shaderc_result_get_error_message(result);
                    return new Result(false, message.length() <= 4000 ? message : message.substring(0, 4000));
                }
                var bytes = Shaderc.shaderc_result_get_bytes(result);
                boolean valid = bytes != null && bytes.remaining() >= 4 && bytes.order(ByteOrder.LITTLE_ENDIAN).getInt(0) == 0x07230203;
                return new Result(valid, valid ? "" : "Invalid SPIR-V result");
            } finally {
                if (result != 0) Shaderc.shaderc_result_release(result);
                if (options != 0) Shaderc.shaderc_compile_options_release(options);
            }
        }

        @Override public void close() {
            if (compiler != 0) { Shaderc.shaderc_compiler_release(compiler); compiler = 0; }
        }
    }
}
