package invtweaks.packets;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import invtweaks.config.InvTweaksConfig;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class PacketUpdateConfig {
  private final List<UnmodifiableConfig> cats;
  private final List<String> rules;
  private final List<UnmodifiableConfig> contOverrides;
  private final boolean autoRefill;

  public PacketUpdateConfig() {
    this(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false);
  }

  public PacketUpdateConfig(
      List<UnmodifiableConfig> cats,
      List<String> rules,
      List<UnmodifiableConfig> contOverrides,
      boolean autoRefill) {
    this.cats = cats;
    this.rules = rules;
    this.autoRefill = autoRefill;
    this.contOverrides = contOverrides;
  }

  public PacketUpdateConfig(PacketBuffer buf) {
    this.cats = new ArrayList<>();
    int catsSize = buf.readVarInt();
    for (int i = 0; i < catsSize; ++i) {
      CommentedConfig subCfg = CommentedConfig.inMemory();
      subCfg.set("name", buf.readString(32767));
      List<String> spec = new ArrayList<>();
      int specSize = buf.readVarInt();
      for (int j = 0; j < specSize; ++j) {
        spec.add(buf.readString(32767));
      }
      subCfg.set("spec", spec);
      cats.add(subCfg);
    }
    this.rules = new ArrayList<>();
    int rulesSize = buf.readVarInt();
    for (int i = 0; i < rulesSize; ++i) {
      rules.add(buf.readString(32767));
    }
    this.contOverrides = new ArrayList<>();
    int contOverridesSize = buf.readVarInt();
    for (int i = 0; i < contOverridesSize; ++i) {
      CommentedConfig contOverride = CommentedConfig.inMemory();
      contOverride.set("containerClass", buf.readString(32767));
      contOverride.set("x", buf.readInt());
      contOverride.set("y", buf.readInt());
      contOverride.set("sortRange", buf.readString(32767));
      contOverrides.add(contOverride);
    }
    this.autoRefill = buf.readBoolean();
  }

  public void handle(Supplier<NetworkEvent.Context> ctx) {
    ctx.get()
        .enqueueWork(
            () -> {
              InvTweaksConfig.setPlayerCats(
                  Objects.requireNonNull(ctx.get().getSender()),
                  InvTweaksConfig.cfgToCompiledCats(cats));
              InvTweaksConfig.setPlayerRules(
                  Objects.requireNonNull(ctx.get().getSender()),
                  new InvTweaksConfig.Ruleset(rules));
              InvTweaksConfig.setPlayerAutoRefill(ctx.get().getSender(), autoRefill);
              InvTweaksConfig.setPlayerContOverrides(
                  Objects.requireNonNull(ctx.get().getSender()),
                  InvTweaksConfig.cfgToCompiledContOverrides(contOverrides));
              // InvTweaksMod.LOGGER.info("Received config from client!");
            });
    ctx.get().setPacketHandled(true);
  }

  public void encode(PacketBuffer buf) {
    buf.writeVarInt(cats.size());
    for (UnmodifiableConfig subCfg : cats) {
      buf.writeString(subCfg.getOrElse("name", ""));
      List<String> spec = subCfg.getOrElse("spec", Collections.<String>emptyList());
      buf.writeVarInt(spec.size());
      for (String subSpec : spec) {
        buf.writeString(subSpec);
      }
    }
    buf.writeVarInt(rules.size());
    for (String subRule : rules) {
      buf.writeString(subRule);
    }
    buf.writeVarInt(contOverrides.size());
    for (UnmodifiableConfig contOverride : contOverrides) {
      buf.writeString(contOverride.getOrElse("containerClass", ""));
      int x = contOverride.getIntOrElse("x", InvTweaksConfig.NO_POS_OVERRIDE);
      int y = contOverride.getIntOrElse("y", InvTweaksConfig.NO_POS_OVERRIDE);
      buf.writeInt(x).writeInt(y);
      buf.writeString(contOverride.getOrElse("sortRange", InvTweaksConfig.NO_SPEC_OVERRIDE));
    }
    buf.writeBoolean(autoRefill);
  }
}
