package invtweaks.packets;

import invtweaks.util.Sorting;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSortInv {
    private final boolean isPlayer;

    public PacketSortInv(boolean isPlayer) {
        this.isPlayer = isPlayer;
    }

    public PacketSortInv(PacketBuffer buf) {
        this(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get()
                .enqueueWork(
                        () -> {
                            // System.out.println("Received message from client!");
                            Sorting.executeSort(ctx.get().getSender(), isPlayer);
                        });
        ctx.get().setPacketHandled(true);
    }

    public void encode(PacketBuffer buf) {
        buf.writeBoolean(isPlayer);
    }
}
