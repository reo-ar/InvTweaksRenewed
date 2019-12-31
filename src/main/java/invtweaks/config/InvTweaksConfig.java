package invtweaks.config;

import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.core.file.*;
import com.electronwill.nightconfig.core.io.*;
import com.google.common.collect.*;

import invtweaks.*;
import net.minecraft.item.*;
import net.minecraft.tags.*;
import net.minecraft.util.*;
import net.minecraftforge.common.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.config.*;

@Mod.EventBusSubscriber
public class InvTweaksConfig {
	public static final ForgeConfigSpec CONFIG;
	
	@SuppressWarnings("unused")
	private static ForgeConfigSpec.ConfigValue<List<? extends UnmodifiableConfig>> CATS;
	
	@SuppressWarnings("unused")
	private static ForgeConfigSpec.ConfigValue<List<? extends String>> RULES;
	
	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		
		{
			builder.comment("Sorting customization").push("sorting");
			
			Map<String, Category> defaultCats = ImmutableMap.<String, Category>builder()
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
			
			CATS = builder.comment("Categor(y/ies) for sorting").defineList(
					"category",
					defaultCats.entrySet().stream()
					.map(ent -> ent.getValue().toConfig(ent.getKey())).collect(Collectors.toList()),
					obj -> {
						return obj instanceof UnmodifiableConfig;
					});
			
			RULES = builder.comment("Rules for sorting").defineList("rules", Arrays.<String>asList(),
					obj -> obj instanceof String);
			
			builder.pop();
		}
		
		CONFIG = builder.build();
	}
	
	private static boolean isDirty = false;
	
	@SubscribeEvent
	public static void onLoad(final ModConfig.Loading configEvent) {
		isDirty = true;
	}
	
	@SubscribeEvent
	public static void onReload(final ModConfig.ConfigReloading configEvent) {
		isDirty = true;
	}
	
	public static boolean isDirty() { return isDirty; }
	
	public static void loadConfig(ForgeConfigSpec spec, Path path) {
		final CommentedFileConfig configData = CommentedFileConfig.builder(path)
				.sync()
				.autosave()
				.writingMode(WritingMode.REPLACE)
				.build();
		
		configData.load();
		spec.setConfig(configData);
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
}
