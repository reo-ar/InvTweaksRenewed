package invtweaks;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import invtweaks.config.InvTweaksConfig;
import invtweaks.gui.InvTweaksButtonSort;
import invtweaks.packets.PacketSortInv;
import invtweaks.packets.PacketUpdateConfig;
import invtweaks.util.ClientUtils;
import invtweaks.util.Sorting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.DisplayEffectsScreen;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.CreativeScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.HandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.IntStream;

// The value here should match an entry in the META-INF/mods.toml file
@SuppressWarnings("unused")
@Mod(InvTweaksMod.MODID)
public class InvTweaksMod {
    public static final String MODID = "invtweaks";
    // Directly reference a log4j logger.
    public static final Logger LOGGER =
            LogManager.getLogger(InvTweaksMod.MODID.toUpperCase(Locale.US));
    public static final String CHANNEL = "channel";

    public static final String NET_VERS = "2";
    public static final int MIN_SLOTS = 9;
    private static final Object clientOnlyL = new Object();
    private static final Field guiLeftF =
            DistExecutor.unsafeCallWhenOn(
                    Dist.CLIENT,
                    () ->
                            () -> ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147003_i"));
    private static final Field guiTopF =
            DistExecutor.unsafeCallWhenOn(
                    Dist.CLIENT,
                    () ->
                            () -> ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147009_r"));
    private static final Set<Screen> screensWithExtSort =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Method renderHotbarItemM =
            DistExecutor.unsafeCallWhenOn(
                    Dist.CLIENT,
                    () ->
                            () ->
                                    ObfuscationReflectionHelper.findMethod(
                                            IngameGui.class,
                                            "func_184044_a",
                                            int.class,
                                            int.class,
                                            float.class,
                                            PlayerEntity.class,
                                            ItemStack.class));
    public static SimpleChannel NET_INST;
    @Nullable
    private static MinecraftServer server;
    private static BooleanSupplier isJEIKeyboardActive = () -> false;
    private static boolean clientOnly = true;
    private static Map<String, KeyBinding> keyBindings;
    private final Map<PlayerEntity, EnumMap<Hand, Item>> itemsCache = new WeakHashMap<>();
    private final Map<PlayerEntity, Object2IntMap<Item>> usedCache = new WeakHashMap<>();

    public InvTweaksMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, InvTweaksConfig.CLIENT_CONFIG);

        // Register the setup method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        // Register the enqueueIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
        // Register the processIMC method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        InvTweaksConfig.loadConfig(
                InvTweaksConfig.CLIENT_CONFIG, FMLPaths.CONFIGDIR.get().resolve("invtweaks-client.toml"));
    }

    public static void setJEIKeyboardActiveFn(BooleanSupplier query) {
        isJEIKeyboardActive = query;
    }

    public static boolean isJEIKeyboardActive() {
        return isJEIKeyboardActive.getAsBoolean();
    }

    public static boolean clientOnly() {
        synchronized (clientOnlyL) {
            // System.out.println(clientOnly);
            return clientOnly;
        }
    }

    public static void requestSort(boolean isPlayer) {
        if (clientOnly()) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT, () -> () -> Sorting.executeSort(ClientUtils.safeGetPlayer(), isPlayer));
        } else {
            NET_INST.sendToServer(new PacketSortInv(isPlayer));
        }
    }

    public static @Nullable
    Slot getDefaultButtonPlacement(
            Collection<Slot> slots, Predicate<Slot> filter) {
        if (slots.stream().filter(filter).count() < MIN_SLOTS) {
            return null;
        }
        // pick the rightmost slot first, then the topmost in case of a tie
        // TODO change button position algorithm?
        return slots.stream()
                .filter(filter)
                .max(Comparator.<Slot>comparingInt(s -> s.xPos).thenComparingInt(s -> -s.yPos))
                .orElse(null);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // if client doesn't have mod installed, that's fine
        NET_INST =
                NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(MODID, CHANNEL),
                        () -> NET_VERS,
                        s -> {
                            synchronized (clientOnlyL) {
                                clientOnly =
                                        NetworkRegistry.ABSENT.equals(s) || NetworkRegistry.ACCEPTVANILLA.equals(s);
                                return NET_VERS.equals(s) || clientOnly;
                            }
                        },
                        s ->
                                NET_VERS.equals(s)
                                        || NetworkRegistry.ABSENT.equals(s)
                                        || NetworkRegistry.ACCEPTVANILLA.equals(s));
        NET_INST.registerMessage(
                0, PacketSortInv.class, PacketSortInv::encode, PacketSortInv::new, PacketSortInv::handle);
        NET_INST.registerMessage(
                1,
                PacketUpdateConfig.class,
                PacketUpdateConfig::encode,
                PacketUpdateConfig::new,
                PacketUpdateConfig::handle);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        keyBindings =
                ImmutableMap.<String, KeyBinding>builder()
                        .put(
                                "sort_player",
                                new KeyBinding(
                                        "key.invtweaks_sort_player.desc",
                                        KeyConflictContext.GUI,
                                        InputMappings.Type.KEYSYM,
                                        GLFW.GLFW_KEY_BACKSLASH,
                                        "key.categories.invtweaks"))
                        .put(
                                "sort_inventory",
                                new KeyBinding(
                                        "key.invtweaks_sort_inventory.desc",
                                        KeyConflictContext.GUI,
                                        InputMappings.Type.KEYSYM,
                                        GLFW.GLFW_KEY_GRAVE_ACCENT,
                                        "key.categories.invtweaks"))
                        .put(
                                "sort_either",
                                new KeyBinding(
                                        "key.invtweaks_sort_either.desc",
                                        KeyConflictContext.GUI,
                                        InputMappings.Type.MOUSE,
                                        GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                                        "key.categories.invtweaks"))
                        .build();
        for (KeyBinding kb : keyBindings.values()) ClientRegistry.registerKeyBinding(kb);
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {
        // some example code to dispatch IMC to another mod
        // InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the
        // MDK"); return "Hello world";});
    }

    private void processIMC(final InterModProcessEvent event) {
        // some example code to receive and process InterModComms from other mods
    /*
    LOGGER.info("Got IMC {}", event.getIMCStream().
            map(m->m.getMessageSupplier().get()).
            collect(Collectors.toList()));*/
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopped(FMLServerStoppedEvent event) {
        server = null;
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        // LOGGER.log(Level.INFO, event.getGui().getClass());
        if (event.getGui() instanceof ContainerScreen && !(event.getGui() instanceof CreativeScreen)) {
            // first, work with player inventory
            Slot placement =
                    getDefaultButtonPlacement(
                            ((ContainerScreen<?>) event.getGui()).getContainer().inventorySlots,
                            slot -> slot.inventory instanceof PlayerInventory);
            if (placement != null
                    && InvTweaksConfig.isSortEnabled(true)
                    && InvTweaksConfig.isButtonEnabled(true)) {
                try {
                    event.addWidget(
                            new InvTweaksButtonSort(
                                    guiLeftF.getInt(event.getGui()) + placement.xPos + 17,
                                    guiTopF.getInt(event.getGui()) + placement.yPos,
                                    true));
                } catch (Exception e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            }

            // then, work with external inventory
            String contClass =
                    event.getGui() != null
                            ? ((ContainerScreen<?>) event.getGui()).getContainer().getClass().getName()
                            : "";
            InvTweaksConfig.ContOverride override =
                    InvTweaksConfig.getSelfCompiledContOverrides().get(contClass);

            if (!(event.getGui() instanceof DisplayEffectsScreen)
                    && !Optional.ofNullable(override)
                    .filter(InvTweaksConfig.ContOverride::isSortDisabled)
                    .isPresent()) {
                int x = InvTweaksConfig.NO_POS_OVERRIDE, y = InvTweaksConfig.NO_POS_OVERRIDE;
                if (override != null) {
                    x = override.getX();
                    y = override.getY();
                }
                placement =
                        getDefaultButtonPlacement(
                                ((ContainerScreen<?>) event.getGui()).getContainer().inventorySlots,
                                slot ->
                                        !(slot.inventory instanceof PlayerInventory
                                                || slot.inventory instanceof CraftingInventory));
                if (placement != null) {
                    if (x == InvTweaksConfig.NO_POS_OVERRIDE) {
                        x = placement.xPos + 17;
                    }
                    if (y == InvTweaksConfig.NO_POS_OVERRIDE) {
                        y = placement.yPos;
                    }
                }
                // System.out.println(x+ " " +y);
                if (InvTweaksConfig.isSortEnabled(false)) {
                    try {
                        if (InvTweaksConfig.isButtonEnabled(false)) {
                            event.addWidget(
                                    new InvTweaksButtonSort(
                                            guiLeftF.getInt(event.getGui()) + x,
                                            guiTopF.getInt(event.getGui()) + y,
                                            false));
                        }
                        screensWithExtSort.add(event.getGui());
                    } catch (Exception e) {
                        Throwables.throwIfUnchecked(e);
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        // event.addWidget(button);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.side == LogicalSide.SERVER) {
            if (!InvTweaksConfig.getPlayerAutoRefill(event.player)) {
                return;
            }
            EnumMap<Hand, Item> cached =
                    itemsCache.computeIfAbsent(event.player, k -> new EnumMap<>(Hand.class));
            Object2IntMap<Item> ucached =
                    usedCache.computeIfAbsent(event.player, k -> new Object2IntOpenHashMap<>());
            for (Hand hand : Hand.values()) {
                if (cached.get(hand) != null
                        && event.player.getHeldItem(hand).isEmpty()
                        && ((ServerPlayerEntity) event.player)
                        .getStats()
                        .getValue(Stats.ITEM_USED.get(cached.get(hand)))
                        > ucached.getOrDefault(cached.get(hand), Integer.MAX_VALUE)) {
                    // System.out.println("Item depleted");
                    searchForSubstitute(event.player, hand, cached.get(hand));
                }
                ItemStack held = event.player.getHeldItem(hand);
                cached.put(hand, held.isEmpty() ? null : held.getItem());
                if (!held.isEmpty()) {
                    ucached.put(
                            held.getItem(),
                            ((ServerPlayerEntity) event.player)
                                    .getStats()
                                    .getValue(Stats.ITEM_USED.get(held.getItem())));
                }
            }
        } else {
            if (InvTweaksConfig.isDirty()) {
                if (!clientOnly()) NET_INST.sendToServer(InvTweaksConfig.getSyncPacket());
                InvTweaksConfig.setDirty(false);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            DistExecutor.unsafeRunWhenOn(
                    Dist.CLIENT,
                    () ->
                            () -> {
                                if (event.getEntity() == Minecraft.getInstance().player) {
                                    InvTweaksConfig.setDirty(true);
                                }
                            });
        }
    }

    private void searchForSubstitute(PlayerEntity ent, Hand hand, Item item) {
        IntList frozen =
                Optional.ofNullable(InvTweaksConfig.getPlayerRules(ent).catToInventorySlots("/FROZEN"))
                        .map(IntArrayList::new) // prevent modification
                        .orElseGet(IntArrayList::new);
        frozen.sort(null);

        if (Collections.binarySearch(frozen, ent.inventory.currentItem) >= 0) {
            return; // ignore frozen slot
        }

        // thank Simon for the flattening
        ent.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.UP)
                .ifPresent(
                        cap -> {
                            for (int i = 0; i < cap.getSlots(); ++i) {
                                if (Collections.binarySearch(frozen, i) >= 0) {
                                    continue; // ignore frozen slot
                                }
                                ItemStack cand = cap.extractItem(i, Integer.MAX_VALUE, true).copy();
                                if (cand.getItem() == item) {
                                    cap.extractItem(i, Integer.MAX_VALUE, false);
                                    ent.setHeldItem(hand, cand);
                                    break;
                                }
                            }
                        });
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void keyInput(GuiScreenEvent.KeyboardKeyPressedEvent.Pre event) {
        if (event.getGui() instanceof ContainerScreen
                && !(event.getGui() instanceof CreativeScreen)
                && !(event.getGui().getListener() instanceof TextFieldWidget)
                && !isJEIKeyboardActive()) {
            // System.out.println(event.getGui().getFocused());
            if (InvTweaksConfig.isSortEnabled(true)
                    && keyBindings
                    .get("sort_player")
                    .isActiveAndMatches(
                            InputMappings.getInputByCode(event.getKeyCode(), event.getScanCode()))) {
                requestSort(true);
            }
            if (InvTweaksConfig.isSortEnabled(false)
                    && screensWithExtSort.contains(event.getGui())
                    && keyBindings
                    .get("sort_inventory")
                    .isActiveAndMatches(
                            InputMappings.getInputByCode(event.getKeyCode(), event.getScanCode()))) {
                requestSort(false);
            }

            Slot slot = ((ContainerScreen<?>) event.getGui()).getSlotUnderMouse();
            if (slot != null && slot.inventory != null) {
                boolean isPlayerSort = slot.inventory instanceof PlayerInventory;
                if (InvTweaksConfig.isSortEnabled(isPlayerSort)
                        && (isPlayerSort || screensWithExtSort.contains(event.getGui()))
                        && keyBindings
                        .get("sort_either")
                        .isActiveAndMatches(
                                InputMappings.getInputByCode(event.getKeyCode(), event.getScanCode()))) {
                    requestSort(isPlayerSort);
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void mouseInput(GuiScreenEvent.MouseClickedEvent.Pre event) {
        if (event.getGui() instanceof ContainerScreen && !(event.getGui() instanceof CreativeScreen)) {
            boolean isMouseActive =
                    keyBindings.get("sort_either").getKeyConflictContext().isActive()
                            && keyBindings.get("sort_either").matchesMouseKey(event.getButton());
            if (!isMouseActive) return;
            Slot slot = ((ContainerScreen<?>) event.getGui()).getSlotUnderMouse();
            if (slot != null && slot.inventory != null) {
                boolean isPlayerSort = slot.inventory instanceof PlayerInventory;
                if (InvTweaksConfig.isSortEnabled(isPlayerSort)
                        && (isPlayerSort || screensWithExtSort.contains(event.getGui()))) {
                    requestSort(isPlayerSort);
                    event.setCanceled(true); // stop pick block event
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
            PlayerEntity ent = Minecraft.getInstance().player;
            if (!InvTweaksConfig.isQuickViewEnabled()) {
                return;
            }

            InvTweaksConfig.Ruleset rules = InvTweaksConfig.getSelfCompiledRules();
            IntList frozen =
                    Optional.ofNullable(rules.catToInventorySlots("/FROZEN"))
                            .map(IntArrayList::new) // prevent modification
                            .orElseGet(IntArrayList::new);
            frozen.sort(null);

            assert ent != null;
            if (Collections.binarySearch(frozen, ent.inventory.currentItem) >= 0) {
                return;
            }

            HandSide dominantHand = ent.getPrimaryHand();
            int i = Minecraft.getInstance().getMainWindow().getScaledWidth() / 2;
            int i2 = Minecraft.getInstance().getMainWindow().getScaledHeight() - 16 - 3;
            int iprime;
            if (dominantHand == HandSide.RIGHT) {
                iprime = i + 91 + 10;
            } else {
                iprime = i - 91 - 26;
            }
            int itemCount =
                    IntStream.range(0, ent.inventory.mainInventory.size())
                            .filter(idx -> Collections.binarySearch(frozen, idx) < 0)
                            .mapToObj(ent.inventory.mainInventory::get)
                            .filter(st -> ItemHandlerHelper.canItemStacksStack(st, ent.getHeldItemMainhand()))
                            .mapToInt(ItemStack::getCount)
                            .sum();
            if (itemCount > ent.getHeldItemMainhand().getCount()) {
                ItemStack toRender = ent.getHeldItemMainhand().copy();
                toRender.setCount(itemCount);

                //noinspection deprecation
                RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                //noinspection deprecation
                RenderSystem.enableRescaleNormal();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                try {
                    renderHotbarItemM.invoke(
                            Minecraft.getInstance().ingameGUI,
                            iprime,
                            i2,
                            Minecraft.getInstance().getRenderPartialTicks(),
                            ent,
                            toRender);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                } finally {
                    //noinspection deprecation
                    RenderSystem.disableRescaleNormal();
                    RenderSystem.disableBlend();
                }
            }
        }
    }
}
