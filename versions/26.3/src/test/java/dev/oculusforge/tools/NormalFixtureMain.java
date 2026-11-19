package dev.oculusforge.tools;

/** Original numeric PNG data, generated only into a NEW isolated audit resource pack. */
public final class NormalFixtureMain {
    private NormalFixtureMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length!=1||!args[0].matches("oculus-normal-audit-[a-z0-9-]+"))throw new IllegalArgumentException("Expected new audit pack basename");
        var root=java.nio.file.Path.of("run-runtime-audit/resourcepacks").toAbsolutePath().normalize();
        var target=generate(root,args[0]);
        net.minecraft.SharedConstants.tryDetectVersion();
        var version=net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        System.out.println("Generated numeric normal/specular resource pack in "+target);
        System.out.println("CLIENT_PACK_FORMAT="+version.major()+"."+version.minor());
    }
    static java.nio.file.Path generate(java.nio.file.Path root,String name) throws Exception {
        if(!name.matches("oculus-normal-audit-[a-z0-9-]+"))throw new IllegalArgumentException("Expected new audit pack basename");
        root=root.toAbsolutePath().normalize();
        var target=root.resolve(name).normalize();
        if(!target.getParent().equals(root)||java.nio.file.Files.exists(target))throw new IllegalArgumentException("Resource pack target must be new");
        java.nio.file.Files.createDirectories(target.resolve("assets/minecraft/textures/block"));
        int[] normals={0x20404040,0x60c04080,0xa040c0c0,0xe0c0c0fc};
        for(String suffix:java.util.List.of("n","s")) {
            var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)image.setRGB(x,y,suffix.equals("n")?normals[(y/8)*2+x/8]:0x40102030);
            if(!javax.imageio.ImageIO.write(image,"png",target.resolve("assets/minecraft/textures/block/stone_"+suffix+".png").toFile()))throw new IllegalStateException("PNG writer missing");
            image.flush();
        }
        for(String file:java.util.List.of("pack.mcmeta","assets/minecraft/optifine/texture.properties")) {
            java.nio.file.Files.createDirectories(target.resolve(file).getParent());
            try(var input=java.util.Objects.requireNonNull(NormalFixtureMain.class.getClassLoader().getResourceAsStream("normal-resource-template/"+file))) {
                java.nio.file.Files.copy(input,target.resolve(file)); // No replacement option: a concurrent file is not overwritten.
            }
        }
        return target;
    }
}
