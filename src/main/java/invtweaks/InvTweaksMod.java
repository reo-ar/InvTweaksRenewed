package invtweaks;

import net.minecraft.client.gui.screen.inventory.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import org.apache.logging.log4j.*;

import com.google.common.base.Throwables;

import invtweaks.gui.*;
import invtweaks.packets.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import javax.annotation.*;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(InvTweaksMod.MODID)
public class InvTweaksMod {
	// Directly reference a log4j logger.
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LogManager.getLogger(InvTweaksMod.MODID.toUpperCase(Locale.US));
	
	public static final String MODID = "invtweaks";
	
	public static final String CHANNEL = "channel";
	
	public static final String NET_VERS = "1";
	
	public static SimpleChannel NET_INST;
	
	@Nullable
	private static MinecraftServer server;
	
	public InvTweaksMod() {
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
	}
	
	private void setup(final FMLCommonSetupEvent event) {
		// some preinit code
		NET_INST = NetworkRegistry.newSimpleChannel(
				new ResourceLocation(MODID, CHANNEL),
				() -> NET_VERS, NET_VERS::equals, NET_VERS::equals);
		NET_INST.registerMessage(0, PacketSortPlayerInv.class,
				PacketSortPlayerInv::encode, PacketSortPlayerInv::new, PacketSortPlayerInv::handle);
	}
	
	private void doClientStuff(final FMLClientSetupEvent event) {
		// do something that can only be done on the client
		//LOGGER.info("Got game settings {}", event.getMinecraftSupplier().get().gameSettings);
	}
	
	private void enqueueIMC(final InterModEnqueueEvent event) {
		// some example code to dispatch IMC to another mod
		//InterModComms.sendTo("examplemod", "helloworld", () -> { LOGGER.info("Hello world from the MDK"); return "Hello world";});
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
	
	private static final Field guiLeftF = ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147003_i");
	private static final Field guiTopF = ObfuscationReflectionHelper.findField(ContainerScreen.class, "field_147009_r");
	static {
		guiLeftF.setAccessible(true);
		guiTopF.setAccessible(true);
	}
	
	@OnlyIn(Dist.CLIENT)
	@SubscribeEvent
	public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
		if (event.getGui() instanceof ContainerScreen) {
			Slot placement = getButtonPlacement(
					((ContainerScreen<?>)event.getGui()).getContainer().inventorySlots,
					slot -> slot.inventory instanceof PlayerInventory
					&& !PlayerInventory.isHotbar(slot.getSlotIndex()));
			if (placement != null) {
				try {
					event.addWidget(new InvTweaksButtonSort(
							guiLeftF.getInt(event.getGui())+placement.xPos+16,
							guiTopF.getInt(event.getGui())+placement.yPos));
				} catch (Exception e) {
					Throwables.throwIfUnchecked(e);
					throw new RuntimeException(e);
				}
			}
		}
		//event.addWidget(button);
	}
	
	public static final int MIN_SLOTS = 9;
	
	public static @Nullable Slot getButtonPlacement(Collection<Slot> slots, Predicate<Slot> filter) {
		if (slots.size() < MIN_SLOTS) {
			return null;
		}
		// pick the rightmost slot first, then the bottommost in case of a tie
		// TODO change button position algorithm?
		return slots.stream().filter(filter).max(
				Comparator.<Slot>comparingDouble(s -> s.xPos)
				.thenComparingDouble(s -> s.yPos)).orElse(null);
	}
	
	/*
    // You can use EventBusSubscriber to automatically subscribe events on the contained class (this is subscribing to the MOD
    // Event bus for receiving Registry Events)
    @Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {
        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
            // register a new block here
            //LOGGER.info("HELLO from Register Block");
        }
    }*/
}
