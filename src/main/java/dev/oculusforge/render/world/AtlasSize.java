package dev.oculusforge.render.world;

/** Dimensions of the live block albedo atlas, captured with the camera environment. */
public record AtlasSize(int width, int height) {
    public static final AtlasSize ABSENT = new AtlasSize(0, 0);
    public AtlasSize {
        if (width < 0 || height < 0 || (width == 0) != (height == 0))
            throw new IllegalArgumentException("Invalid block atlas dimensions");
    }
    public static AtlasSize capture() {
        var texture = net.minecraft.client.Minecraft.getInstance().getAtlasManager()
            .getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getTexture();
        int width = texture.getWidth(0), height = texture.getHeight(0);
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Block atlas is not ready");
        return new AtlasSize(width, height);
    }
}
