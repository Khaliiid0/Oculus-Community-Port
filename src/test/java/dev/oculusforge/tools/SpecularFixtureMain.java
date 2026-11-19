package dev.oculusforge.tools;

/** Generate binary diagnostic data only, into a newly named directory below the smoke workspace. */
public final class SpecularFixtureMain {
    private SpecularFixtureMain() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Pass the new isolated resource-pack directory");
        var root=java.nio.file.Path.of("tools/artifact-smoke").toAbsolutePath().normalize();
        var target=java.nio.file.Path.of(args[0]).toAbsolutePath().normalize();
        if(!target.startsWith(root)||target.equals(root)||java.nio.file.Files.exists(target))
            throw new IllegalArgumentException("A new diagnostic directory below tools/artifact-smoke is required");
        java.nio.file.Files.createDirectories(target.resolve("assets/minecraft/textures/block"));
        var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] colors={0x80204060,0xa0406080,0xc06080a0,0xe080a0c0};
        for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,colors[(y/8)*2+x/8]);
        if(!javax.imageio.ImageIO.write(image,"png",target.resolve("assets/minecraft/textures/block/stone_s.png").toFile()))
            throw new IllegalStateException("PNG writer missing");
        image.flush();
        net.minecraft.SharedConstants.tryDetectVersion();
        var version=net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        java.nio.file.Files.writeString(target.resolve("pack.mcmeta"),"{\"pack\":{\"description\":\"Oculus isolated static specular diagnostic\","
            +"\"min_format\":["+version.major()+","+version.minor()+"],\"max_format\":["+version.major()+","+version.minor()+"]}}",
            java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("Generated isolated four-color stone specular map: "+target);
    }
}
