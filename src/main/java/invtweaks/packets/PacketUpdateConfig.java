package invtweaks.packets;

import java.util.*;
import java.util.function.*;

import com.electronwill.nightconfig.core.*;

import invtweaks.config.*;
import net.minecraft.network.*;
import net.minecraftforge.fml.network.*;

public class PacketUpdateConfig {
	private final List<UnmodifiableConfig> cats;
	private final List<String> rules;
	private final boolean autoRefill;
	
	public PacketUpdateConfig() { this(Collections.emptyList(), Collections.emptyList(), false); }
	public PacketUpdateConfig(List<UnmodifiableConfig> cats, List<String> rules, boolean autoRefill) {
		this.cats = cats;
		this.rules = rules;
		this.autoRefill = autoRefill;
	}
	public PacketUpdateConfig(PacketBuffer buf) {
		this.cats = new ArrayList<>();
		int catsSize = buf.readVarInt();
		for (int i=0; i<catsSize; ++i) {
			CommentedConfig subCfg = CommentedConfig.inMemory();
			subCfg.set("name", buf.readString(32767));
			List<String> spec = new ArrayList<>();
			int specSize = buf.readVarInt();
			for (int j=0; j<specSize; ++j) {
				spec.add(buf.readString(32767));
			}
			subCfg.set("spec", spec);
			cats.add(subCfg);
		}
		this.rules = new ArrayList<>();
		int rulesSize = buf.readVarInt();
		for (int i=0; i<rulesSize; ++i) {
			rules.add(buf.readString(32767));
		}
		this.autoRefill = buf.readBoolean();
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			InvTweaksConfig.setPlayerCats(ctx.get().getSender(), InvTweaksConfig.cfgToCompiledCats(cats));
			InvTweaksConfig.setPlayerRules(ctx.get().getSender(), new InvTweaksConfig.Ruleset(rules));
			InvTweaksConfig.setPlayerAutoRefill(ctx.get().getSender(), autoRefill);
			
			//InvTweaksMod.LOGGER.info("Received config from client!");
		});
	}
	
	public void encode(PacketBuffer buf) {
		buf.writeVarInt(cats.size());
		for (UnmodifiableConfig subCfg: cats) {
			buf.writeString(subCfg.getOrElse("name", ""));
			List<String> spec = subCfg.getOrElse("spec", Collections.<String>emptyList());
			buf.writeVarInt(spec.size());
			for (String subSpec: spec) {
				buf.writeString(subSpec);
			}
		}
		
		buf.writeVarInt(rules.size());
		for (String subRule: rules) {
			buf.writeString(subRule);
		}
		buf.writeBoolean(autoRefill);
	}
}
