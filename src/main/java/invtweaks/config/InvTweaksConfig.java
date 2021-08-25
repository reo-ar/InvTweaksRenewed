package invtweaks.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.google.common.collect.ImmutableMap;
import invtweaks.InvTweaksMod;
import invtweaks.packets.PacketUpdateConfig;
import invtweaks.util.Utils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ResourceLocationException;
import net.minecraft.util.concurrent.ThreadTaskExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.LogicalSidedProvider;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class InvTweaksConfig {
    public static final ForgeConfigSpec CLIENT_CONFIG;
    /**
     * Sentinel to indicate that the GUI position should be left alone.
     */
    public static final int NO_POS_OVERRIDE = -1418392593;

    public static final String NO_SPEC_OVERRIDE = "default";
    public static final Map<String, Category> DEFAULT_CATS =
            ImmutableMap.<String, Category>builder()
                    .put("sword", new Category("/instanceof:net.minecraft.item.SwordItem"))
                    .put("axe", new Category("/instanceof:net.minecraft.item.AxeItem"))
                    .put("pickaxe", new Category("/instanceof:net.minecraft.item.PickaxeItem"))
                    .put("shovel", new Category("/instanceof:net.minecraft.item.ShovelItem"))
                    .put(
                            "acceptableFood",
                            new Category(
                                    String.format(
                                            "/instanceof:net.minecraft.item.Food; !%s; !%s; !%s; !%s",
                                            Items.ROTTEN_FLESH.getRegistryName(),
                                            Items.SPIDER_EYE.getRegistryName(),
                                            Items.POISONOUS_POTATO.getRegistryName(),
                                            Items.PUFFERFISH.getRegistryName())))
                    .put(
                            "torch",
                            new Category(Objects.requireNonNull(Items.TORCH.getRegistryName()).toString()))
                    .put("cheapBlocks", new Category("/tag:minecraft:cobblestone", "/tag:minecraft:dirt"))
                    .put("blocks", new Category("/instanceof:net.minecraft.item.BlockItem"))
                    .build();
    public static final List<String> DEFAULT_RAW_RULES = Arrays.asList("D /LOCKED", "A1-C9 /OTHER");
    public static final Ruleset DEFAULT_RULES = new Ruleset(DEFAULT_RAW_RULES);
    public static final Map<String, ContOverride> DEFAULT_CONT_OVERRIDES =
            ImmutableMap.<String, ContOverride>builder()
                    .put("com.tfar.craftingstation.CraftingStationContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("tfar.dankstorage.container.DankContainers", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE,""))
                    .put("mcjty.rftoolsutility.modules.crafter.blocks.CrafterContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("appeng.container.implementations.InterfaceTerminalContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("appeng.container.implementations.CraftingTermContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("appeng.container.implementations.PatternTermContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("appeng.container.implementations.WirelessTermContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("net.blay09.mods.excompressum.container.AutoSieveContainer", new ContOverride(NO_POS_OVERRIDE, NO_POS_OVERRIDE, ""))
                    .put("slimeknights.tconstruct.tables.inventory.table.CraftingStationContainer", new ContOverride(NO_POS_OVERRIED, NO_POS_OVERRIDE, ""))

                    .build();

    private static final ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CATS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RULES;
    private static final ForgeConfigSpec.BooleanValue ENABLE_AUTOREFILL;
    private static final ForgeConfigSpec.BooleanValue ENABLE_QUICKVIEW;
    private static final ForgeConfigSpec.IntValue ENABLE_SORT;
    private static final ForgeConfigSpec.IntValue ENABLE_BUTTONS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>>
            CONT_OVERRIDES;
    private static final Map<UUID, Map<String, Category>> playerToCats = new HashMap<>();
    private static final Map<UUID, Ruleset> playerToRules = new HashMap<>();
    private static final Set<UUID> playerAutoRefill = new HashSet<>();
    private static final Map<UUID, Map<String, ContOverride>> playerToContOverrides = new HashMap<>();
    private static Map<String, Category> COMPILED_CATS = DEFAULT_CATS;
    private static Ruleset COMPILED_RULES = DEFAULT_RULES;
    private static Map<String, ContOverride> COMPILED_CONT_OVERRIDES = DEFAULT_CONT_OVERRIDES;
    private static boolean isDirty = false;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        {
            builder.comment("Sorting customization").push("sorting");

            CATS =
                    builder
                            .comment(
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
                                    "Prepending an exclamation mark at the start of a clause inverts it")
                            .defineList(
                                    "category",
                                    DEFAULT_CATS.entrySet().stream()
                                            .map(ent -> ent.getValue().toConfig(ent.getKey()))
                                            .collect(Collectors.toList()),
                                    obj -> obj instanceof UnmodifiableConfig);

            RULES =
                    builder
                            .comment(
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
                                    "CATEGORY = /OTHER covers all remaining items after other rules are exhausted")
                            .defineList("rules", DEFAULT_RAW_RULES, obj -> obj instanceof String);

            CONT_OVERRIDES =
                    builder
                            .comment(
                                    "Custom settings per GUI",
                                    "x = x-position of external sort button relative to GUI top left",
                                    "y = same as above except for the y-position",
                                    "Omit x and y to leave position unchanged",
                                    "sortRange = slots to sort",
                                    "E.g. sortRange = \"5,0-2\" sorts slots 5,0,1,2 in that order",
                                    "sortRange = \"\" disables sorting for that container",
                                    "Out-of-bound slots are ignored",
                                    "Omit sortRange to leave as default")
                            .defineList(
                                    "containerOverrides",
                                    DEFAULT_CONT_OVERRIDES.entrySet().stream()
                                            .map(ent -> ent.getValue().toConfig(ent.getKey()))
                                            .collect(Collectors.toList()),
                                    obj -> obj instanceof UnmodifiableConfig);

            builder.pop();
        }

        {
            builder.comment("Tweaks").push("tweaks");

            ENABLE_AUTOREFILL = builder.comment("Enable auto-refill").define("autoRefill", true);
            ENABLE_QUICKVIEW =
                    builder
                            .comment(
                                    "Enable a quick view of how many items that you're currently holding exists in your inventory by displaying it next your hotbar.")
                            .define("quickView", true);
            ENABLE_SORT =
                    builder
                            .comment(
                                    "0 = disable sorting",
                                    "1 = player sorting only",
                                    "2 = external sorting only",
                                    "3 = all sorting enabled (default)")
                            .defineInRange("enableSort", 3, 0, 3);
            ENABLE_BUTTONS =
                    builder
                            .comment(
                                    "0 = disable buttons (i.e. keybind only)",
                                    "1 = buttons for player sorting only",
                                    "2 = buttons for external sorting only",
                                    "3 = all buttons enabled (default)")
                            .defineInRange("enableButtons", 3, 0, 3);

            builder.pop();
        }

        CLIENT_CONFIG = builder.build();
    }

    @SuppressWarnings("unchecked")
    public static PacketUpdateConfig getSyncPacket() {
        return new PacketUpdateConfig(
                (List<UnmodifiableConfig>) CATS.get(),
                (List<String>) RULES.get(),
                (List<UnmodifiableConfig>) CONT_OVERRIDES.get(),
                ENABLE_AUTOREFILL.get());
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onLoad(final ModConfig.Loading configEvent) {
        ThreadTaskExecutor<?> executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT);
        executor.runAsync(() -> setDirty(true));
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onReload(final ModConfig.Reloading configEvent) {
        ThreadTaskExecutor<?> executor = LogicalSidedProvider.WORKQUEUE.get(LogicalSide.CLIENT);
        executor.runAsync(() -> setDirty(true));
    }

    public static boolean isDirty() {
        return isDirty;
    }

    @SuppressWarnings("unchecked")
    public static void setDirty(boolean newVal) {
        isDirty = newVal;
        if (isDirty) {
            COMPILED_CATS = cfgToCompiledCats((List<UnmodifiableConfig>) CATS.get());
            COMPILED_RULES = new Ruleset((List<String>) RULES.get());
            COMPILED_CONT_OVERRIDES =
                    cfgToCompiledContOverrides((List<UnmodifiableConfig>) CONT_OVERRIDES.get());
        }
    }

    public static Map<String, Category> getSelfCompiledCats() {
        return COMPILED_CATS;
    }

    public static Ruleset getSelfCompiledRules() {
        return COMPILED_RULES;
    }

    public static Map<String, ContOverride> getSelfCompiledContOverrides() {
        return COMPILED_CONT_OVERRIDES;
    }

    public static void loadConfig(ForgeConfigSpec spec, Path path) {
        final CommentedFileConfig configData =
                CommentedFileConfig.builder(path)
                        .sync()
                        .autosave()
                        .writingMode(WritingMode.REPLACE)
                        .build();

        configData.load();
        spec.setConfig(configData);
    }

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
        if (DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ent == Minecraft.getInstance().player)
                == Boolean.TRUE) {
            return getSelfCompiledCats();
        }
        return playerToCats.getOrDefault(ent.getUniqueID(), DEFAULT_CATS);
    }

    public static Ruleset getPlayerRules(PlayerEntity ent) {
        if (DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ent == Minecraft.getInstance().player)
                == Boolean.TRUE) {
            return getSelfCompiledRules();
        }
        return playerToRules.getOrDefault(ent.getUniqueID(), DEFAULT_RULES);
    }

    public static boolean getPlayerAutoRefill(PlayerEntity ent) {
        if (DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ent == Minecraft.getInstance().player)
                == Boolean.TRUE) {
            return ENABLE_AUTOREFILL.get();
        }
        return playerAutoRefill.contains(ent.getUniqueID());
    }

    public static Map<String, ContOverride> getPlayerContOverrides(PlayerEntity ent) {
        if (DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ent == Minecraft.getInstance().player)
                == Boolean.TRUE) {
            return getSelfCompiledContOverrides();
        }
        return playerToContOverrides.getOrDefault(ent.getUniqueID(), DEFAULT_CONT_OVERRIDES);
    }

    public static boolean isSortEnabled(boolean isPlayerSort) {
        return isFlagEnabled(ENABLE_SORT.get(), isPlayerSort);
    }

    public static boolean isButtonEnabled(boolean isPlayer) {
        return isFlagEnabled(ENABLE_BUTTONS.get(), isPlayer);
    }

    private static boolean isFlagEnabled(int flag, boolean isPlayer) {
        return flag == 3 || flag == (isPlayer ? 1 : 2);
    }

    public static boolean isQuickViewEnabled() {
        return ENABLE_QUICKVIEW.get();
    }

    public static Map<String, Category> cfgToCompiledCats(List<UnmodifiableConfig> lst) {
        Map<String, Category> catsMap = new LinkedHashMap<>();
        for (UnmodifiableConfig subCfg : lst) {
            String name = subCfg.getOrElse("name", "");
            if (!name.equals("") && !name.startsWith("/")) {
                catsMap.put(
                        name, new InvTweaksConfig.Category(subCfg.getOrElse("spec", Collections.emptyList())));
            }
        }
        return catsMap;
    }

    public static Map<String, ContOverride> cfgToCompiledContOverrides(List<UnmodifiableConfig> lst) {
        Map<String, ContOverride> res = new LinkedHashMap<>();
        for (UnmodifiableConfig subCfg : lst) {
            res.put(
                    subCfg.getOrElse("containerClass", ""),
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
            for (String subspec : spec) {
                List<Predicate<ItemStack>> compiledSubspec = new ArrayList<>();
                for (String clause : subspec.split("\\s*;\\s*")) {
                    compileClause(clause).ifPresent(compiledSubspec::add);
                }
                compiledSpec.add(compiledSubspec);
            }
        }

        public Category(String... spec) {
            this(Arrays.asList(spec));
        }

        private static Optional<Predicate<ItemStack>> compileClause(String clause) {
            if (clause.startsWith("!")) {
                return compileClause(clause.substring(1)).map(Predicate::negate);
            }

            String[] parts = clause.split(":", 2);
            if (parts[0].equals("/tag")) {
                return Optional.of(
                        st ->
                                (Optional.ofNullable(ItemTags.getCollection().get(new ResourceLocation(parts[1])))
                                        .filter(tg -> st.getItem().isIn(tg))
                                        .isPresent()
                                        || (st.getItem() instanceof BlockItem
                                        && Optional.ofNullable(
                                        BlockTags.getCollection().get(new ResourceLocation(parts[1])))
                                        .filter(tg -> ((BlockItem) st.getItem()).getBlock().isIn(tg))
                                        .isPresent())));
            } else if (parts[0].equals("/instanceof")
                    || parts[0].equals("/class")) { // use this for e.g. pickaxes
                try {
                    Class<?> clazz = Class.forName(parts[1]);
                    if (parts[0].equals("/instanceof")) {
                        return Optional.of(st -> clazz.isInstance(st.getItem()));
                    } else {
                        return Optional.of(st -> st.getItem().getClass().equals(clazz));
                    }
                } catch (ClassNotFoundException e) {
                    InvTweaksMod.LOGGER.warn("Class not found! Ignoring clause");
                    return Optional.empty();
                }
            } else { // default to standard item checking
                try {
                    return Optional.of(
                            st -> Objects.equals(st.getItem().getRegistryName(), new ResourceLocation(clause)));
                } catch (ResourceLocationException e) {
                    InvTweaksMod.LOGGER.warn("Invalid item resource location found.");
                    return Optional.empty();
                }
            }
        }

        // returns an index for sorting within a category
        public int checkStack(ItemStack stack) {
            return IntStream.range(0, compiledSpec.size())
                    .filter(idx -> compiledSpec.get(idx).stream().allMatch(pr -> pr.test(stack)))
                    .findFirst()
                    .orElse(-1);
        }

        public CommentedConfig toConfig(String catName) {
            CommentedConfig result = CommentedConfig.inMemory();
            result.set("name", catName);
            result.set("spec", spec);
            return result;
        }
    }

    public static class Ruleset {
        private final List<String> rules;
        private final Map<String, IntList> compiledRules = new LinkedHashMap<>();
        private final IntList compiledFallbackRules =
                new IntArrayList(Utils.gridSpecToSlots("A1-D9", false));

        public Ruleset(List<String> rules) {
            this.rules = rules;
            for (String rule : rules) {
                String[] parts = rule.split("\\s+", 2);
                if (parts.length == 2) {
                    try {
                        compiledRules
                                .computeIfAbsent(parts[1], k -> new IntArrayList())
                                .addAll(IntArrayList.wrap(Utils.gridSpecToSlots(parts[0], false)));
                        if (parts[1].equals("/OTHER")) {
                            compiledFallbackRules.clear();
                            compiledFallbackRules.addAll(
                                    IntArrayList.wrap(Utils.gridSpecToSlots(parts[0], true)));
                        }
                    } catch (IllegalArgumentException e) {
                        InvTweaksMod.LOGGER.warn("Bad slot target: " + parts[0]);
                        // throw e;
                    }
                } else {
                    InvTweaksMod.LOGGER.warn("Syntax error in rule: " + rule);
                }
            }
        }

        @SuppressWarnings("unused")
        public Ruleset(String... rules) {
            this(Arrays.asList(rules));
        }

        @SuppressWarnings("unused")
        public Ruleset(Ruleset rules) {
            this.rules = rules.rules;
            this.compiledRules.putAll(rules.compiledRules);
            this.compiledFallbackRules.clear();
            this.compiledFallbackRules.addAll(rules.compiledFallbackRules);
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
            this.x = x;
            this.y = y;
            this.sortRangeSpec = sortRangeSpec;
            IntList tmp = null;
            if (sortRangeSpec.isEmpty()) {
                tmp = IntLists.EMPTY_LIST;
            } else if (!sortRangeSpec.equalsIgnoreCase(NO_SPEC_OVERRIDE)) {
                try {
                    tmp =
                            Arrays.stream(sortRangeSpec.split("\\s*,\\s*"))
                                    .flatMapToInt(
                                            str -> {
                                                String[] rangeSpec = str.split("\\s*-\\s*");
                                                return IntStream.rangeClosed(
                                                        Integer.parseInt(rangeSpec[0]), Integer.parseInt(rangeSpec[1]));
                                            })
                                    .collect(IntArrayList::new, IntList::add, IntList::addAll);
                } catch (NumberFormatException e) {
                    InvTweaksMod.LOGGER.warn("Invalid slot spec: " + sortRangeSpec);
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

        public @Nullable
        IntList getSortRange() {
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
