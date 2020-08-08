package invtweaks.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;

public class ClientUtils {
    /**
     * Java classloading is weird
     */
    public static PlayerEntity safeGetPlayer() {
        return Minecraft.getInstance().player;
    }
}
