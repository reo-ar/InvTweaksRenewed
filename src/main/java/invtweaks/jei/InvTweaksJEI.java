package invtweaks.jei;

import invtweaks.*;
import mezz.jei.api.*;
import mezz.jei.api.runtime.*;
import net.minecraft.util.*;

@JeiPlugin
public class InvTweaksJEI implements IModPlugin {
	public static final ResourceLocation UID = new ResourceLocation(InvTweaksMod.MODID, "jei_plugin");

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}
	
	@Override
	public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
		InvTweaksMod.setJEIKeyboardActiveFn(() -> jeiRuntime.getIngredientListOverlay().hasKeyboardFocus());
	}
}
