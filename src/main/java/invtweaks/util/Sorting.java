package invtweaks.util;

import com.google.common.base.Equivalence;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Streams;
import invtweaks.config.InvTweaksConfig;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerController;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Sorting {
    public static void executeSort(PlayerEntity player, boolean isPlayerSort) {
        if (isPlayerSort) {
            Map<String, InvTweaksConfig.Category> cats = InvTweaksConfig.getPlayerCats(player);
            InvTweaksConfig.Ruleset rules = InvTweaksConfig.getPlayerRules(player);
            IntList lockedSlots =
                    Optional.ofNullable(rules.catToInventorySlots("/LOCKED"))
                            .<IntList>map(IntArrayList::new) // copy list to prevent modification
                            .orElseGet(IntArrayList::new);
            lockedSlots.addAll(
                    Optional.ofNullable(rules.catToInventorySlots("/FROZEN")).orElse(IntLists.EMPTY_LIST));
            lockedSlots.sort(null);

            PlayerInventory inv = player.inventory;

            if (player.world.isRemote) {
                DistExecutor.unsafeRunWhenOn(
                        Dist.CLIENT,
                        () ->
                                () -> {
                                    PlayerController pc = Minecraft.getInstance().playerController;
                                    Int2ObjectMap<Slot> indexToSlot =
                                            player.openContainer.inventorySlots.stream()
                                                    .filter(slot -> slot.inventory instanceof PlayerInventory)
                                                    .filter(slot -> 0 <= slot.getSlotIndex() && slot.getSlotIndex() < 36)
                                                    .collect(
                                                            Collectors.toMap(
                                                                    Slot::getSlotIndex,
                                                                    Function.identity(),
                                                                    (u, v) -> u,
                                                                    Int2ObjectOpenHashMap::new));

                                    IntList stackIdxs =
                                            IntStream.range(0, inv.mainInventory.size())
                                                    .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                                                    .filter(idx -> !inv.mainInventory.get(idx).isEmpty())
                                                    .collect(IntArrayList::new, IntList::add, IntList::addAll);
                                    Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots =
                                            Utils.gatheredSlots(
                                                    () ->
                                                            stackIdxs.stream()
                                                                    .mapToInt(v -> v)
                                                                    .mapToObj(indexToSlot::get)
                                                                    .filter(Slot::getHasStack)
                                                                    .iterator());
                                    List<Equivalence.Wrapper<ItemStack>> stackWs =
                                            new ArrayList<>(gatheredSlots.keySet());
                                    stackWs.sort(
                                            Comparator.comparing(Equivalence.Wrapper::get, Utils.FALLBACK_COMPARATOR));

                                    // System.out.println("SZ: "+gatheredSlots.size());
                                    for (Map.Entry<String, InvTweaksConfig.Category> ent : cats.entrySet()) {
                                        // System.out.println(gatheredSlots.values().stream().flatMap(s ->
                                        // s.stream()).count());

                                        @SuppressWarnings("DuplicatedCode") IntList specificRules = rules.catToInventorySlots(ent.getKey());
                                        if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
                                        specificRules =
                                                specificRules.stream()
                                                        .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                                                        .mapToInt(v -> v)
                                                        .collect(IntArrayList::new, IntList::add, IntList::addAll);
                                        // System.out.println(ent.getKey());
                                        // System.out.println(specificRules);
                                        List<Slot> specificRulesSlots =
                                                specificRules.stream()
                                                        .map(
                                                                idx -> indexToSlot.get((int) idx))
                                                        .collect(Collectors.toList());
                                        ListIterator<Slot> toIt = specificRulesSlots.listIterator();

                                        Client.processCategoryClient(
                                                player, pc, gatheredSlots, stackWs, ent.getValue(), toIt);
                                    }

                                    @SuppressWarnings("UnstableApiUsage") List<Slot> fallbackList =
                                            Stream.concat(
                                                    Streams.stream(
                                                            Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
                                                            .flatMap(List::stream),
                                                    rules.fallbackInventoryRules().stream())
                                                    .mapToInt(v -> v)
                                                    .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                                                    .distinct()
                                                    .mapToObj(indexToSlot::get)
                                                    .collect(Collectors.toList());
                                    // System.out.println(Arrays.toString(fallbackList.stream().mapToInt(slot ->
                                    // slot.getSlotIndex()).toArray()));
                                    Client.processCategoryClient(
                                            player, pc, gatheredSlots, stackWs, null, fallbackList.listIterator());
                                });
            } else {
                List<ItemStack> stacks =
                        Utils.condensed(
                                () ->
                                        IntStream.range(0, inv.mainInventory.size())
                                                .filter(idx -> Collections.binarySearch(lockedSlots, idx) < 0)
                                                .mapToObj(inv.mainInventory::get)
                                                .filter(st -> !st.isEmpty())
                                                .iterator());
                stacks.sort(Utils.FALLBACK_COMPARATOR);
                stacks = new LinkedList<>(stacks);

                for (int i = 0; i < inv.mainInventory.size(); ++i) {
                    if (Collections.binarySearch(lockedSlots, i) < 0) {
                        inv.mainInventory.set(i, ItemStack.EMPTY);
                    }
                }

                for (Map.Entry<String, InvTweaksConfig.Category> ent : cats.entrySet()) {
                    IntList specificRules = rules.catToInventorySlots(ent.getKey());
                    if (specificRules == null) specificRules = IntLists.EMPTY_LIST;
                    specificRules =
                            specificRules.stream()
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
                    //noinspection UnstableApiUsage
                    Streams.zip(specificRules.stream(), curStacks.stream(), Pair::of)
                            .forEach(
                                    pr -> inv.mainInventory.set(pr.getKey(), pr.getValue()));
                }

                @SuppressWarnings("UnstableApiUsage") PrimitiveIterator.OfInt fallbackIt =
                        Stream.concat(
                                Streams.stream(Optional.ofNullable(rules.catToInventorySlots("/OTHER")))
                                        .flatMap(List::stream),
                                rules.fallbackInventoryRules().stream())
                                .mapToInt(v -> v)
                                .iterator();
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
            // ctx.get().getSender().openContainer.detectAndSendChanges();
        } else {
            Container cont = player.openContainer;

            // check if an inventory is open
            if (cont != player.container) {
                String contClass = cont.getClass().getName();
                InvTweaksConfig.ContOverride override =
                        InvTweaksConfig.getPlayerContOverrides(player).get(contClass);

                if (override != null && override.isSortDisabled()) return;

                List<Slot> validSlots =
                        (override != null && override.getSortRange() != null
                                ? override.getSortRange().stream()
                                .filter(Objects::nonNull)
                                .filter(idx -> 0 <= idx && idx < cont.inventorySlots.size())
                                .map(cont.inventorySlots::get)
                                : cont.inventorySlots.stream())
                                .filter(slot -> !(slot.inventory instanceof PlayerInventory))
                                .filter(
                                        slot ->
                                                (slot.canTakeStack(player) && slot.isItemValid(slot.getStack()))
                                                        || !slot.getHasStack())
                                .collect(Collectors.toList());

                if (player.world.isRemote) {
                    DistExecutor.unsafeRunWhenOn(
                            Dist.CLIENT,
                            () ->
                                    () -> {
                                        PlayerController pc = Minecraft.getInstance().playerController;
                                        Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots =
                                                Utils.gatheredSlots(
                                                        () -> validSlots.stream()
                                                                .filter(Slot::getHasStack)
                                                                .iterator());
                                        List<Equivalence.Wrapper<ItemStack>> stackWs =
                                                new ArrayList<>(gatheredSlots.keySet());
                                        stackWs.sort(
                                                Comparator.comparing(Equivalence.Wrapper::get, Utils.FALLBACK_COMPARATOR));

                                        ListIterator<Slot> toIt = validSlots.listIterator();
                                        for (Equivalence.Wrapper<ItemStack> stackW : stackWs) {
                                            BiMap<Slot, Slot> displaced = HashBiMap.create();
                                            Client.clientPushToSlots(
                                                    player, pc, gatheredSlots.get(stackW).iterator(), toIt, displaced);
                                            for (Map.Entry<Slot, Slot> displacedPair : displaced.entrySet()) {
                                                Set<Slot> toModify =
                                                        gatheredSlots.get(
                                                                Utils.STACKABLE.wrap(displacedPair.getValue().getStack()));
                                                toModify.remove(displacedPair.getKey());
                                                toModify.add(displacedPair.getValue());
                                            }
                                        }
                                    });
                } else {
                    if (!validSlots.iterator().hasNext()) return;
                    List<ItemStack> stacks =
                            Utils.condensed(
                                    () ->
                                            validSlots.stream()
                                                    .map(Slot::getStack)
                                                    .filter(st -> !st.isEmpty())
                                                    .iterator());
                    stacks.sort(Utils.FALLBACK_COMPARATOR);

                    Iterator<Slot> slotIt = validSlots.iterator();
                    for (ItemStack stack : stacks) {
                        Slot cur = null;
                        while (slotIt.hasNext() && !(cur = slotIt.next()).isItemValid(stack)) {
                            assert true;
                        }
                        if (cur == null || !cur.isItemValid(stack)) {
                            return; // nope right out of the sort
                        }
                    }

                    // execute sort
                    validSlots.forEach(slot -> slot.putStack(ItemStack.EMPTY));
                    slotIt = validSlots.iterator();
                    for (ItemStack stack : stacks) {
                        // System.out.println(i);
                        Slot cur = null;
                        while (slotIt.hasNext() && !(cur = slotIt.next()).isItemValid(stack)) {
                            assert true;
                        }
                        assert cur != null;
                        cur.putStack(stack);
                    }
                }
            }
        }
    }

    /**
     * This prevents the functions below from accidentally being loaded on the server.
     */
    static class Client {

        static void processCategoryClient(
                PlayerEntity player,
                PlayerController pc,
                Map<Equivalence.Wrapper<ItemStack>, Set<Slot>> gatheredSlots,
                List<Equivalence.Wrapper<ItemStack>> stackWs,
                InvTweaksConfig.Category cat,
                ListIterator<Slot> toIt) {
            List<Equivalence.Wrapper<ItemStack>> subStackWs =
                    cat == null
                            ? new ArrayList<>(stackWs)
                            : stackWs.stream()
                            .filter(stackW -> cat.checkStack(stackW.get()) >= 0)
                            .sorted(Comparator.comparingInt(stackW -> cat.checkStack(stackW.get())))
                            .collect(Collectors.toList());

            for (Equivalence.Wrapper<ItemStack> stackW : subStackWs) {
                if (cat == null || cat.checkStack(stackW.get()) >= 0) {
                    BiMap<Slot, Slot> displaced = HashBiMap.create();
                    ListIterator<Slot> fromIt = (ListIterator<Slot>) gatheredSlots.get(stackW).iterator();
                    @SuppressWarnings("unused") boolean fullInserted = Client.clientPushToSlots(player, pc, fromIt, toIt, displaced);
                    for (Map.Entry<Slot, Slot> displacedPair : displaced.entrySet()) {
                        Equivalence.Wrapper<ItemStack> displacedW =
                                Utils.STACKABLE.wrap(displacedPair.getValue().getStack());
                        Set<Slot> toModify = gatheredSlots.get(displacedW);
                        toModify.remove(displacedPair.getKey());
                        toModify.add(displacedPair.getValue());
                    }
                    // System.out.println(gatheredSlots.get(stackW).size());
                    // System.out.println("HAS_PREV: "+fromIt.hasPrevious());
                    // System.out.println(gatheredSlots.get(stackW).size());
                }
            }
            stackWs.removeIf(sw -> gatheredSlots.get(sw).isEmpty());
            gatheredSlots.values().removeIf(Set::isEmpty);
        }

        /**
         * Transfers the items from a specified sequence of slots to a specified
         * sequence of slots, possibly displacing existing items.
         *
         * @param player           The player that is interacting with the sort
         * @param playerController Controller so clicks can be sent to move items
         * @param OriginIter       The Slots from which the ItemStacks will be moved
         * @param destinationIter  The Slots to which the ItemStacks will be moved
         * @param displaced        BiMap to keep track of what Slots had their items
         *                         swapped to make space for the items that needed to
         *                         be moved.
         * @return whether all items in OriginIter have been fully pushed
         */
        static boolean clientPushToSlots(PlayerEntity player, PlayerController playerController, Iterator<Slot> OriginIter, ListIterator<Slot> destinationIter, BiMap<Slot, Slot> displaced) {
            // There are no more spaces in the destination container to put items
            if (!destinationIter.hasNext())
                return true;

            boolean completedCurrentItemSwap = true;

            // Grab more items from the to-move list.
            while (OriginIter.hasNext()) {
                // Starting new iteration -> not done with this item
                completedCurrentItemSwap = false;

                // Where is the item coming from
                Slot originSlot = OriginIter.next();
                // Pick up the origin item
                playerController.windowClick(player.openContainer.windowId, originSlot.slotNumber, 0, ClickType.PICKUP, player);

                // Find next open slot in the container
                Slot destinationSlot = null;
                while (destinationIter.hasNext()) {
                    // Check previous stack; If can put this item there, then do
                    if (destinationIter.hasPrevious()) {
                        destinationSlot = destinationIter.previous();

                        // If the stack is not at max capacity AND can stack with the one that is held right now
                        if (destinationSlot.getStack().getCount() != Math.max(destinationSlot.getSlotStackLimit(), destinationSlot.getStack().getMaxStackSize())
                                && Utils.STACKABLE.equivalent(destinationSlot.getStack(), player.inventory.getItemStack())) {
                            // Stay on this current 'previous' slot (by doing nothing).
                            assert true;
                        }

                        // Other wise advance back to where we should be.
                        else
                            destinationIter.next();
                    }

                    // Where the held item will be going
                    destinationSlot = destinationIter.next();

                    // Place held item (from origin) in destination slot,
                    // picking up whatever was at destination, if it had anything.
                    // or adding to that stack, if we backed up because it was the same item.
                    // (possibly filling the stack and getting leftover ItemStack)
                    playerController.windowClick(player.openContainer.windowId, destinationSlot.slotNumber, 0, ClickType.PICKUP, player);

                    // Didnt pick anything up -> done
                    if (player.inventory.getItemStack().isEmpty()) {
                        completedCurrentItemSwap = true;
                        break;
                    }

                    // Did pick something else up / have leftover item from topping off the stack
                    else {
                        // If its overflow from the current item, no need to swap it back to the starting position,
                        // just try the next slot.
                        if (Utils.STACKABLE.equivalent(destinationSlot.getStack(), player.inventory.getItemStack()))
                            continue;

                        // Else, this stack was picked up, and is being displaced...
                        // Click to put this item into the origin slot, which is guaranteed to be free
                        playerController.windowClick(player.openContainer.windowId, originSlot.slotNumber, 0, ClickType.PICKUP, player);

                        if (originSlot.getHasStack() && !ItemHandlerHelper.canItemStacksStack(originSlot.getStack(), destinationSlot.getStack())) {
                            // This iteration is now complete.
                            completedCurrentItemSwap = true;
                            // Remember that the item that was in destination is now moved to origin...
                            displaced.put(destinationSlot, originSlot);
                            break;
                        }
                    }
                }
                if (!destinationIter.hasNext() && Optional.ofNullable(destinationSlot).filter(s -> s.getStack().getCount() >= Math.min(s.getSlotStackLimit(), s.getStack().getMaxStackSize())).isPresent()) {
                    break;
                }
            }
            return completedCurrentItemSwap;
        }
    }
}
