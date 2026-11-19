package dev.oculusforge.render.program;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Only the winning Minecraft resource is read; bounded, closed, and no GPU bootstrap. */
public final class MaterialFormatResource {
    public static final Identifier LOCATION=Identifier.withDefaultNamespace("optifine/texture.properties");
    @FunctionalInterface public interface Source { Optional<InputStream> open() throws IOException; }
    private MaterialFormatResource() {}
    public static MaterialFormat read(ResourceManager manager) throws ShaderCompatibilityException {
        return read(source(manager));
    }
    private static Source source(ResourceManager manager) {
        java.util.Objects.requireNonNull(manager);
        return ()->{
            var resource=manager.getResource(LOCATION);
            return resource.isEmpty()?Optional.empty():Optional.of(resource.get().open());
        };
    }
    public static MaterialFormat read(Source source) throws ShaderCompatibilityException {
        try {
            var optional=source.open();
            if(optional.isEmpty())return MaterialFormat.DEFAULT;
            try(var input=optional.get()) {
                return MaterialFormat.parse(input.readNBytes(MaterialFormat.MAX_PROPERTIES_BYTES+1));
            }
        }catch(IOException|IllegalStateException failure){
            var rejected=new ShaderCompatibilityException("Could not read material texture properties: "+failure.getMessage());
            rejected.initCause(failure);throw rejected;
        }
    }
    /** Invalid declarations are a context too: retry stale work once, then report its error. */
    public static boolean matches(MaterialFormat captured,Source current) {
        try{return captured==read(current);}catch(ShaderCompatibilityException invalid){return captured==null;}
    }
    public static boolean matches(MaterialFormat captured,ResourceManager current) {return matches(captured,source(current));}
}
