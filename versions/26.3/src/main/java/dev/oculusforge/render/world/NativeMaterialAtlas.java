package dev.oculusforge.render.world;

import dev.oculusforge.OculusForge;
import dev.oculusforge.render.program.PackGpuTexture;
import dev.oculusforge.render.program.ShaderCompatibilityException;
import dev.oculusforge.render.program.StaticMaterialAtlasData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** One static material atlas per shader generation; never decodes or uploads during a draw. */
public final class NativeMaterialAtlas implements AutoCloseable {
    private final com.mojang.renderpearl.api.textures.GpuTexture albedo;
    private final ResourceManager resources;
    private final PackGpuTexture texture;
    private final StaticMaterialAtlasData.Kind kind;
    private NativeMaterialAtlas(StaticMaterialAtlasData.Kind kind,com.mojang.renderpearl.api.textures.GpuTexture albedo,ResourceManager resources,PackGpuTexture texture) {
        this.kind=kind;this.albedo=albedo;this.resources=resources;this.texture=texture;
    }
    public static NativeMaterialAtlas load(StaticMaterialAtlasData.Kind kind) throws ShaderCompatibilityException {
        return load(kind,dev.oculusforge.render.program.MaterialFormatResource.read(Minecraft.getInstance().getResourceManager()));
    }
    public static NativeMaterialAtlas load(StaticMaterialAtlasData.Kind kind,dev.oculusforge.render.program.MaterialFormat expected) throws ShaderCompatibilityException {
        var minecraft=Minecraft.getInstance();
        var atlas=minecraft.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        atlas.missingSprite(); // Require completed stitch/upload, never assume an empty atlas means absent maps.
        var albedo=atlas.getTexture();
        var resources=minecraft.getResourceManager();
        expected.requireMatches(dev.oculusforge.render.program.MaterialFormatResource.read(resources));
        var locations=atlas.getTextureLocations();
        if(locations.size()>StaticMaterialAtlasData.MAX_SPRITES)throw new ShaderCompatibilityException("Too many block atlas sprites for material data");
        var sprites=new ArrayList<StaticMaterialAtlasData.Sprite>(locations.size());
        for(var location:locations.stream().sorted().toList()) {
            var sprite=atlas.getSprite(location);
            int padding=Math.round(sprite.getU0()*albedo.getWidth(0))-sprite.getX();
            if(padding!=Math.round(sprite.getV0()*albedo.getHeight(0))-sprite.getY())
                throw new ShaderCompatibilityException("Asymmetric material atlas padding is unsupported");
            String path=location.getPath();
            sprites.add(new StaticMaterialAtlasData.Sprite(location.getNamespace()+":"
                +(path.startsWith("optifine/cit/")?"":"textures/")+path+kind.suffix()+".png",
                sprite.getX(),sprite.getY(),sprite.contents().width(),sprite.contents().height(),padding));
        }
        try {
            var data=StaticMaterialAtlasData.prepare(kind,albedo.getWidth(0),albedo.getHeight(0),albedo.getMipLevels(),sprites,
                expected.atlasFormat(),name->{
                    var image=resources.getResource(Identifier.parse(name));
                    if(image.isEmpty())return Optional.empty();
                    var metadata=resources.getResource(Identifier.parse(name+".mcmeta"));
                    String text=metadata.isEmpty()?null:new String(read(metadata.get(),4096),java.nio.charset.StandardCharsets.UTF_8);
                    return Optional.of(new StaticMaterialAtlasData.Image(read(image.get(),8*1024*1024),text));
                });
            OculusForge.LOGGER.info("Oculus static {} atlas: {} resource maps, {}x{}, {} mip levels, {} bytes{}",
                kind.label(),data.mapCount(),data.width(0),data.height(0),data.mipCount(),data.byteSize(),
                data.mapCount()==0?" (verified absent-map default)":"");
            return new NativeMaterialAtlas(kind,albedo,resources,PackGpuTexture.create(data));
        }catch(IOException failure){throw new ShaderCompatibilityException("Could not read "+kind.label()+" atlas resources: "+failure.getMessage());}
    }
    private static byte[] read(net.minecraft.server.packs.resources.Resource resource,int limit) throws IOException,ShaderCompatibilityException {
        try(var stream=resource.open()) {
            byte[] bytes=stream.readNBytes(limit+1);
            if(bytes.length>limit)throw new ShaderCompatibilityException("Material resource exceeds its bounded read budget");
            return bytes;
        }
    }
    static void requireGeneration(Object resources,Object albedo,Object currentResources,Object currentAlbedo) {
        if(resources==null||albedo==null||resources!=currentResources||albedo!=currentAlbedo)
            throw new IllegalStateException("Material atlas belongs to an old resource generation");
    }
    public void requireCurrent() {
        var minecraft=Minecraft.getInstance();
        requireGeneration(resources,albedo,minecraft.getResourceManager(),minecraft.getAtlasManager()
            .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getTexture());
    }
    public void bind(com.mojang.renderpearl.api.commands.RenderPass pass) {
        requireCurrent();
        pass.setUniform(kind.uniform(),texture.view(),com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
            .getClampToEdge(kind==StaticMaterialAtlasData.Kind.NORMAL?com.mojang.renderpearl.api.textures.FilterMode.LINEAR
                :com.mojang.renderpearl.api.textures.FilterMode.NEAREST,true));
    }
    @Override public void close(){texture.close();}
}
