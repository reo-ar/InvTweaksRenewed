package invtweaks.util;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.apache.commons.lang3.tuple.*;

import com.google.common.base.Equivalence;
import com.google.common.collect.*;
import com.google.common.collect.Streams;

import invtweaks.config.*;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.fml.*;
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
			
			if (player.world.isRemote) {
				DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
					PlayerController pc = Minecraft.getInstance().playerController;
					Int2ObjectMap<Slot> indexToSlot = player.openContainer.inventorySlots
							.stream()
							.filter(slot -> slot.inventory instanceof PlayerInventory)
							.filter(slot -> 0 <= slot.getSlotIndex() && slot.getSlotIndex() < 36)
							.collect(Collectors.toMap(Slot::getSlotIndex, Function.identity(), (u, v) -> u, Int2ObjectOpenHashMap::new));
					
					IntList stackIdxs = IntStream.range(0, inv.mainInventory.size())
							.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
							.filter(idx -> !inv.mainInventory.get(idx).isEmpty())
							.collect(IntArrayList::new, IntList::add, IntList::addAll);
					Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots = Utils.gatheredSlots(() ->
						stackIdxs.stream()
						.mapToInt(v -> v)
						.mapToObj(indexToSlot::get)
						.filter(Slot::getHasStack).iterator());
					List<Equivalence.Wrapper<ItemStack>> stackWs = new ArrayList<>(gatheredSlots.keySet());
					stackWs.sort(Comparator.comparing(w -> w.get(), Utils.FALLBACK_COMPARATOR));
					
					//System.out.println("SZ: "+gatheredSlots.size());
					for (Map.Entry<String, InvTweaksConfig.Category> ent: cats.entrySet()) {
						//System.out.println(gatheredSlots.values().stream().flatMap(s -> s.stream()).count());
						
						IntList specificRules = rules.catToInventorySlots(ent.getKey());
						if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
						specificRules = specificRules.stream()
								.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
								.mapToInt(v -> v)
								.collect(IntArrayList::new, IntList::add, IntList::addAll);
						//System.out.println(ent.getKey());
						//System.out.println(specificRules);
						List<Slot> specificRulesSlots = Lists.transform(specificRules, idx -> {
							return indexToSlot.get((int)idx);
						});
						ListIterator<Slot> toIt = specificRulesSlots.listIterator();
						
						Client.processCategoryClient(player, pc, gatheredSlots, stackWs, ent.getValue(), toIt);
					}
					
					List<Slot> fallbackList = Stream.concat(
						Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
						.flatMap(List::stream),
						rules.fallbackInventoryRules().stream()
					).mapToInt(v -> v)
							.filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
							.distinct()
							.mapToObj(indexToSlot::get)
							.collect(Collectors.toList());
					//System.out.println(Arrays.toString(fallbackList.stream().mapToInt(slot -> slot.getSlotIndex()).toArray()));
					Client.processCategoryClient(player, pc, gatheredSlots, stackWs, null, fallbackList.listIterator());
				});
			} else {
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
				
				PrimitiveIterator.OfInt fallbackIt = Stream.concat(
					Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
					.flatMap(List::stream),
					rules.fallbackInventoryRules().stream()
					).mapToInt(v -> v).iterator();
				while (fallbackIt.hasNext()) {
					int idx = fallbackIt.nextInt();
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
					DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
						PlayerController pc = Minecraft.getInstance().playerController;
						Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots = Utils.gatheredSlots(() ->
							validSlots.stream().filter(Slot::getHasStack).iterator());
						List<Equivalence.Wrapper<ItemStack>> stackWs = new ArrayList<>(gatheredSlots.keySet());
						stackWs.sort(Comparator.comparing(w -> w.get(), Utils.FALLBACK_COMPARATOR));
						
						ListIterator<Slot> toIt = validSlots.listIterator();
						for (Equivalence.Wrapper<ItemStack> stackW: stackWs) {
							BiMap<Slot, Slot> displaced = HashBiMap.create();
							Client.clientPushToSlots(player, pc, gatheredSlots.get(stackW).iterator(), toIt, displaced);
							for (Map.Entry<Slot, Slot> displacedPair: displaced.entrySet()) {
								Set<Slot> toModify = gatheredSlots.get(Utils.STACKABLE.wrap(displacedPair.getValue().getStack()));
								toModify.remove(displacedPair.getKey());
								toModify.add(displacedPair.getValue());
							}
							toIt.next();
						}
					});
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
	
	/**
	 * This prevents the functions below from accidentally being loaded on the server.
	 */
	static class Client {

		static void processCategoryClient(PlayerEntity player, PlayerController pc,
				Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots, List<Equivalence.Wrapper<ItemStack>> stackWs,
				InvTweaksConfig.Category cat, ListIterator<Slot> toIt) {
			List<Equivalence.Wrapper<ItemStack>> subStackWs = cat == null 
					? new ArrayList<>(stackWs) : stackWs.stream()
			.filter(stackW -> cat.checkStack(stackW.get()) >= 0)
			.sorted(Comparator.comparingInt(stackW -> cat.checkStack(stackW.get())))
			.collect(Collectors.toList());
			
			Iterator<Equivalence.Wrapper<ItemStack>> sswIt = subStackWs.iterator();
			while (sswIt.hasNext()) {
				Equivalence.Wrapper<ItemStack> stackW = sswIt.next();
				if (cat == null || cat.checkStack(stackW.get()) >= 0) {
					BiMap<Slot, Slot> displaced = HashBiMap.create();
					ListIterator<Slot> fromIt = (ListIterator<Slot>)gatheredSlots.get(stackW).iterator();
					boolean fullInserted = Client.clientPushToSlots(player, pc, fromIt, toIt, displaced);
					for (Map.Entry<Slot, Slot> displacedPair: displaced.entrySet()) {
						Equivalence.Wrapper<ItemStack> displacedW = Utils.STACKABLE.wrap(displacedPair.getValue().getStack());
						Set<Slot> toModify = gatheredSlots.get(displacedW);
						toModify.remove(displacedPair.getKey());
						toModify.add(displacedPair.getValue());
					}
					//System.out.println(gatheredSlots.get(stackW).size());
					//System.out.println("HAS_PREV: "+fromIt.hasPrevious());
					if (!fullInserted) fromIt.previous();
					while (fromIt.hasPrevious()) {
						fromIt.previous(); fromIt.remove();
					}
					//System.out.println(gatheredSlots.get(stackW).size());
					if (!toIt.hasNext()) break;
					toIt.next();
				}
			}
			stackWs.removeIf(sw -> gatheredSlots.get(sw).isEmpty());
			gatheredSlots.values().removeIf(set -> set.isEmpty());
		}

		/**
		 * 
		 * Transfers the items from a specified sequence of slots to a specified
		 * sequence of slots, possibly displacing existing items.
		 * 
		 * @param ent
		 * @param pc
		 * @param from 
		 * @param to
		 * @param displaced mapping from slot formerly containing existing item -> new slot of existing item
		 * 
		 * @return whether the item preceding fromIt has been fully pushed
		 * 
		 */
		static boolean clientPushToSlots(PlayerEntity player, PlayerController pc, Iterator<Slot> fromIt, ListIterator<Slot> to, BiMap<Slot, Slot> displaced) {
			if (!to.hasNext()) return true;
			boolean didCompleteCurrent = true;
			while (fromIt.hasNext()) {
				didCompleteCurrent = false;
				Slot slot = fromIt.next();
				pc.windowClick(player.openContainer.windowId,
						slot.slotNumber, 0, ClickType.PICKUP, player);
				Slot toSlot = null;
				while (to.hasNext()) {
					toSlot = to.next();
					pc.windowClick(player.openContainer.windowId,
							toSlot.slotNumber, 0, ClickType.PICKUP, player);
					if (player.inventory.getItemStack().isEmpty()) {
						didCompleteCurrent = true;
						break;
					}
					pc.windowClick(player.openContainer.windowId,
							slot.slotNumber, 0, ClickType.PICKUP, player);
					if (slot.getHasStack() && !ItemHandlerHelper.canItemStacksStack(slot.getStack(), toSlot.getStack())) {
						didCompleteCurrent = true;
						displaced.put(toSlot, slot);
						break;
					}
				}
				if (!to.hasNext() && Optional.ofNullable(toSlot)
						.filter(s -> s.getStack().getCount() >= Math.min(s.getSlotStackLimit(), s.getStack().getMaxStackSize()))
						.isPresent()) {
					break;
				}
				to.previous();
			}
			return didCompleteCurrent;
		}
		
	}
}
