package invtweaks.gui;

import invtweaks.InvTweaksMod;

public class InvTweaksButtonSort extends InvTweaksButton {

  public InvTweaksButtonSort(int x, int y, boolean isPlayer) {
    super(
        x,
        y,
        0,
        0,
        btn -> InvTweaksMod.requestSort(isPlayer));
  }
}
