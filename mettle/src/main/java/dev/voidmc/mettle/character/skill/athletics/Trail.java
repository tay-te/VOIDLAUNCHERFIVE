package dev.voidmc.mettle.character.skill.athletics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.AthleticsSkill;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Where a player has been, for Trailblazer (Survival 10 + Athletics 10: a new biome or place gives Speed I and
 * Regeneration I for 60 s) and Mountain Goat (stand above y 220 in ten places 500 blocks apart). Checked every two
 * seconds per player. Biomes and places (structure kinds) are tracked from the first step, perk or not, so "new" means
 * new to the character. The lists are bounded by the registries (and ten peaks), and never synced.
 */
public final class Trail {
	public static final String PEAKS_COUNTER = "athletics_peaks";

	public record Journal(List<String> biomes, List<String> places, List<Long> peaks) {
		public static final Journal EMPTY = new Journal(List.of(), List.of(), List.of());
		public static final Codec<Journal> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(Journal::biomes),
			Codec.STRING.listOf().optionalFieldOf("places", List.of()).forGetter(Journal::places),
			Codec.LONG.listOf().optionalFieldOf("peaks", List.of()).forGetter(Journal::peaks)
		).apply(i, Journal::new));

		Journal with(List<String> biomes, List<String> places, List<Long> peaks) {
			return new Journal(List.copyOf(biomes), List.copyOf(places), List.copyOf(peaks));
		}
	}

	public static final AttachmentType<Journal> JOURNAL = AttachmentRegistry.create(Mettle.id("athletics/trail"), b -> b
		.persistent(Journal.CODEC)
		.copyOnDeath());

	private Trail() {
	}

	public static Journal journal(ServerPlayer player) {
		return player.getAttachedOrElse(JOURNAL, Journal.EMPTY);
	}

	public static void check(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Journal journal = journal(player);
		List<String> biomes = new ArrayList<>(journal.biomes());
		List<String> places = new ArrayList<>(journal.places());
		List<Long> peaks = new ArrayList<>(journal.peaks());
		Component found = null;

		Identifier biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier()).orElse(null);
		if (biome != null && !biomes.contains(biome.toString())) {
			biomes.add(biome.toString());
			found = Component.translatable(Util.makeDescriptionId("biome", biome));
		}
		Map<Structure, LongSet> nearby = level.structureManager().getAllStructuresAt(pos);
		if (!nearby.isEmpty()) {
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			for (Map.Entry<Structure, LongSet> e : nearby.entrySet()) {
				Identifier id = registry.getKey(e.getKey());
				if (e.getValue().isEmpty() || id == null || places.contains(id.toString())) {
					continue;
				}
				StructureStart start = level.structureManager().getStructureWithPieceAt(pos.getX(), pos.getY(), pos.getZ(), e.getKey());
				if (start.isValid()) {
					places.add(id.toString());
					found = Component.literal(AthleticsRules.pretty(id.getPath()));
				}
			}
		}

		boolean peak = peak(player, peaks);
		if (found != null || peak) {
			player.setAttached(JOURNAL, journal.with(biomes, places, peaks));
		}
		if (peak) {
			FeatCounters.add(player, PEAKS_COUNTER, 1);
		}
		// Trailblazer (Survival 10 + Athletics 10) is Survival's: it owns discovery (character.skill.survival.Discovery).
	}

	/** Mountain Goat: standing high, far from every peak counted before. Adds it to {@code peaks}. */
	private static boolean peak(ServerPlayer player, List<Long> peaks) {
		if (player.getY() <= AthleticsRules.PEAK_Y || !player.onGround() || player.isCreative() || peaks.size() >= AthleticsRules.PEAKS
			|| Perks.has(player, AthleticsSkill.MOUNTAIN_GOAT)) {
			return false;
		}
		long[] known = peaks.stream().mapToLong(Long::longValue).toArray();
		if (!AthleticsRules.newPeak(known, player.getBlockX(), player.getBlockZ())) {
			return false;
		}
		peaks.add(AthleticsRules.packPeak(player.getBlockX(), player.getBlockZ()));
		return true;
	}
}
