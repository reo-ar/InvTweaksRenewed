package invtweaks.gui;

import invtweaks.InvTweaksMod;

public class InvTweaksButtonSort extends InvTweaksButton {

  public InvTweaksButtonSort(int x, int y, boolean isPlayer) {
    super(
        x,
        y,
        0,
        0,
        BUTTON_SPRITES,
        btn -> {
          InvTweaksMod.requestSort(isPlayer);
        });
  }
}
