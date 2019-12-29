package invtweaks.gui;

import invtweaks.*;
import invtweaks.packets.*;

public class InvTweaksButtonSort extends InvTweaksButton {

	public InvTweaksButtonSort(int x, int y) {
		super(x, y, 0, 0, BUTTON_SPRITES, btn -> {
			InvTweaksMod.NET_INST.sendToServer(new PacketSortPlayerInv());
		});
	}
	
}
