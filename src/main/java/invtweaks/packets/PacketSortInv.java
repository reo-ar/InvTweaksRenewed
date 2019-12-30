package invtweaks.packets;

import java.util.*;
import java.util.function.*;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraft.util.*;
import net.minecraftforge.fml.network.*;
import net.minecraftforge.items.*;

public class PacketSortInv {
	private boolean isPlayer;
	
	public PacketSortInv(boolean isPlayer) { this.isPlayer = isPlayer; }
	public PacketSortInv(PacketBuffer buf) {
		this(buf.readBoolean());
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			//System.out.println("Teehee!");
			Comparator<ItemStack> cmp = Comparator.comparing(is -> is.getTranslationKey()); // TODO change comparator
			if (isPlayer) {
				PlayerInventory inv = ctx.get().getSender().inventory;
				List<ItemStack> sl = inv.mainInventory.subList(PlayerInventory.getHotbarSize(), inv.mainInventory.size());
				List<ItemStack> stacks = new ArrayList<>(sl);
				stacks.sort(cmp);
				
				ctx.get().getSender().getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP)
				.ifPresent(cap -> {
					Collections.fill(sl, ItemStack.EMPTY);
					
					int index = PlayerInventory.getHotbarSize();
					for (ItemStack stack: stacks) {
						while (!(stack = cap.insertItem(index, stack, false)).isEmpty()) { ++index; }
					}
				});
			} else {
				Container cont = ctx.get().getSender().openContainer;
				// check if an inventory is open
				if (cont != ctx.get().getSender().container) {
					// TODO add
				}
			}
		});
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeBoolean(isPlayer);
	}
}
