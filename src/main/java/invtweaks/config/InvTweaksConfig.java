package invtweaks.config;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.core.file.*;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.common.collect.*;

import invtweaks.*;
import invtweaks.packets.*;
import invtweaks.util.*;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.tags.*;
import net.minecraft.util.*;
import net.minecraftforge.common.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.config.*;

@Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
public class InvTweaksConfig {
	public static final ForgeConfigSpec CONFIG;
	
	private static ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CATS;
	
	private static ForgeConfigSpec.ConfigValue<List<? extends String>> RULES;
	
	public static final Map<String, Category> DEFAULT_CATS = ImmutableMap.<String, Category>builder()
			.put("sword", new Category("/instanceof:net.minecraft.item.SwordItem"))
			.put("axe", new Category("/instanceof:net.minecraft.item.AxeItem"))
			.put("pickaxe", new Category("/instanceof:net.minecraft.item.PickaxeItem"))
			.put("shovel", new Category("/instanceof:net.minecraft.item.ShovelItem"))
			.put("acceptableFood", new Category(
					String.format("/instanceof:net.minecraft.item.Food; !%s; !%s; !%s; !%s",
							Items.ROTTEN_FLESH.getRegistryName(),
							Items.SPIDER_EYE.getRegistryName(),
							Items.POISONOUS_POTATO.getRegistryName(),
							Items.PUFFERFISH.getRegistryName())
					))
			.put("torch", new Category(Items.TORCH.getRegistryName().toString()))
			.put("cheapblocks", new Category("/tag:cobblestone", "/tag:dirt"))
			.put("blocks", new Category("/instanceof:net.minecraft.item.BlockItem"))
			.build();
	public static final List<String> DEFAULT_RAW_RULES = Arrays.asList("D1 sword", "D2 pickaxe", "D3-D9 /FROZEN", "A1-C9v /OTHER");
	public static final Ruleset DEFAULT_RULES = new Ruleset(DEFAULT_RAW_RULES);
	
	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		
		{
			builder.comment("Sorting customization").push("sorting");
			
			CATS = builder.comment("Categor(y/ies) for sorting").defineList(
					"category",
					DEFAULT_CATS.entrySet().stream()
					.map(ent -> ent.getValue().toConfig(ent.getKey())).collect(Collectors.toList()),
					obj -> {
						return obj instanceof UnmodifiableConfig;
					});
			
			RULES = builder.comment("Rules for sorting").defineList("rules",
					DEFAULT_RAW_RULES,
					obj -> obj instanceof String);
			
			builder.pop();
		}
		
		CONFIG = builder.build();
	}
	
	@SuppressWarnings("unchecked")
	public static PacketUpdateConfig getSyncPacket() {
		return new PacketUpdateConfig((List<UnmodifiableConfig>)CATS.get(), (List<String>)RULES.get());
	}
	
	private static boolean isDirty = false;
	
	@SubscribeEvent
	public static void onLoad(final ModConfig.Loading configEvent) {
		setDirty(true);
	}
	
	@SubscribeEvent
	public static void onReload(final ModConfig.ConfigReloading configEvent) {
		setDirty(true);
	}
	
	public static boolean isDirty() { return isDirty; }
	public static void setDirty(boolean newVal) { isDirty = newVal; }
	
	public static void loadConfig(ForgeConfigSpec spec, Path path) {
		final CommentedFileConfig configData = CommentedFileConfig.builder(path)
				.sync()
				.autosave()
				.writingMode(WritingMode.REPLACE)
				.build();
		
		configData.load();
		spec.setConfig(configData);
	}
	
	private static final Map<PlayerEntity, Map<String, Category>> playerToCats = new WeakHashMap<>();
	private static final Map<PlayerEntity, Ruleset> playerToRules = new WeakHashMap<>();
	
	public static void setPlayerCats(PlayerEntity ent, Map<String, Category> cats) {
		playerToCats.put(ent, cats);
	}
	public static void setPlayerRules(PlayerEntity ent, Ruleset ruleset) {
		playerToRules.put(ent, ruleset);
	}
	public static Map<String, Category> getPlayerCats(PlayerEntity ent) {
		return playerToCats.getOrDefault(ent, DEFAULT_CATS);
	}
	public static Ruleset getPlayerRules(PlayerEntity ent) {
		return playerToRules.getOrDefault(ent, DEFAULT_RULES);
	}
	
	public static class Category {
		private final List<String> spec;
		private final List<List<Predicate<ItemStack>>> compiledSpec = new ArrayList<>();
		
		public Category(List<String> spec) {
			this.spec = spec;
			for (String subspec: spec) {
				List<Predicate<ItemStack>> compiledSubspec = new ArrayList<>();
				for (String clause: subspec.split("\\s*;\\s*")) {
					compileClause(clause).ifPresent(compiledSubspec::add);
				}
				compiledSpec.add(compiledSubspec);
			}
		}
		public Category(String...spec) { this(Arrays.asList(spec)); }
		
		private static Optional<Predicate<ItemStack>> compileClause(String clause) {
			if (clause.startsWith("!")) {
				return compileClause(clause.substring(1)).map(Predicate::negate);
			}
			
			String[] parts = clause.split(":", 2);
			if (parts[0].equals("/tag")) { // F to pay respects to oredict
				return Optional.of(
						st -> Optional.ofNullable(ItemTags.getCollection().get(new ResourceLocation(parts[1])))
						.filter(tg -> tg.contains(st.getItem()))
						.isPresent());
			} else if (parts[0].equals("/instanceof") || parts[0].equals("/class")) { // use this for e.g. pickaxes
				try {
					Class<?> clazz = Class.forName(parts[1]);
					if (parts[0].equals("/instanceof")) {
						return Optional.of(st -> clazz.isInstance(st.getItem()));
					} else {
						return Optional.of(st -> st.getItem().getClass().equals(clazz));
					}
				} catch (ClassNotFoundException e) {
					InvTweaksMod.LOGGER.warn("Class %s not found! Ignoring clause", parts[1]);
					return Optional.empty();
				}
			} else {// default to standard item checking
				try {
					return Optional.of(st -> Objects.equals(st.getItem().getRegistryName(), new ResourceLocation(clause)));
				} catch (ResourceLocationException e) {
					InvTweaksMod.LOGGER.warn("Invalid item resource location: %s", clause);
					return Optional.empty();
				}
			}
		}
		
		// returns an index for sorting within a category
		public int checkStack(ItemStack stack) {
			return IntStream.range(0, compiledSpec.size())
					.filter(idx -> compiledSpec.get(idx).stream().allMatch(pr -> pr.test(stack)))
					.findFirst().orElse(-1);
		}
		
		public CommentedConfig toConfig(String catName) {
			CommentedConfig result = CommentedConfig.inMemory();
			result.set("name", catName);
			result.set("spec", spec);
			return result;
		}
	}
	
	public static class Ruleset {
		@SuppressWarnings("unused")
		private final List<String> rules;
		private final Map<String, IntList> compiledRules = new LinkedHashMap<>();
		
		public Ruleset(List<String> rules) {
			this.rules = rules;
			for (String rule: rules) {
				String[] parts = rule.split("\\s+", 2);
				if (parts.length == 2) {
					try {
						compiledRules.computeIfAbsent(parts[1], k -> new IntArrayList())
						.addAll(IntArrayList.wrap(Utils.gridSpecToSlots(parts[0])));
					} catch (IllegalArgumentException e) {
						InvTweaksMod.LOGGER.warn("Bad slot target: "+parts[0]);
					}
				} else {
					InvTweaksMod.LOGGER.warn("Syntax error in rule: "+rule);
				}
			}
		}
		public Ruleset(String...rules) {
			this(Arrays.asList(rules));
		}
	}
}
