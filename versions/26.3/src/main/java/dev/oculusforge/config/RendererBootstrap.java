package dev.oculusforge.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraftforge.fml.loading.FMLPaths;

/** One immutable decision, possibly needed before the mod's constructor is called. */
public final class RendererBootstrap {
    public static final String EXTENDED="oculus.experimental.extendedTerrain";
    public static final String HDR="oculus.experimental.hdrWorldScene";
    public static final Snapshot ACTIVE=resolve(
        ()->FMLPaths.CONFIGDIR.get().resolve(RendererSettingsStore.FILE_NAME),System::getProperty);
    private RendererBootstrap(){}
    public record Snapshot(boolean requested,boolean extended,boolean hdr,boolean overridden,String issue){
        public boolean restartRequired(boolean desired){return !overridden&&(desired!=extended||desired!=hdr);}
    }
    static Snapshot resolve(Supplier<Path> path,Function<String,String> properties){
        String extended=properties.apply(EXTENDED),hdr=properties.apply(HDR);
        boolean requested=false;String issue="";
        // Headless tools supply both overrides and must not access the game's config directory.
        if(extended==null||hdr==null){
            try{requested=new RendererSettingsStore(path.get()).load();}
            catch(IOException|RuntimeException failure){issue="Startup renderer settings could not be read; disabled by default";}
        }
        if((extended!=null&&!extended.equals("true")&&!extended.equals("false"))
            ||(hdr!=null&&!hdr.equals("true")&&!hdr.equals("false")))
            issue="Invalid renderer JVM override; invalid values are disabled";
        return new Snapshot(requested,extended==null?requested:extended.equals("true"),
            hdr==null?requested:hdr.equals("true"),extended!=null||hdr!=null,issue);
    }
}
