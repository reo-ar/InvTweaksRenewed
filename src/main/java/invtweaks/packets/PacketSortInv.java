package invtweaks.packets;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.google.common.collect.*;

import invtweaks.config.*;
import invtweaks.util.*;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.network.*;
import net.minecraftforge.fml.network.*;

public class PacketSortInv {
	private boolean isPlayer;
	
	public PacketSortInv(boolean isPlayer) { this.isPlayer = isPlayer; }
	public PacketSortInv(PacketBuffer buf) {
		this(buf.readBoolean());
	}
	
	private void playerInvHelper(
			String catName,
			InvTweaksConfig.Ruleset rules,
			Map<String, List<ItemStack>> stacksByCat,
			IntList lockedSlots,
			PlayerInventory inv
			) {
		IntList lst = rules.catToInventorySlots(catName);
		if (lst != null) {
			List<ItemStack> queue = stacksByCat.get(catName);
			for (int idx: lst) {
				if (Collections.binarySearch(lockedSlots, idx) >= 0) {
					continue;
				}
				if (queue.isEmpty()) {
					break;
				}
				if (inv.mainInventory.get(idx).isEmpty()) {
					inv.mainInventory.set(idx, queue.remove(0));
				}
			}
		}
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			if (isPlayer) {
				Map<String, InvTweaksConfig.Category> cats = InvTweaksConfig.getPlayerCats(ctx.get().getSender());
				InvTweaksConfig.Ruleset rules = InvTweaksConfig.getPlayerRules(ctx.get().getSender());
				IntList lockedSlots = Optional.ofNullable(rules.catToInventorySlots("/LOCKED"))
						.orElseGet(IntArrayList::new);
				lockedSlots.addAll(Optional.ofNullable(rules.catToInventorySlots("/FROZEN"))
						.orElseGet(IntArrayList::new));
				lockedSlots.sort(null);
				
				PlayerInventory inv = ctx.get().getSender().inventory;
				
				List<ItemStack> stacks = Utils.condensed(() -> IntStream.range(0, inv.mainInventory.size())
						.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
						.mapToObj(idx -> inv.mainInventory.get(idx))
						.filter(st -> !st.isEmpty())
						.iterator());
				stacks.sort(Utils.FALLBACK_COMPARATOR);
				
				Map<String, List<ItemStack>> stacksByCat = stacks.stream()
						.filter(st -> !st.isEmpty())
						.collect(Collectors.groupingBy(st -> {
							for (Map.Entry<String, InvTweaksConfig.Category> ent: cats.entrySet()) {
								if (ent.getValue().checkStack(st) >= 0) {
									return ent.getKey();
								}
							}
							return "/OTHER";
						}));
				stacksByCat.forEach((k,v) -> {
					if (!k.equals("/OTHER")) {
						v.sort(Comparator.comparingInt(s -> cats.get(k).checkStack(s)));
					}
				});
				
				for (int i=0; i<inv.mainInventory.size(); ++i) {
					if (Collections.binarySearch(lockedSlots, i) < 0) {
						inv.mainInventory.set(i, ItemStack.EMPTY);
					}
				}
				
				// deal with the fixed categories first
				for (String k: cats.keySet()) {
					if (stacksByCat.containsKey(k)) {
						playerInvHelper(k, rules, stacksByCat, lockedSlots, inv);
					}
				}
				
				List<ItemStack> remaining = stacksByCat.values().stream()
						.flatMap(List::stream)
						.collect(Collectors.toList());
				remaining.sort(Utils.FALLBACK_COMPARATOR);
				
				Iterable<Integer> toIter = () -> Stream.concat(
						Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
						.flatMap(List::stream),
						rules.fallbackInventoryRules().stream()
						).iterator();
				for (int idx: toIter) {
					if (Collections.binarySearch(lockedSlots, idx) >= 0) {
						continue;
					}
					if (remaining.isEmpty()) {
						break;
					}
					if (inv.mainInventory.get(idx).isEmpty()) {
						inv.mainInventory.set(idx, remaining.remove(0));
					}
				}
			} else {
				Container cont = ctx.get().getSender().openContainer;
				// check if an inventory is open
				if (cont != ctx.get().getSender().container) {
					Iterable<Slot> validSlots = () -> cont.inventorySlots.stream()
							.filter(slot -> !(slot.inventory instanceof PlayerInventory))
							.filter(slot -> slot.canTakeStack(ctx.get().getSender()))
							.iterator();
					if (!validSlots.iterator().hasNext()) return;
					List<ItemStack> stacks = Utils.condensed(() -> Streams.stream(validSlots)
							.map(slot -> slot.getStack())
							.filter(st -> !st.isEmpty())
							.iterator());
					stacks.sort(Utils.FALLBACK_COMPARATOR);
					
					Iterator<Slot> slotIt = validSlots.iterator();
					for (int i=0; i<stacks.size(); ++i) {
						while (slotIt.hasNext()
								&& !slotIt.next().isItemValid(stacks.get(i))) {
							// do nothing
						}
						if (!slotIt.hasNext()) {
							return; // nope right out of the sort
						}
					}
					
					// sort can be done, execute it
					validSlots.forEach(slot -> slot.putStack(ItemStack.EMPTY));
					slotIt = validSlots.iterator();
					for (int i=0; i<stacks.size(); ++i) {
						//System.out.println(i);
						Slot cur = null;
						while (slotIt.hasNext()
								&& !(cur = slotIt.next()).isItemValid(stacks.get(i))) {
							// do nothing
						}
						cur.putStack(stacks.get(i));
					}
				}
			}
		});
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeBoolean(isPlayer);
	}
}
