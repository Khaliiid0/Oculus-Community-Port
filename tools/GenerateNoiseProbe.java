import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
/** Reproducible numerical test image, not an artist-pack asset. Run from repository root. */
class GenerateNoiseProbe {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--print-generated")) {
            var random = new java.util.Random(0x4F43554C55534CL);
            for (int i = 0; i < 4; i++) {
                int pixel = random.nextInt();
                System.out.printf("%d,%d,%d,255%n", pixel & 255, (pixel >>> 8) & 255, (pixel >>> 16) & 255);
            }
            return;
        }
        Path path = Path.of("test-packs/native-noise/shaders/noise.png");
        if (Files.exists(path)) throw new IllegalStateException("Refusing to replace existing fixture");
        BufferedImage image = new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xff114383); image.setRGB(1,0,0xffd31f59);
        image.setRGB(0,1,0xff2bad07); image.setRGB(1,1,0xff65e535);
        if (!ImageIO.write(image,"png",path.toFile())) throw new IllegalStateException("No PNG writer");
    }
}
