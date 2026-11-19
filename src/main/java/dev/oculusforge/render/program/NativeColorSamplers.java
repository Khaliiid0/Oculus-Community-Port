package dev.oculusforge.render.program;

import java.util.List;
import java.util.stream.IntStream;

/** Auxiliary world images only; the borrowed main scene needs a separate snapshot. */
public final class NativeColorSamplers {
    private NativeColorSamplers() {}
    public static int index(String name) {
        return switch (name) {
            case "gcolor" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "composite" -> 3;
            case "gaux1" -> 4;
            case "gaux2" -> 5;
            case "gaux3" -> 6;
            case "gaux4" -> 7;
            default -> name.matches("colortex(?:[0-9]|1[0-5])") ? Integer.parseInt(name.substring(8)) : -1;
        };
    }
    public static String canonical(int index) { return "OculusColor" + index; }
    public static List<Integer> inputs(String source) {
        return IntStream.range(1,16).filter(i -> source.contains("uniform sampler2D " + canonical(i) + ";"))
            .boxed().toList();
    }
    static String validate(String name, List<Integer> targets, boolean shadow) throws ShaderCompatibilityException {
        int index=index(name);
        if(index<0) throw new ShaderCompatibilityException("Unsupported native terrain sampler " + name);
        if(shadow) throw new ShaderCompatibilityException("Native shadow cannot sample world color buffers");
        if(index==0) throw new ShaderCompatibilityException("Native terrain colortex0 sampling requires a scene snapshot");
        if(targets.contains(index)) throw new ShaderCompatibilityException("Native terrain color feedback: " + name + " is also an output attachment");
        return canonical(index);
    }
}
