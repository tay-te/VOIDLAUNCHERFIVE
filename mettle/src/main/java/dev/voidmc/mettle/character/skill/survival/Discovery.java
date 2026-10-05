package dev.voidmc.mettle.character.skill.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.mettle.Mettle;
import dev.voidmc.mettle.character.FeatCounters;
import dev.voidmc.mettle.character.Perks;
import dev.voidmc.mettle.character.skill.SurvivalSkill;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.Waypoint;

/**
 * First visits (BALANCE.md §3): each biome, each structure instance, each landmark. Checked every 40 ticks per player,
 * cheaply (a biome lookup, and a structure lookup only where the chunk has structure references). Remembered in an
 * unsynced, persistent journal that grows with play. Discoveries feed Weatherwise and Seen It All, set Lay of the Land's
 * marker and trigger Trailblazer.
 */
public final class Discovery {
	public static final int INTERVAL = 40;
	/** The chip key for discoveries (not a perk: everyone discovers). */
	public static final Identifier FEEDBACK = Mettle.id("survival/discovery");
	/** Lay of the Land's marker on the locator bar: one fixed id per player, so it is replaced rather than piled up. */
	public static final UUID MARKER = UUID.nameUUIDFromBytes("mettle:survival/lay_of_the_land".getBytes(StandardCharsets.UTF_8));
	/** Muted parchment, so the dot sits quietly among the bar's own. */
	private static final int MARKER_COLOUR = 0xC9B48A;
	/** At most one landmark search per player this often. */
	private static final int SEARCH_COOLDOWN = 200;
	private static final int TRAILBLAZER_TICKS = 1200;

	/**
	 * What a player has found. {@code places} are structure instances ({@code id@chunkX,chunkZ}); {@code kinds} the kinds
	 * of landmark among them; {@code marker} Lay of the Land's current target.
	 */
	public record Journal(Set<Identifier> biomes, Set<String> places, Set<Identifier> kinds, Optional<Marker> marker) {
		public static final Journal EMPTY = new Journal(Set.of(), Set.of(), Set.of(), Optional.empty());
		public static final Codec<Journal> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("biomes", Set.of()).forGetter(Journal::biomes),
			Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("places", Set.of()).forGetter(Journal::places),
			Identifier.CODEC.listOf().xmap(Set::copyOf, List::copyOf).optionalFieldOf("kinds", Set.of()).forGetter(Journal::kinds),
			Marker.CODEC.optionalFieldOf("marker").forGetter(Journal::marker)
		).apply(i, Journal::new));

		Journal withBiome(Identifier biome) {
			return new Journal(plus(this.biomes, biome), this.places, this.kinds, this.marker);
		}

		Journal withPlace(String place, Identifier kind, boolean landmark) {
			return new Journal(this.biomes, plus(this.places, place), landmark ? plus(this.kinds, kind) : this.kinds, this.marker);
		}

		Journal withMarker(Optional<Marker> m) {
			return new Journal(this.biomes, this.places, this.kinds, m);
		}

		private static <T> Set<T> plus(Set<T> set, T value) {
			Set<T> next = new HashSet<>(set);
			next.add(value);
			return Set.copyOf(next);
		}
	}

	public record Marker(Identifier dimension, BlockPos pos, Identifier kind) {
		public static final Codec<Marker> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("dimension").forGetter(Marker::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Marker::pos),
			Identifier.CODEC.fieldOf("kind").forGetter(Marker::kind)
		).apply(i, Marker::new));
	}

	public static final AttachmentType<Journal> JOURNAL = AttachmentRegistry.create(Mettle.id("survival/discoveries"), b -> b
		.persistent(Journal.CODEC)
		.copyOnDeath());

	private static final Map<UUID, Long> SEARCHED_AT = new HashMap<>();

	private Discovery() {
	}

	public static void init() {
		ServerPlayerEvents.JOIN.register(Discovery::resendMarker);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> resendMarker(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> resendMarker(player));
	}

	public static Journal journal(ServerPlayer player) {
		return player.getAttachedOrElse(JOURNAL, Journal.EMPTY);
	}

	/** Test hook and admin use: forget everything this player has found (and take the marker down). */
	public static void reset(ServerPlayer player) {
		player.removeAttached(JOURNAL);
		player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(MARKER));
		SEARCHED_AT.remove(player.getUUID());
	}

	public static void check(ServerPlayer player) {
		if (player.isSpectator() || player.isCreative()) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		Journal journal = journal(player);
		Journal next = journal;
		boolean found = false;
		boolean foundPlace = false;

		Identifier biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier()).orElse(null);
		if (biome != null && !journal.biomes().contains(biome)) {
			next = next.withBiome(biome);
			found = true;
			SurvivalSkill.award(player, "biome", SurvivalRules.XP_BIOME);
			FeatCounters.add(player, SurvivalSkill.BIOMES_VISITED, 1);
			SurvivalSkill.chip(player, FEEDBACK, "discovered.biome", net.minecraft.network.chat.Component.translatable(
				net.minecraft.util.Util.makeDescriptionId("biome", biome)));
		}

		if (!level.structureManager().getAllStructuresAt(pos).isEmpty()) {
			StructureStart start = level.structureManager().getStructureWithPieceAt(pos, h -> true);
			if (start.isValid()) {
				Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				Identifier id = registry.getKey(start.getStructure());
				if (id != null) {
					String place = id + "@" + start.getChunkPos().x() + "," + start.getChunkPos().z();
					if (!journal.places().contains(place)) {
						boolean landmark = Landmarks.landmarks(registry).map(set -> contains(registry, set, start.getStructure())).orElse(false);
						boolean expanse = landmark && Landmarks.expanseLandmarks(registry);
						Identifier kind = Landmarks.kind(id);
						next = next.withPlace(place, kind, landmark);
						found = true;
						foundPlace = true;
						SurvivalSkill.award(player, "place", expanse ? SurvivalRules.XP_LANDMARK : SurvivalRules.XP_STRUCTURE);
						SurvivalSkill.chip(player, FEEDBACK, expanse ? "discovered.landmark" : "discovered.place", name(kind));
						if (landmark) {
							seenItAll(player, next.kinds().size());
						}
					}
				}
			}
		}

		// Lay of the Land: the marker comes down once you get there (or find that kind anywhere).
		if (next.marker().isPresent()) {
			Marker m = next.marker().get();
			boolean reached = m.dimension().equals(level.dimension().identifier())
				&& flatDistanceSqr(m.pos(), player.position()) < SurvivalRules.MARKER_REACHED * SurvivalRules.MARKER_REACHED;
			if (reached || next.kinds().contains(m.kind()) || !Perks.has(player, SurvivalSkill.LAY_OF_THE_LAND)) {
				next = next.withMarker(Optional.empty());
				player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(MARKER));
			}
		}

		if (found) {
			if (Perks.has(player, SurvivalSkill.TRAILBLAZER)) {
				// No particles: the effect is felt, not shown (Expanse's natural look).
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, TRAILBLAZER_TICKS, 0, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, TRAILBLAZER_TICKS, 0, true, false, true));
				SurvivalSkill.chip(player, SurvivalSkill.TRAILBLAZER, "trailblazer");
			}
			if (Perks.has(player, SurvivalSkill.LAY_OF_THE_LAND)) {
				next = mark(player, next, foundPlace);
			}
		}
		if (next != journal) {
			player.setAttached(JOURNAL, next);
		}
	}

	private static boolean contains(Registry<Structure> registry, HolderSet<Structure> set, Structure structure) {
		return set.contains(registry.wrapAsHolder(structure));
	}

	private static net.minecraft.network.chat.Component name(Identifier kind) {
		return net.minecraft.network.chat.Component.translatableWithFallback("mettle.survival.structure." + kind.getNamespace() + "." + kind.getPath(),
			Landmarks.prettify(kind.getPath()));
	}

	private static double flatDistanceSqr(BlockPos pos, Vec3 at) {
		double dx = pos.getX() + 0.5 - at.x;
		double dz = pos.getZ() + 0.5 - at.z;
		return dx * dx + dz * dz;
	}


	/** Seen It All's tally: how many kinds of landmark this player has found. */
	private static void seenItAll(ServerPlayer player, int kinds) {
		long delta = kinds - FeatCounters.get(player, SurvivalSkill.LANDMARK_KINDS);
		if (delta != 0) {
			FeatCounters.add(player, SurvivalSkill.LANDMARK_KINDS, delta);
		}
	}

	/**
	 * Lay of the Land: puts the nearest landmark of a kind not yet found on the locator bar. Finding a place always
	 * searches (places are rare and change what is left to find); new biomes search at most every ten seconds.
	 */
	private static Journal mark(ServerPlayer player, Journal journal, boolean force) {
		long now = player.level().getGameTime();
		Long last = SEARCHED_AT.get(player.getUUID());
		if (!force && last != null && now - last < SEARCH_COOLDOWN && now >= last) {
			return journal;
		}
		SEARCHED_AT.put(player.getUUID(), now);
		ServerLevel level = player.level();
		Landmarks.Found found = Landmarks.nearest(level, player.blockPosition(), journal.kinds());
		if (found == null) {
			return journal;
		}
		Marker marker = new Marker(level.dimension().identifier(), found.pos(), found.kind());
		if (journal.marker().map(m -> m.pos().equals(found.pos())).orElse(false)) {
			return journal;
		}
		send(player, marker);
		SurvivalSkill.chip(player, SurvivalSkill.LAY_OF_THE_LAND, "lay_of_the_land", name(found.kind()),
			(int) Math.sqrt(flatDistanceSqr(found.pos(), player.position())));
		return journal.withMarker(Optional.of(marker));
	}

	private static void send(ServerPlayer player, Marker marker) {
		Waypoint.Icon icon = new Waypoint.Icon();
		icon.color = Optional.of(MARKER_COLOUR);
		player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(MARKER));
		player.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(MARKER, icon, marker.pos()));
	}

	/** On join, respawn and changing dimension the client has forgotten the marker; show it again where it applies. */
	private static void resendMarker(ServerPlayer player) {
		Optional<Marker> marker = journal(player).marker();
		if (marker.isEmpty()) {
			return;
		}
		if (marker.get().dimension().equals(player.level().dimension().identifier()) && Perks.has(player, SurvivalSkill.LAY_OF_THE_LAND)) {
			send(player, marker.get());
		} else {
			player.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(MARKER));
		}
	}

	public static void forget(UUID player) {
		SEARCHED_AT.remove(player);
	}

	public static void clear() {
		SEARCHED_AT.clear();
	}
}
