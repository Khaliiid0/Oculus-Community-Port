package dev.oculusforge.tools;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Offline setup for one disposable GPU fixture only. Never included in the mod. */
public final class MaterialFluidWorldMain {
    private MaterialFluidWorldMain() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected the repository root only");
        Path root = Path.of(args[0]).toRealPath();
        Path save = root.resolve("tools/artifact-smoke/run-26.2-material-fluids-20260916/saves/BufferRoutingTest");
        if (!save.toRealPath().equals(save) || !save.startsWith(root))
            throw new IllegalArgumentException("Fixture path must not traverse links");
        Path level = save.resolve("level.dat");
        if (!Files.isRegularFile(level, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Missing regular fixture level.dat");
        // Minecraft uses this lock too. Refuse to modify a running world.
        try (var channel = FileChannel.open(save.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Fixture world is running");
            var data = NbtIo.readCompressed(level, NbtAccounter.create(4L * 1024 * 1024));
            var world = data.getCompound("Data").orElseThrow();
            world.putBoolean("allowCommands", true);
            Path backup = save.resolve("level.dat.before-fluid-test");
            Files.copy(level, backup); // Refuse to replace an earlier backup.
            Path temporary = Files.createTempFile(save, "fluid-setup-", ".dat");
            NbtIo.writeCompressed(data, temporary);
            if (!NbtIo.readCompressed(temporary, NbtAccounter.create(4L * 1024 * 1024))
                .getCompound("Data").orElseThrow().getBoolean("allowCommands").orElse(false))
                throw new IllegalStateException("Written fixture did not verify");
            Files.move(temporary, level, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Enabled commands only in disposable fluid fixture; original level.dat backed up.");
        }
    }
}
