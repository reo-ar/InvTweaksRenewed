package invtweaks.util;

import java.util.*;
import java.util.stream.*;

import org.apache.commons.lang3.tuple.*;

import com.google.common.base.Equivalence;
import com.google.common.collect.Streams;

import invtweaks.config.*;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraftforge.items.*;

public class Sorting {
	public static void executeSort(PlayerEntity player, boolean isPlayerSort) {
		if (isPlayerSort) {
			Map<String, InvTweaksConfig.Category> cats = InvTweaksConfig.getPlayerCats(player);
			InvTweaksConfig.Ruleset rules = InvTweaksConfig.getPlayerRules(player);
			IntList lockedSlots = Optional.ofNullable(rules.catToInventorySlots("/LOCKED"))
					.<IntList>map(IntArrayList::new) // copy list to prevent modification
					.orElseGet(IntArrayList::new);
			lockedSlots.addAll(Optional.ofNullable(rules.catToInventorySlots("/FROZEN"))
					.orElse(IntLists.EMPTY_LIST));
			lockedSlots.sort(null);
			
			PlayerInventory inv = player.inventory;
			
			List<ItemStack> stacks = Utils.condensed(() -> IntStream.range(0, inv.mainInventory.size())
					.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
					.mapToObj(idx -> inv.mainInventory.get(idx))
					.filter(st -> !st.isEmpty())
					.iterator());
			stacks.sort(Utils.FALLBACK_COMPARATOR);
			stacks = new LinkedList<>(stacks);
			
			for (int i=0; i<inv.mainInventory.size(); ++i) {
				if (Collections.binarySearch(lockedSlots, i) < 0) {
					inv.mainInventory.set(i, ItemStack.EMPTY);
				}
			}
			
			for (Map.Entry<String, InvTweaksConfig.Category> ent: cats.entrySet()) {
				IntList specificRules = rules.catToInventorySlots(ent.getKey());
				if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
				specificRules = specificRules.stream()
						.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
						.mapToInt(v -> v)
						.collect(IntArrayList::new, IntList::add, IntList::addAll);
				List<ItemStack> curStacks = new ArrayList<>();
				Iterator<ItemStack> it = stacks.iterator();
				while (it.hasNext() && curStacks.size() < specificRules.size()) {
					ItemStack st = it.next();
					if (ent.getValue().checkStack(st) >= 0) {
						curStacks.add(st);
						it.remove();
					}
				}
				curStacks.sort(Comparator.comparingInt(s -> cats.get(ent.getKey()).checkStack(s)));
				Streams.zip(specificRules.stream(), curStacks.stream(), Pair::of)
				.forEach(pr -> {
					inv.mainInventory.set(pr.getKey(), pr.getValue());
				});
			}
			
			Iterable<Integer> toIter = () -> Stream.concat(
					Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
					.flatMap(List::stream),
					rules.fallbackInventoryRules().stream()
					).iterator();
			for (int idx: toIter) {
				if (Collections.binarySearch(lockedSlots, idx) >= 0) {
					continue;
				}
				if (stacks.isEmpty()) {
					break;
				}
				if (inv.mainInventory.get(idx).isEmpty()) {
					inv.mainInventory.set(idx, stacks.remove(0));
				}
			}
			//ctx.get().getSender().openContainer.detectAndSendChanges();
		} else {
			Container cont = player.openContainer;
			
			// check if an inventory is open
			if (cont != player.container) {
				String contClass = cont.getClass().getName();
				InvTweaksConfig.ContOverride override = InvTweaksConfig.getPlayerContOverrides(player).get(contClass);
				
				if (override != null && override.isSortDisabled()) return;
				
				List<Slot> validSlots = (override != null && override.getSortRange() != null
						? override.getSortRange().stream()
								.filter(idx -> 0 <= idx && idx < cont.inventorySlots.size())
								.map(cont.inventorySlots::get)
						: cont.inventorySlots.stream())
						.filter(slot -> !(slot.inventory instanceof PlayerInventory))
						.filter(slot -> {
							return (slot.canTakeStack(player) && 
									slot.isItemValid(slot.getStack())) || !slot.getHasStack();
						})
						.collect(Collectors.toList());
				
				if (player.world.isRemote) {
					PlayerController pc = Minecraft.getInstance().playerController;
					Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots = Utils.gatheredSlots(() ->
						validSlots.stream().filter(Slot::getHasStack).iterator());
					List<Equivalence.Wrapper<ItemStack>> stackWs = new ArrayList<>(gatheredSlots.keySet());
					stackWs.sort(Comparator.comparing(w -> w.get(), Utils.FALLBACK_COMPARATOR));
					
					int slotI = 0;
					for (Equivalence.Wrapper<ItemStack> stackW: stackWs) {
						Iterator<Slot> it = gatheredSlots.get(stackW).iterator();
						while (it.hasNext()) {
							Slot slot = it.next();
							pc.windowClick(player.openContainer.windowId,
									slot.slotNumber, 0, ClickType.PICKUP, player);
							for ( ; slotI < validSlots.size(); ++slotI) {
								pc.windowClick(player.openContainer.windowId,
										validSlots.get(slotI).slotNumber, 0, ClickType.PICKUP, player);
								if (player.inventory.getItemStack().isEmpty()) {
									break;
								}
								pc.windowClick(player.openContainer.windowId,
										slot.slotNumber, 0, ClickType.PICKUP, player);
								//System.out.println(slot.getStack()+", "+stackW.get());
								if (slot.getHasStack() && !ItemHandlerHelper.canItemStacksStack(slot.getStack(), stackW.get())) {
									Set<Slot> toModify = gatheredSlots.get(Utils.STACKABLE.wrap(slot.getStack()));
									toModify.remove(validSlots.get(slotI));
									toModify.add(slot);
									break;
								}
							}
						}
						++slotI;
					}
				} else {
					if (!validSlots.iterator().hasNext()) return;
					List<ItemStack> stacks = Utils.condensed(() -> validSlots.stream()
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
					
					// execute sort
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
		}
	}
}
