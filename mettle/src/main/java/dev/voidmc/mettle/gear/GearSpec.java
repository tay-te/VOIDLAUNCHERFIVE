package dev.voidmc.mettle.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.api.GearSlot;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * The {@code mettle:gear} component: what makes any item wearable in one of the three gear slots (BURDEN.md §5). Data
 * alone can make a new backpack: give an item this component.
 *
 * @param slot       where it is worn
 * @param capacityKg carry capacity it adds while worn
 * @param pockets    how many pocket slots it has (capped per slot: 27 back, 6 belt, 4 quiver)
 * @param accepts    when present, its pockets take only items in this tag (quivers: {@code #minecraft:arrows})
 */
public record GearSpec(GearSlot slot, float capacityKg, int pockets, Optional<TagKey<Item>> accepts) {
	public static final Codec<GearSlot> SLOT_CODEC = Codec.STRING.comapFlatMap(name -> {
		try {
			return DataResult.success(GearSlot.valueOf(name.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "unknown gear slot " + name + " (back, belt or quiver)");
		}
	}, slot -> slot.name().toLowerCase(Locale.ROOT));

	public static final Codec<GearSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
		SLOT_CODEC.fieldOf("slot").forGetter(GearSpec::slot),
		Codec.floatRange(0f, 10_000f).optionalFieldOf("capacity_kg", 0f).forGetter(GearSpec::capacityKg),
		Codec.intRange(0, GearRules.MAX_POCKETS_ANY).optionalFieldOf("pockets", 0).forGetter(GearSpec::pockets),
		TagKey.hashedCodec(Registries.ITEM).optionalFieldOf("accepts").forGetter(GearSpec::accepts)
	).apply(i, GearSpec::new));

	public static final StreamCodec<ByteBuf, GearSpec> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.idMapper(i -> GearSlot.values()[i], GearSlot::ordinal), GearSpec::slot,
		ByteBufCodecs.FLOAT, GearSpec::capacityKg,
		ByteBufCodecs.VAR_INT, GearSpec::pockets,
		ByteBufCodecs.optional(TagKey.streamCodec(Registries.ITEM)), GearSpec::accepts,
		GearSpec::new);

	/** Pocket slots actually usable: the slot's layout has room for 27 / 6 / 4. */
	public int pocketCount() {
		return Math.min(pockets, GearRules.maxPockets(slot));
	}
}
