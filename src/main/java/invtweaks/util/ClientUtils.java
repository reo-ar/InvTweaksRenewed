package invtweaks.util;

import net.minecraft.client.*;
import net.minecraft.entity.player.*;

public class ClientUtils {
	/**
	 * Java classloading is weird
	 */
	public static PlayerEntity safeGetPlayer() {
		return Minecraft.getInstance().player;
	}
}
