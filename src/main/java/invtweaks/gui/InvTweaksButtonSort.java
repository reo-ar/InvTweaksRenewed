package invtweaks.gui;

import invtweaks.*;

public class InvTweaksButtonSort extends InvTweaksButton {
	@SuppressWarnings("unused")
	private boolean isPlayer;

	public InvTweaksButtonSort(int x, int y, boolean isPlayer) {
		super(x, y, 0, 0, BUTTON_SPRITES, btn -> {
			InvTweaksMod.requestSort(isPlayer);
		});
		this.isPlayer = isPlayer;
	}
	
}
