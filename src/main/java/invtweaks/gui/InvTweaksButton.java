package invtweaks.gui;

import invtweaks.InvTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

import javax.annotation.ParametersAreNonnullByDefault;

public class InvTweaksButton extends ExtendedButton {
    protected static final ResourceLocation button =
            new ResourceLocation(InvTweaksMod.MODID, "textures/gui/button_sprites.png");
    private final int tx;
    private final int ty;

    public InvTweaksButton(
            int x, int y, int tx, int ty, IPressable handler) {
        super(x, y, 12, 12, "", handler);
        this.tx = tx;
        this.ty = ty;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void render(int mouseX, int mouseY, float partialTicks) {
        isHovered =
                this.active
                        && mouseX >= this.x
                        && mouseY >= this.y
                        && mouseX < this.x + this.width
                        && mouseY < this.y + this.height;
        Minecraft.getInstance().getTextureManager().bindTexture(button);
        blit(x, y, tx, ty + (isHovered ? 16 : 0), 14, 16);
    }

}
