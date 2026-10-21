package dev.voidmc.hearth.quest.commission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A player's service to lords ({@code hearth:quest/retinue}, persistent, copied on death; docs/LIVING_WORLD_2.md §8.2): per
 * lord's seat, their rank, the commissions done, the lord they swore to (an heir keeps the service one rank lower until
 * the player pledges again), the feat a knighthood asks, and the rights already given (the horse, the house). Immutable;
 * changed by replacing.
 */
public record Retinue(Map<Long, Service> services) {
	public static final Retinue EMPTY = new Retinue(Map.of());

	/**
	 * @param rank      their rank now
	 * @param done      commissions done for this seat
	 * @param lord      the lord they serve (the seat's lord when they last rose or were confirmed)
	 * @param since     the sim day they first asked to serve
	 * @param feat      a tournament won or a raid repelled in the lord's colours
	 * @param demotedFrom the rank they held under the old lord, while an heir keeps them one lower; empty otherwise
	 * @param horse     the lord's horse given
	 * @param house     the knight's house given
	 * @param open      the commission they are on for this seat, if any
	 */
	public record Service(Rank rank, int done, Optional<UUID> lord, long since, boolean feat, Optional<Rank> demotedFrom, boolean horse, boolean house,
		Optional<UUID> open) {
		public static final Codec<Service> CODEC = RecordCodecBuilder.create(i -> i.group(
			Rank.LENIENT.optionalFieldOf("rank", Rank.PETITIONER).forGetter(Service::rank),
			Codec.INT.optionalFieldOf("done", 0).forGetter(Service::done),
			UUIDUtil.STRING_CODEC.optionalFieldOf("lord").forGetter(Service::lord),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Service::since),
			Codec.BOOL.optionalFieldOf("feat", false).forGetter(Service::feat),
			Rank.LENIENT.optionalFieldOf("demoted_from").forGetter(Service::demotedFrom),
			Codec.BOOL.optionalFieldOf("horse", false).forGetter(Service::horse),
			Codec.BOOL.optionalFieldOf("house", false).forGetter(Service::house),
			UUIDUtil.STRING_CODEC.optionalFieldOf("open").forGetter(Service::open)
		).apply(i, Service::new));

		public static Service start(@Nullable UUID lord, long day) {
			return new Service(Rank.PETITIONER, 0, Optional.ofNullable(lord), day, false, Optional.empty(), false, false, Optional.empty());
		}

		public Service withRank(Rank r) {
			return new Service(r, this.done, this.lord, this.since, this.feat, Optional.empty(), this.horse, this.house, this.open);
		}

		public Service withDone(int d, boolean featNow) {
			return new Service(this.rank, d, this.lord, this.since, this.feat || featNow, this.demotedFrom, this.horse, this.house, Optional.empty());
		}

		public Service withOpen(@Nullable UUID quest) {
			return new Service(this.rank, this.done, this.lord, this.since, this.feat, this.demotedFrom, this.horse, this.house, Optional.ofNullable(quest));
		}

		/** The lord died and an heir holds the seat: the service is kept one rank lower until the player pledges again. */
		public Service underHeir(UUID heir) {
			Rank was = this.demotedFrom.orElse(this.rank);
			return new Service(this.rank.previous(), this.done, Optional.of(heir), this.since, this.feat, Optional.of(was), this.horse, this.house, this.open);
		}

		/** Pledged again to the heir: the rank held under the old lord comes back. */
		public Service repledged() {
			return new Service(this.demotedFrom.orElse(this.rank), this.done, this.lord, this.since, this.feat, Optional.empty(), this.horse, this.house,
				this.open);
		}

		public Service withHorse() {
			return new Service(this.rank, this.done, this.lord, this.since, this.feat, this.demotedFrom, true, this.house, this.open);
		}

		public Service withHouse() {
			return new Service(this.rank, this.done, this.lord, this.since, this.feat, this.demotedFrom, this.horse, true, this.open);
		}
	}

	public static final Codec<Retinue> CODEC = Codec.unboundedMap(Codec.STRING, Service.CODEC).xmap(m -> {
		Map<Long, Service> out = new LinkedHashMap<>();
		m.forEach((k, v) -> {
			try {
				out.put(Long.parseLong(k), v);
			} catch (NumberFormatException ignored) {
				// a damaged key is dropped
			}
		});
		return new Retinue(out);
	}, r -> {
		Map<String, Service> out = new LinkedHashMap<>();
		r.services.forEach((k, v) -> out.put(Long.toString(k), v));
		return out;
	});


	public Retinue {
		services = Map.copyOf(services);
	}

	/** The attachment, registered on first use (init, at mod start): records and codecs stay usable in tests without it. */
	public static final class Attachment {
		public static final AttachmentType<Retinue> TYPE = AttachmentRegistry.create(Hearth.id("quest/retinue"), b -> b
			.persistent(CODEC)
			.initializer(() -> EMPTY)
			.copyOnDeath());

		private Attachment() {
		}
	}

	public static void init() {
		Hearth.LOG.debug("player attachment {}", Attachment.TYPE.identifier());
	}

	public static Retinue of(Player player) {
		Retinue r = player.getAttached(Attachment.TYPE);
		return r == null ? EMPTY : r;
	}

	public static void set(ServerPlayer player, Retinue retinue) {
		player.setAttached(Attachment.TYPE, retinue);
	}

	public @Nullable Service service(long seat) {
		return this.services.get(seat);
	}

	public Retinue with(long seat, Service s) {
		Map<Long, Service> m = new LinkedHashMap<>(this.services);
		m.put(seat, s);
		return new Retinue(m);
	}
}
