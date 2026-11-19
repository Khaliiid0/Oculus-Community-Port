package dev.oculusforge.render.world;

import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.PackGpuTexture;
import dev.oculusforge.render.program.ShaderCompatibilityException;
import dev.oculusforge.render.program.SpecularAtlasData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** One static material atlas per shader generation; never decodes or uploads during a draw. */
public final class NativeSpecularAtlas implements AutoCloseable {
    private final com.mojang.blaze3d.textures.GpuTexture albedo;
    private final ResourceManager resources;
    private final PackGpuTexture texture;
    private NativeSpecularAtlas(com.mojang.blaze3d.textures.GpuTexture albedo,ResourceManager resources,PackGpuTexture texture) {
        this.albedo=albedo;this.resources=resources;this.texture=texture;
    }
    public static NativeSpecularAtlas load() throws ShaderCompatibilityException {
        var minecraft=Minecraft.getInstance();
        var atlas=minecraft.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        atlas.missingSprite(); // Require completed stitch/upload, never assume an empty atlas means absent maps.
        var albedo=atlas.getTexture();
        var resources=minecraft.getResourceManager();
        var locations=atlas.getTextureLocations();
        if(locations.size()>SpecularAtlasData.MAX_SPRITES)throw new ShaderCompatibilityException("Too many block atlas sprites for specular data");
        var sprites=new ArrayList<SpecularAtlasData.Sprite>(locations.size());
        for(var location:locations.stream().sorted().toList()) {
            var sprite=atlas.getSprite(location);
            int padding=Math.round(sprite.getU0()*albedo.getWidth(0))-sprite.getX();
            if(padding!=Math.round(sprite.getV0()*albedo.getHeight(0))-sprite.getY())
                throw new ShaderCompatibilityException("Asymmetric specular atlas padding is unsupported");
            String path=location.getPath();
            sprites.add(new SpecularAtlasData.Sprite(location.getNamespace()+":"
                +(path.startsWith("optifine/cit/")?"":"textures/")+path+"_s.png",
                sprite.getX(),sprite.getY(),sprite.contents().width(),sprite.contents().height(),padding));
        }
        try {
            var data=SpecularAtlasData.prepare(albedo.getWidth(0),albedo.getHeight(0),albedo.getMipLevels(),sprites,
                format(resources),name->{
                    var image=resources.getResource(Identifier.parse(name));
                    if(image.isEmpty())return Optional.empty();
                    var metadata=resources.getResource(Identifier.parse(name+".mcmeta"));
                    String text=metadata.isEmpty()?null:new String(read(metadata.get(),4096),java.nio.charset.StandardCharsets.UTF_8);
                    return Optional.of(new SpecularAtlasData.Image(read(image.get(),8*1024*1024),text));
                });
            OculusForge.LOGGER.info("Oculus static specular atlas: {} resource maps, {}x{}, {} mip levels, {} bytes{}",
                data.mapCount(),data.width(0),data.height(0),data.mipCount(),data.byteSize(),
                data.mapCount()==0?" (verified absent-map default)":"");
            return new NativeSpecularAtlas(albedo,resources,PackGpuTexture.create(data));
        }catch(IOException failure){throw new ShaderCompatibilityException("Could not read specular atlas resources: "+failure.getMessage());}
    }
    private static byte[] read(net.minecraft.server.packs.resources.Resource resource,int limit) throws IOException,ShaderCompatibilityException {
        try(var stream=resource.open()) {
            byte[] bytes=stream.readNBytes(limit+1);
            if(bytes.length>limit)throw new ShaderCompatibilityException("Specular resource exceeds its bounded read budget");
            return bytes;
        }
    }
    private static SpecularAtlasData.Format format(ResourceManager resources) throws IOException,ShaderCompatibilityException {
        var properties=resources.getResource(Identifier.withDefaultNamespace("optifine/texture.properties"));
        if(properties.isEmpty())return SpecularAtlasData.Format.LINEAR;
        return SpecularAtlasData.parseFormat(read(properties.get(),4096));
    }
    static void requireGeneration(Object resources,Object albedo,Object currentResources,Object currentAlbedo) {
        if(resources==null||albedo==null||resources!=currentResources||albedo!=currentAlbedo)
            throw new IllegalStateException("Specular atlas belongs to an old resource generation");
    }
    public void requireCurrent() {
        var minecraft=Minecraft.getInstance();
        requireGeneration(resources,albedo,minecraft.getResourceManager(),minecraft.getAtlasManager()
            .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getTexture());
    }
    public void bind(com.mojang.blaze3d.systems.RenderPass pass) {
        requireCurrent();
        pass.bindTexture("OculusSpecular",texture.view(),com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
            .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.NEAREST,true));
    }
    @Override public void close(){texture.close();}
}
