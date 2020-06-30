package invtweaks.gui;

import invtweaks.InvTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.gui.GuiUtils;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

public class InvTweaksButton extends ExtendedButton {
  private final ResourceLocation tex;
  private final int tx;
  private final int ty;

  protected static final ResourceLocation BUTTON_SPRITES =
      new ResourceLocation(InvTweaksMod.MODID, "textures/gui/button_sprites.png");

  public InvTweaksButton(
      int x, int y, int tx, int ty, ResourceLocation tex, Button.IPressable handler) {
    super(x, y, 12, 12, "", handler);
    this.tex = tex;
    this.tx = tx;
    this.ty = ty;
  }

  // private static final Field tabPageF =
  // ObfuscationReflectionHelper.findField(CreativeScreen.class, "tabPage");

  @Override
  protected void renderBg(Minecraft mc, int mouseX, int mouseY) {
    /*try {
    	visible = tabPageF.getInt(null) == ItemGroup.INVENTORY.getIndex();
    } catch (Exception e) {
    	Throwables.throwIfUnchecked(e);
    	throw new RuntimeException(e);
    }*/
    if (visible) {
      mc.textureManager.bindTexture(tex);
      GuiUtils.drawTexturedModalRect(x, y, tx, ty, width, height, this.getBlitOffset());
    }
  }
}
