package invtweaks.packets;

import java.util.*;
import java.util.function.*;

import net.minecraft.entity.player.*;
import net.minecraft.network.*;
import net.minecraftforge.fml.network.*;

public class PacketSortPlayerInv {
	public PacketSortPlayerInv() {}
	public PacketSortPlayerInv(PacketBuffer buf) {}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			//System.out.println("Teehee!");
			PlayerInventory inv = ctx.get().getSender().inventory;
			inv.mainInventory.subList(PlayerInventory.getHotbarSize(), inv.mainInventory.size())
			.sort(Comparator.comparing(is -> is.getTranslationKey())); // TODO change comparator
		});
	}
	
	public void encode(PacketBuffer buf) {}
}
