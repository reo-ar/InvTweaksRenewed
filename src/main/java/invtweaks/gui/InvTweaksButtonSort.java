package invtweaks.gui;

import invtweaks.*;
import invtweaks.packets.*;

public class InvTweaksButtonSort extends InvTweaksButton {
	@SuppressWarnings("unused")
	private boolean isPlayer;

	public InvTweaksButtonSort(int x, int y, boolean isPlayer) {
		super(x, y, 0, 0, BUTTON_SPRITES, btn -> {
			InvTweaksMod.NET_INST.sendToServer(new PacketSortInv(isPlayer));
		});
		this.isPlayer = isPlayer;
	}
	
}
