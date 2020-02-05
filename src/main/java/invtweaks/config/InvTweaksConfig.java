package invtweaks.config;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import javax.annotation.*;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.core.file.*;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.common.collect.*;

import invtweaks.*;
import invtweaks.packets.*;
import invtweaks.util.*;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.container.*;
import net.minecraft.item.*;
import net.minecraft.tags.*;
import net.minecraft.util.*;
import net.minecraft.util.concurrent.*;
import net.minecraftforge.common.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.config.*;

@Mod.EventBusSubscriber(bus=Mod.EventBusSubscriber.Bus.MOD)
public class InvTweaksConfig {
	public static final ForgeConfigSpec CLIENT_CONFIG;
	
	private static ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CATS;
	
	private static ForgeConfigSpec.ConfigValue<List<? extends String>> RULES;
	
	private static ForgeConfigSpec.BooleanValue ENABLE_AUTOREFILL;
	
	private static ForgeConfigSpec.IntValue ENABLE_SORT;
	
	/**
	 * Sentinel to indicate that the GUI position should be left alone.
	 */
	public static final int NO_POS_OVERRIDE = -1418392593;
	
	public static final String NO_SPEC_OVERRIDE = "default";
	
	// containerClass
	// x, y
	// sortRange
	private static ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CONT_OVERRIDES;
	
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
			.put("cheapBlocks", new Category("/tag:forge:cobblestone", "/tag:forge:dirt"))
			.put("blocks", new Category("/instanceof:net.minecraft.item.BlockItem"))
			.build();
	public static final List<String> DEFAULT_RAW_RULES = Arrays.asList("D /LOCKED", "A1-C9 /OTHER");
	public static final Ruleset DEFAULT_RULES = new Ruleset(DEFAULT_RAW_RULES);
	public static final Map<String, ContOverride> DEFAULT_CONT_OVERRIDES
		= ImmutableMap.of(ChestContainer.class.getName(), new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, "0-8")); // TODO remove test
	
	@SuppressWarnings("unused")
	private static Map<String, Category> COMPILED_CATS = DEFAULT_CATS;
	private static Ruleset COMPILED_RULES = DEFAULT_RULES;
	private static Map<String, ContOverride> COMPILED_CONT_OVERRIDES = DEFAULT_CONT_OVERRIDES;
	
	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		
		{
			builder.comment("Sorting customization").push("sorting");
			
			CATS = builder.comment(
					"Categor(y/ies) for sorting",
					"",
					"name: the name of the category",
					"",
					"spec:",
					"Each element denotes a series of semicolon-separated clauses",
					"Items need to match all clauses of at least one element",
					"Items matching earlier elements are earlier in order",
					"A clause of the form /tag:<tag_value> matches a tag",
					"Clauses /instanceof:<fully_qualified_name> or /class:<fully_qualified_name> check if item is",
					"instance of class or exactly of that class respectively",
					"Specifying an item's registry name as a clause checks for that item",
					"Prepending an exclamation mark at the start of a clause inverts it"
					).defineList(
					"category",
					DEFAULT_CATS.entrySet().stream()
					.map(ent -> ent.getValue().toConfig(ent.getKey())).collect(Collectors.toList()),
					obj -> {
						return obj instanceof UnmodifiableConfig;
					});
			
			RULES = builder.comment(
					"Rules for sorting",
					"Each element is of the form <POS> <CATEGORY>",
					"A-D is the row from top to bottom",
					"1-9 is the column from left to right",
					"POS denotes the target slots",
					"Exs. POS = D3 means 3rd slot of hotbar",
					"     POS = B means 2nd row, left to right",
					"     POS = 9 means 9th column, bottom to top",
					"     POS = A1-C9 means slots A1,A2,…,A9,B1,…,B9,C1,…,C9",
					"     POS = A9-C1 means slots A9,A8,…,A1,B9,…,B1,C9,…,C1",
					"Append v to POS of the form A1-C9 to move in columns instead of rows",
					"Append r to POS of the form B or 9 to reverse slot order",
					"CATEGORY is the item category to designate the slots to",
					"CATEGORY = /LOCKED prevents slots from moving in sorting",
					"CATEGORY = /FROZEN has the effect of /LOCKED and, in addition, ignores slot in auto-refill",
					"CATEGORY = /OTHER covers all remaining items after other rules are exhausted"
					).defineList("rules",
					DEFAULT_RAW_RULES,
					obj -> obj instanceof String);
			
			CONT_OVERRIDES = builder.comment()
					.defineList("containerOverrides",
							DEFAULT_CONT_OVERRIDES.entrySet().stream()
							.map(ent -> ent.getValue().toConfig(ent.getKey())).collect(Collectors.toList()),
							obj -> obj instanceof UnmodifiableConfig);
			
			builder.pop();
		}
		
		{
			builder.comment("Tweaks").push("tweaks");
			
			ENABLE_AUTOREFILL = builder.comment("Enable auto-refill").define("autoRefill", true);
			ENABLE_SORT = builder.comment(
					"0 = disable sorting",
					"1 = player sorting only",
					"2 = external sorting only",
					"3 = all sorting enabled (default)"
					).defineInRange("enableSort", 3, 0, 3);
			
			builder.pop();
		}
		
		CLIENT_CONFIG = builder.build();
	}
	
	@SuppressWarnings("unchecked")
	public static PacketUpdateConfig getSyncPacket() {
		return new PacketUpdateConfig((List<UnmodifiableConfig>)CATS.get(), (List<String>)RULES.get(), (List<UnmodifiableConfig>)CONT_OVERRIDES.get(), ENABLE_AUTOREFILL.get());
	}
	
	private static boolean isDirty = false;
	
	@SubscribeEvent
	public static void onLoad(final ModConfig.Loading configEvent) {
		ThreadTaskExecutor<?> executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT);
		executor.runAsync(() -> setDirty(true));
	}
	
	@SubscribeEvent
	public static void onReload(final ModConfig.ConfigReloading configEvent) {
		ThreadTaskExecutor<?> executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT);
		executor.runAsync(() -> setDirty(true));
	}
	
	public static boolean isDirty() { return isDirty; }
	@SuppressWarnings("unchecked")
	public static void setDirty(boolean newVal) {
		isDirty = newVal;
		if (isDirty) {
			COMPILED_CATS = cfgToCompiledCats((List<UnmodifiableConfig>)CATS.get());
			COMPILED_RULES = new Ruleset((List<String>)RULES.get());
			COMPILED_CONT_OVERRIDES = cfgToCompiledContOverrides((List<UnmodifiableConfig>)CONT_OVERRIDES.get());
		}
	}
	
	public static Ruleset getSelfCompiledRules() {
		return COMPILED_RULES;
	}
	
	public static Map<String, ContOverride> getSelfCompiledContOverrides() {
		return COMPILED_CONT_OVERRIDES;
	}
	
	public static void loadConfig(ForgeConfigSpec spec, Path path) {
		final CommentedFileConfig configData = CommentedFileConfig.builder(path)
				.sync()
				.autosave()
				.writingMode(WritingMode.REPLACE)
				.build();
		
		configData.load();
		spec.setConfig(configData);
	}
	
	private static final Map<UUID, Map<String, Category>> playerToCats = new HashMap<>();
	private static final Map<UUID, Ruleset> playerToRules = new HashMap<>();
	private static final Set<UUID> playerAutoRefill = new HashSet<>();
	private static final Map<UUID, Map<String, ContOverride>> playerToContOverrides = new HashMap<>();
	
	public static void setPlayerCats(PlayerEntity ent, Map<String, Category> cats) {
		playerToCats.put(ent.getUniqueID(), cats);
	}
	public static void setPlayerRules(PlayerEntity ent, Ruleset ruleset) {
		playerToRules.put(ent.getUniqueID(), ruleset);
	}
	public static void setPlayerAutoRefill(PlayerEntity ent, boolean autoRefill) {
		if (autoRefill) {
			playerAutoRefill.add(ent.getUniqueID());
		} else {
			playerAutoRefill.remove(ent.getUniqueID());
		}
	}
	public static void setPlayerContOverrides(PlayerEntity ent, Map<String, ContOverride> val) {
		playerToContOverrides.put(ent.getUniqueID(), val);
	}
	
	public static Map<String, Category> getPlayerCats(PlayerEntity ent) {
		return playerToCats.getOrDefault(ent.getUniqueID(), DEFAULT_CATS);
	}
	public static Ruleset getPlayerRules(PlayerEntity ent) {
		return playerToRules.getOrDefault(ent.getUniqueID(), DEFAULT_RULES);
	}
	public static boolean getPlayerAutoRefill(PlayerEntity ent) {
		return playerAutoRefill.contains(ent.getUniqueID());
	}
	public static Map<String, ContOverride> getPlayerContOverrides(PlayerEntity ent) {
		return playerToContOverrides.getOrDefault(ent.getUniqueID(), DEFAULT_CONT_OVERRIDES);
	}
	
	public static boolean isSortEnabled(boolean isPlayerSort) {
		return ENABLE_SORT.get() == 3 || ENABLE_SORT.get() == (isPlayerSort ? 1 : 2);
	}
	
	public static Map<String, Category> cfgToCompiledCats(List<UnmodifiableConfig> lst) {
		Map<String, Category> catsMap = new LinkedHashMap<>();
		for (UnmodifiableConfig subCfg: lst) {
			String name = subCfg.getOrElse("name", "");
			if (!name.equals("") && !name.startsWith("/")) {
				catsMap.put(name,
						new InvTweaksConfig.Category(subCfg.getOrElse("spec", Collections.<String>emptyList())
								));
			}
		}
		return catsMap;
	}
	
	public static Map<String, ContOverride> cfgToCompiledContOverrides(List<UnmodifiableConfig> lst) {
		Map<String, ContOverride> res = new LinkedHashMap<>();
		for (UnmodifiableConfig subCfg: lst) {
			res.put(subCfg.getOrElse("containerClass", ""),
					new ContOverride(
							subCfg.getOrElse("x", NO_POS_OVERRIDE),
							subCfg.getOrElse("y", NO_POS_OVERRIDE),
							subCfg.getOrElse("sortRange", NO_SPEC_OVERRIDE)));
		}
		return res;
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
						st -> (
								Optional.ofNullable(ItemTags.getCollection().get(new ResourceLocation(parts[1])))
								.filter(tg -> tg.contains(st.getItem()))
								.isPresent()
								||
								(st.getItem() instanceof BlockItem
										&& Optional.ofNullable(BlockTags.getCollection().get(new ResourceLocation(parts[1])))
										.filter(tg -> tg.contains(((BlockItem)st.getItem()).getBlock()))
										.isPresent())
						));
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
		private final IntList compiledFallbackRules = new IntArrayList(Utils.gridSpecToSlots("A1-D9", false));
		
		public Ruleset(List<String> rules) {
			this.rules = rules;
			for (String rule: rules) {
				String[] parts = rule.split("\\s+", 2);
				if (parts.length == 2) {
					try {
						compiledRules.computeIfAbsent(parts[1], k -> new IntArrayList())
						.addAll(IntArrayList.wrap(Utils.gridSpecToSlots(parts[0], false)));
						if (parts[1].equals("/OTHER")) {
							compiledFallbackRules.clear();
							compiledFallbackRules.addAll(
									IntArrayList.wrap(Utils.gridSpecToSlots(parts[0], true)));
						}
					} catch (IllegalArgumentException e) {
						InvTweaksMod.LOGGER.warn("Bad slot target: "+parts[0]);
						//throw e;
					}
				} else {
					InvTweaksMod.LOGGER.warn("Syntax error in rule: "+rule);
				}
			}
		}
		public Ruleset(String...rules) {
			this(Arrays.asList(rules));
		}
		
		public IntList catToInventorySlots(String cat) {
			return compiledRules.get(cat);
		}
		
		public IntList fallbackInventoryRules() {
			return compiledFallbackRules;
		}
	}
	
	public static class ContOverride {
		private final int x, y;
		@Nullable
		private final IntList sortRange;
		private final String sortRangeSpec;
		
		public ContOverride(int x, int y, String sortRangeSpec) {
			this.x = x; this.y = y;
			this.sortRangeSpec = sortRangeSpec;
			IntList tmp = null;
			if (!sortRangeSpec.equalsIgnoreCase(NO_SPEC_OVERRIDE)) {
				try {
					tmp = Arrays.stream(sortRangeSpec.split("\\s*,\\s*"))
					.flatMapToInt(str -> {
						String[] rangeSpec = str.split("\\s*-\\s*");
						return IntStream.rangeClosed(Integer.parseInt(rangeSpec[0]), Integer.parseInt(rangeSpec[1]));
					})
					.collect(IntArrayList::new, IntList::add, IntList::addAll);
				} catch (NumberFormatException e) {
					InvTweaksMod.LOGGER.warn("Invalid slot spec: "+sortRangeSpec);
					tmp = IntLists.EMPTY_LIST;
				}
			}
			sortRange = tmp;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public @Nullable IntList getSortRange() {
			return sortRange;
		}
		
		public boolean isSortDisabled() {
			return sortRange != null && sortRange.isEmpty();
		}
		
		public CommentedConfig toConfig(String contClass) {
			CommentedConfig result = CommentedConfig.inMemory();
			result.set("containerClass", contClass);
			if (x != NO_POS_OVERRIDE) result.set("x", x);
			if (y != NO_POS_OVERRIDE) result.set("y", y);
			if (!sortRangeSpec.equalsIgnoreCase(NO_SPEC_OVERRIDE)) result.set("sortRange", sortRangeSpec);
			return result;
		}
	}
}
