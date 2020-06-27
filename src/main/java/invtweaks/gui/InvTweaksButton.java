package invtweaks.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import invtweaks.InvTweaksMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

public class InvTweaksButton extends ExtendedButton {
	private final ResourceLocation tex;
	private final int tx;
	private final int ty;

	protected static final ResourceLocation button = new ResourceLocation(InvTweaksMod.MODID, "textures/gui/button_sprites.png");
	
	public InvTweaksButton(int x, int y, int tx, int ty, ResourceLocation tex, IPressable handler) {
		super(x, y, 16, 16, new StringTextComponent(""), handler);
		this.tex = tex;
		this.tx = tx;
		this.ty = ty;
	}

	@Override
	public void func_230431_b_(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
		final boolean active = field_230693_o_;
		final boolean visible = field_230694_p_;
		final int x = field_230690_l_;
		final int y = field_230691_m_;
		final int width = field_230688_j_;
		final int height = field_230689_k_;
		field_230692_n_ = active && visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
		Minecraft.getInstance().getTextureManager().bindTexture(button);
		func_238474_b_(matrixStack, x, y, tx, ty + (field_230692_n_ ? 12 : 0), 12,12);
	}
}
