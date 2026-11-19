package dev.oculusforge.render.world;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Immutable extraction snapshot. Null means no player/hand; an empty stack is minecraft:air. */
public record HeldItemKeys(String mainHand, String offHand) {
    public static final HeldItemKeys NONE = new HeldItemKeys(null, null);
    public HeldItemKeys {
        for (String key : new String[]{mainHand, offHand}) if (key != null && !valid(key))
            throw new IllegalArgumentException("Invalid held item/model identifier");
    }
    public static boolean valid(String key) { return key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
    public static String modelOrItem(String model, String item) { return model == null ? item : model; }
    public static HeldItemKeys capture(Player player) {
        return player == null ? NONE : new HeldItemKeys(key(player.getMainHandItem()), key(player.getOffhandItem()));
    }
    private static String key(ItemStack stack) {
        if (stack == null) return null;
        var model = stack.get(DataComponents.ITEM_MODEL);
        return modelOrItem(model == null ? null : model.toString(), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}
