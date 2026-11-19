package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.*;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dimension-resolved CPU sky plans. Prepared does not mean runtime eligible. */
public record SkyProgramPlan(Map<String, Program> programs) {
    public static final List<String> STAGES = List.of("gbuffers_skybasic", "gbuffers_skytextured");
    public static final SkyProgramPlan EMPTY = new SkyProgramPlan(Map.of());
    public SkyProgramPlan { programs = Map.copyOf(programs); }
    public record Program(String sourceRoot, NativeSkyProgram prepared, String issue) {
        public Program {
            java.util.Objects.requireNonNull(sourceRoot);
            java.util.Objects.requireNonNull(issue);
            if ((prepared == null) == issue.isEmpty()) throw new IllegalArgumentException("Sky needs a prepared pair or an issue");
        }
    }
    static String sourceRoot(PackFileSystem pack, String root, String stage) throws IOException {
        // A partial dimension override is an error, not permission to mix shader pairs.
        if (pack.exists(VirtualPath.parse(root + "/" + stage + ".vsh"))
            || pack.exists(VirtualPath.parse(root + "/" + stage + ".fsh"))) return root;
        return "shaders";
    }
    static SkyProgramPlan inspect(PackFileSystem pack, String root, ShaderPackLimits limits,
        Map<String,String> environment, NativeCustomUniforms custom) throws IOException, ShaderCompatibilityException {
        var programs = new LinkedHashMap<String, Program>();
        for (String stage : STAGES) {
            String selected = sourceRoot(pack, root, stage);
            var vertex = VirtualPath.parse(selected + "/" + stage + ".vsh");
            var fragment = VirtualPath.parse(selected + "/" + stage + ".fsh");
            if (!pack.exists(vertex) && !pack.exists(fragment)) continue;
            if (!pack.exists(vertex) || !pack.exists(fragment)) {
                programs.put(stage, new Program(selected, null, "Missing paired sky vertex or fragment shader"));
                continue;
            }
            String v = GlslPreprocessor.preprocess(new ShaderSourceLoader(pack, limits).load(vertex).source(), environment);
            String f = GlslPreprocessor.preprocess(new ShaderSourceLoader(pack, limits).load(fragment).source(), environment);
            try {
                programs.put(stage, new Program(selected,
                    NativeSkyProgram.adapt(v, f, WorldProgramPlan.parseTargets(f, stage), custom), ""));
            } catch (ShaderCompatibilityException unsupported) {
                programs.put(stage, new Program(selected, null, unsupported.getMessage()));
            }
        }
        return new SkyProgramPlan(programs);
    }
}
