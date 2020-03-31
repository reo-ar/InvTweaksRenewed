package invtweaks.packets;

import java.util.function.*;

import invtweaks.util.*;
import net.minecraft.network.*;
import net.minecraftforge.fml.network.*;

public class PacketSortInv {
	private boolean isPlayer;
	
	public PacketSortInv(boolean isPlayer) { this.isPlayer = isPlayer; }
	public PacketSortInv(PacketBuffer buf) {
		this(buf.readBoolean());
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			//System.out.println("Received message from client!");
			Sorting.executeSort(ctx.get().getSender(), isPlayer);
		});
		ctx.get().setPacketHandled(true);
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeBoolean(isPlayer);
	}
}
