package dev.voidmc.fray.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.fray.Fray;
import dev.voidmc.fray.api.DamageKind;
import dev.voidmc.fray.api.StatusType;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The {@code fray:damage_split} item component (CONTRACTS.md §5, §12): kind → share, the weapon's poise factor and the
 * build-up it adds per hit. Arsenal writes it from parts; vanilla weapons get Fray's defaults by tag
 * ({@link Weapons#split}).
 */
public record DamageSplit(Map<DamageKind, Float> shares, float poiseFactor, Map<StatusType, Float> buildUp) {
	public static final int VERSION = 1;

	private static final Codec<DamageKind> KIND = Codec.STRING.xmap(DamageKind::byId, DamageKind::id);
	private static final Codec<StatusType> STATUS = Codec.STRING.xmap(StatusType::byId, StatusType::id);

	public static final Codec<DamageSplit> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("version", VERSION).forGetter(s -> VERSION),
		Codec.unboundedMap(KIND, Codec.FLOAT).fieldOf("shares").forGetter(DamageSplit::shares),
		Codec.FLOAT.optionalFieldOf("poise_factor", 1F).forGetter(DamageSplit::poiseFactor),
		Codec.unboundedMap(STATUS, Codec.FLOAT).optionalFieldOf("build_up", Map.of()).forGetter(DamageSplit::buildUp)
	).apply(i, (v, shares, poise, build) -> new DamageSplit(shares, poise, build)));

	public static final StreamCodec<ByteBuf, DamageSplit> STREAM_CODEC = StreamCodec.of((b, s) -> {
		ByteBufCodecs.VAR_INT.encode(b, s.shares().size());
		s.shares().forEach((k, v) -> {
			ByteBufCodecs.VAR_INT.encode(b, k.ordinal());
			b.writeFloat(v);
		});
		b.writeFloat(s.poiseFactor());
		ByteBufCodecs.VAR_INT.encode(b, s.buildUp().size());
		s.buildUp().forEach((k, v) -> {
			ByteBufCodecs.VAR_INT.encode(b, k.ordinal());
			b.writeFloat(v);
		});
	}, b -> {
		Map<DamageKind, Float> shares = new EnumMap<>(DamageKind.class);
		int n = Math.min(16, ByteBufCodecs.VAR_INT.decode(b));
		for (int i = 0; i < n; i++) {
			shares.put(DamageKind.VALUES[ByteBufCodecs.VAR_INT.decode(b)], b.readFloat());
		}
		float poise = b.readFloat();
		Map<StatusType, Float> build = new EnumMap<>(StatusType.class);
		int m = Math.min(16, ByteBufCodecs.VAR_INT.decode(b));
		for (int i = 0; i < m; i++) {
			build.put(StatusType.VALUES[ByteBufCodecs.VAR_INT.decode(b)], b.readFloat());
		}
		return new DamageSplit(shares, poise, build);
	});

	public static final DataComponentType<DamageSplit> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Fray.id("damage_split"),
		DataComponentType.<DamageSplit>builder().persistent(CODEC).networkSynchronized(STREAM_CODEC).build());

	public static void init() {
	}

	public static DamageSplit of(DamageKind kind, float poiseFactor) {
		Map<DamageKind, Float> m = new EnumMap<>(DamageKind.class);
		m.put(kind, 1F);
		return new DamageSplit(m, poiseFactor, Map.of());
	}

	public static DamageSplit of(DamageKind a, float shareA, DamageKind b, float shareB, float poiseFactor) {
		Map<DamageKind, Float> m = new EnumMap<>(DamageKind.class);
		m.put(a, shareA);
		m.put(b, shareB);
		return new DamageSplit(m, poiseFactor, Map.of());
	}
}
