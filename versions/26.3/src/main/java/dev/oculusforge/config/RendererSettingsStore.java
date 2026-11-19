package dev.oculusforge.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Properties;

/** Separate startup preference; never changes the selected pack or active mesh layout. */
public final class RendererSettingsStore {
    public static final String FILE_NAME="oculus-renderer.properties";
    private static final int MAX_BYTES=4096;
    private final Path path;
    public RendererSettingsStore(Path path){this.path=path.toAbsolutePath().normalize();}
    public Path path(){return path;}
    public boolean load() throws IOException {
        BasicFileAttributes attributes;
        try{attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);}
        catch(NoSuchFileException absent){return false;}
        if(!attributes.isRegularFile()||attributes.isSymbolicLink()||attributes.size()>MAX_BYTES)
            throw new IOException("Renderer settings must be a regular file of at most 4096 bytes");
        byte[] bytes;
        try(var input=Files.newInputStream(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            bytes=input.readNBytes(MAX_BYTES+1);
        }
        if(bytes.length>MAX_BYTES)throw new IOException("Renderer settings exceed 4096 bytes");
        var properties=new Properties(){
            private static final long serialVersionUID=1L;
            @Override public synchronized Object put(Object key,Object value){
                if(containsKey(key))throw new IllegalArgumentException("Duplicate renderer setting");
                return super.put(key,value);
            }
        };
        try{
            properties.load(new ByteArrayInputStream(bytes));
            String value=properties.getProperty("worldRenderer.enabled","false").trim();
            return switch(value){case "true"->true;case "false"->false;default->throw new IllegalArgumentException("Expected true or false");};
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid renderer settings",invalid);}
    }
    public void save(boolean enabled) throws IOException {
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
            var attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile()||attributes.isSymbolicLink())throw new IOException("Refusing non-regular renderer settings");
        }
        Files.createDirectories(path.getParent());
        Path temporary=Files.createTempFile(path.getParent(),"oculus-renderer-",".tmp");
        boolean moved=false;
        try{
            Files.writeString(temporary,"# Oculus 26.3 world renderer; restart Minecraft after changing this setting.\n"
                +"worldRenderer.enabled="+enabled+"\n",StandardCharsets.UTF_8);
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
            moved=true;
        }finally{if(!moved)Files.deleteIfExists(temporary);}
    }
}
