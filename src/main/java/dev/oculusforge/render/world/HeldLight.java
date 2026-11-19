package dev.oculusforge.render.world;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** One render-frame snapshot; values are block emission, not item identifiers. */
public record HeldLight(int mainHand, int offHand) {
    public static final HeldLight NONE = new HeldLight(0, 0);

    public static boolean legacyMode(String propertiesText) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(propertiesText));
        return !"false".equals(properties.getProperty("oldHandLight", "true").trim());
    }

    public static HeldLight resolve(int main, int off, boolean legacy) {
        return new HeldLight(legacy ? Math.max(main, off) : main, off);
    }

    public static HeldLight capture(Player player, boolean legacy) {
        return player == null ? NONE : resolve(emission(player, player.getMainHandItem()),
            emission(player, player.getOffhandItem()), legacy);
    }

    public static int emission(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem block)) return 0;
        var state = block.getBlock().defaultBlockState();
        var properties = stack.get(DataComponents.BLOCK_STATE);
        if (properties != null) state = properties.apply(state);
        return state.getLightEmission(player.level(), player.blockPosition());
    }
}
