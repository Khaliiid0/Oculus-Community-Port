package dev.oculusforge.render.program;

import dev.oculusforge.shaderpack.PackFileSystem;
import dev.oculusforge.shaderpack.VirtualPath;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded PNG decoding on the preparation thread. Never accesses network or ImageIO disk caches. */
public final class PackImageData {
    public static final int MAX_SIDE = 4096;
    public static final long MAX_PIXELS = 4L * 1024 * 1024;
    public static final long MAX_TOTAL_BYTES = 32L * 1024 * 1024;
    private static final long PNG_SIGNATURE = 0x89504E470D0A1A0AL;
    private final int width;
    private final int height;
    private final int[] pixelsAbgr;
    private final boolean blur;
    private final boolean clamp;

    private PackImageData(int width, int height, int[] pixels, boolean blur, boolean clamp) {
        this.width = width; this.height = height; this.pixelsAbgr = pixels;
        this.blur = blur; this.clamp = clamp;
    }

    public static PackImageData load(PackFileSystem pack, VirtualPath path, long remainingBytes)
        throws IOException, ShaderCompatibilityException {
        byte[] encoded = pack.read(path);
        boolean blur = false, clamp = false;
        VirtualPath metadata = VirtualPath.parse(path + ".mcmeta");
        if (pack.exists(metadata)) {
            String json = pack.readUtf8(metadata);
            if (json.length() > 4096) throw new ShaderCompatibilityException("Texture metadata is too large: " + metadata);
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                if (!root.keySet().equals(Set.of("texture"))) throw new IllegalArgumentException("Only texture metadata is supported");
                JsonObject texture = root.getAsJsonObject("texture");
                for (String key : texture.keySet()) {
                    if (!Set.of("blur", "clamp").contains(key) || !texture.get(key).isJsonPrimitive()
                        || !texture.get(key).getAsJsonPrimitive().isBoolean()) {
                        throw new IllegalArgumentException("Only boolean blur/clamp settings are supported");
                    }
                }
                blur = texture.has("blur") && texture.get("blur").getAsBoolean();
                clamp = texture.has("clamp") && texture.get("clamp").getAsBoolean();
            } catch (RuntimeException exception) {
                throw new ShaderCompatibilityException("Invalid or unsupported PNG metadata " + metadata + ": " + exception.getMessage());
            }
        }
        return decode(encoded, remainingBytes, blur, clamp);
    }

    static PackImageData decode(byte[] encoded, long remainingBytes, boolean blur, boolean clamp)
        throws IOException, ShaderCompatibilityException {
        if (encoded.length < 33 || encoded.length > 8 * 1024 * 1024) {
            throw new ShaderCompatibilityException("PNG file size is outside the supported bounds");
        }
        ByteBuffer header = ByteBuffer.wrap(encoded);
        if (header.getLong() != PNG_SIGNATURE || header.getInt() != 13 || header.getInt() != 0x49484452) {
            throw new ShaderCompatibilityException("Custom textures must be valid PNG files with an IHDR header");
        }
        int width = header.getInt(), height = header.getInt();
        int depth = Byte.toUnsignedInt(header.get());
        if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE
            || (long) width * height > MAX_PIXELS || (long) width * height * 4 > remainingBytes) {
            throw new ShaderCompatibilityException("Custom PNG exceeds dimension, pixel or 32 MiB aggregate texture budget");
        }
        if (depth != 1 && depth != 2 && depth != 4 && depth != 8) {
            throw new ShaderCompatibilityException("Only PNG bit depths up to 8 are supported; 16-bit data must not be silently reduced");
        }
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
        if (!readers.hasNext()) throw new IOException("This Java runtime has no PNG reader");
        ImageReader reader = readers.next();
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(validatedPixelsOnly(encoded)))) {
            reader.setInput(stream, true, true); // Ignore ancillary metadata, including compressed text.
            if (reader.getWidth(0) != width || reader.getHeight(0) != height) throw new IOException("PNG dimensions disagree");
            BufferedImage image = reader.read(0);
            try {
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                for (int i = 0; i < pixels.length; i++) {
                    int argb = pixels[i];
                    pixels[i] = (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
                }
                return new PackImageData(width, height, pixels, blur, clamp);
            } finally { image.flush(); }
        } finally { reader.dispose(); }
    }

    private static byte[] validatedPixelsOnly(byte[] encoded) throws ShaderCompatibilityException {
        var clean = new java.io.ByteArrayOutputStream(encoded.length);
        clean.write(encoded, 0, 8);
        ByteBuffer chunks = ByteBuffer.wrap(encoded);
        chunks.position(8);
        boolean headerSeen = false, dataSeen = false, dataEnded = false;
        int count = 0;
        while (chunks.remaining() >= 12) {
            if (++count > 4096) throw new ShaderCompatibilityException("PNG has too many chunks");
            int start = chunks.position(), length = chunks.getInt(), type = chunks.getInt();
            if (length < 0 || length > chunks.remaining() - 4) throw new ShaderCompatibilityException("Truncated PNG chunk");
            var crc = new java.util.zip.CRC32();
            crc.update(encoded, start + 4, length + 4);
            chunks.position(chunks.position() + length);
            if (Integer.toUnsignedLong(chunks.getInt()) != crc.getValue()) throw new ShaderCompatibilityException("PNG chunk checksum mismatch");
            if (type == 0x6163544C || type == 0x6663544C || type == 0x66644154) {
                throw new ShaderCompatibilityException("Animated PNG textures are not supported");
            }
            if (type == 0x49484452) {
                if (headerSeen || start != 8) throw new ShaderCompatibilityException("Duplicate or misplaced PNG header");
                headerSeen = true;
            }
            if (type == 0x49444154) {
                if (dataEnded) throw new ShaderCompatibilityException("PNG image-data chunks must be consecutive");
                dataSeen = true;
            } else if (dataSeen) dataEnded = true;
            boolean essential = type == 0x49484452 || type == 0x504C5445 || type == 0x49444154 || type == 0x49454E44;
            if (!essential && (type & 0x20000000) == 0) throw new ShaderCompatibilityException("Unknown critical PNG chunk");
            // Text/profile metadata is neither needed for shader data nor decompressed.
            // Keep palette transparency, which changes actual texel alpha.
            if (essential || type == 0x74524E53) clean.write(encoded, start, length + 12);
            if (type == 0x49454E44) {
                if (length != 0 || chunks.hasRemaining() || !dataSeen) throw new ShaderCompatibilityException("Invalid PNG end marker");
                return clean.toByteArray();
            }
        }
        throw new ShaderCompatibilityException("PNG is missing its end marker");
    }

    public int width() { return width; }
    public int height() { return height; }
    public int byteSize() { return pixelsAbgr.length * Integer.BYTES; }
    public int pixelAbgr(int index) { return pixelsAbgr[index]; }
    public boolean blur() { return blur; }
    public boolean clamp() { return clamp; }
}
