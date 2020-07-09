package invtweaks.jei;

import invtweaks.InvTweaksMod;
import mcp.MethodsReturnNonnullByDefault;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.util.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("unused")
@JeiPlugin
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class InvTweaksJEI implements IModPlugin {
  public static final ResourceLocation UID = new ResourceLocation(InvTweaksMod.MODID, "jei_plugin");

  @Override
  public ResourceLocation getPluginUid() {
    return UID;
  }

  @Override
  public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
    InvTweaksMod.setJEIKeyboardActiveFn(
        () -> jeiRuntime.getIngredientListOverlay().hasKeyboardFocus());
  }
}
