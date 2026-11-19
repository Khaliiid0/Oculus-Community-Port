package dev.oculusforge.render.program;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/** Immutable resource-pack declaration, not a guess based on a shader or PNG name. */
public enum MaterialFormat {
    DEFAULT, LAB_PBR, LAB_PBR_1_3;

    public static final int MAX_PROPERTIES_BYTES=4096;
    public static MaterialFormat parse(byte[] encoded) throws ShaderCompatibilityException {
        if(encoded.length>MAX_PROPERTIES_BYTES)throw new ShaderCompatibilityException("Material texture properties exceed 4 KiB");
        var properties=new Properties();
        try {properties.load(new ByteArrayInputStream(encoded));}
        catch(IOException|IllegalArgumentException invalid){throw new ShaderCompatibilityException("Invalid material texture properties");}
        String format=properties.getProperty("format","").trim();
        return switch(format) {
            case "" -> DEFAULT;
            case "lab-pbr" -> LAB_PBR;
            case "lab-pbr/1.3" -> LAB_PBR_1_3;
            default -> throw new ShaderCompatibilityException("Unsupported material texture format: "+format.substring(0,Math.min(64,format.length())));
        };
    }
    public Map<String,String> macros() {
        return switch(this) {
            case DEFAULT -> Map.of();
            case LAB_PBR -> Map.of("MC_TEXTURE_FORMAT_LAB_PBR","");
            case LAB_PBR_1_3 -> Map.of("MC_TEXTURE_FORMAT_LAB_PBR","","MC_TEXTURE_FORMAT_LAB_PBR_1_3","");
        };
    }
    public StaticMaterialAtlasData.Format atlasFormat() {
        return this==DEFAULT?StaticMaterialAtlasData.Format.LINEAR:StaticMaterialAtlasData.Format.LAB_PBR;
    }
    public String label(){return switch(this){case DEFAULT->"default";case LAB_PBR->"lab-pbr";case LAB_PBR_1_3->"lab-pbr/1.3";};}
    public void requireMatches(MaterialFormat current) throws ShaderCompatibilityException {
        if(this!=java.util.Objects.requireNonNull(current))throw new ShaderCompatibilityException(
            "Prepared material format "+label()+" does not match current resources "+current.label()+"; reprepare shaders");
    }
}
