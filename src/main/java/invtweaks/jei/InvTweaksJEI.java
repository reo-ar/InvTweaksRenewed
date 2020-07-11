package invtweaks.jei;

import invtweaks.InvTweaksMod;
import mcp.MethodsReturnNonnullByDefault;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@JeiPlugin
public class InvTweaksJEI implements IModPlugin {
    public static final ResourceLocation UID = new ResourceLocation(InvTweaksMod.MODID, "jei_plugin");

    @Override
    @Nonnull
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        InvTweaksMod.setJEIKeyboardActiveFn(() -> jeiRuntime.getIngredientListOverlay().hasKeyboardFocus());
    }
}
