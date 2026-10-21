package dev.voidmc.hearth.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.HearthConfig;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The map module: the settlements round each player, sent to the player's client for a map (VOID Atlas, see
 * docs/ARCHITECTURE.md §11), which decides there which of them the player has sighted and discovered and keeps that with
 * its own map of the world. Each player is looked at every couple of seconds, staggered; the list (those within
 * {@code sync_radius} blocks, with the player's own standing in each) is sent only when it changed, and only to clients
 * that asked for it (those with Atlas). Server side only; touches nothing of Atlas.
 */
public final class MapModule {
	/**
	 * @param radius   blocks from the player within which a settlement (its centre, less its size) is sent: well past
	 *                 any view distance, so the map can sight it from as far as the player sees
	 * @param interval ticks between looks at each player
	 */
	public record Config(boolean enabled, int radius, int interval) {
		public static final Config DEFAULTS = new Config(true, 1024, 40);
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Config::enabled),
			Codec.intRange(64, 8192).optionalFieldOf("sync_radius", DEFAULTS.radius).forGetter(Config::radius),
			Codec.intRange(1, 1200).optionalFieldOf("interval_ticks", DEFAULTS.interval).forGetter(Config::interval)
		).apply(i, Config::new));
	}

	/**
	 * What older Hearth kept per player ({@code hearth:map/known}: the settlements sighted and discovered, before Atlas
	 * judged that itself). Still registered so old saves load without a warning; read as is and dropped on joining.
	 */
	private static final class Legacy {
		static final AttachmentType<Dynamic<?>> KNOWN = AttachmentRegistry.create(Hearth.id("map/known"), b -> b.persistent(Codec.PASSTHROUGH));
	}

	private static Config config = Config.DEFAULTS;
	/** The last list sent to each player (its hash), so an unchanged list is not sent again. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();

	private MapModule() {
	}

	public static void init() {
		config = HearthConfig.load("map", Config.CODEC, Config.DEFAULTS);
		// registered now (not on first use): player data is read before the join event below
		Hearth.LOG.debug("legacy map attachment {}", Legacy.KNOWN.identifier());
		PayloadTypeRegistry.clientboundPlay().register(MapPayload.TYPE, MapPayload.CODEC);
		ServerPlayerEvents.JOIN.register(player -> {
			if (player.hasAttached(Legacy.KNOWN)) {
				player.removeAttached(Legacy.KNOWN);
			}
		});
		if (!config.enabled()) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(MapModule::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
	}

	private static void tick(MinecraftServer server) {
		int interval = config.interval();
		long t = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if ((t + (p.getUUID().hashCode() & 0x7FFFFFFF)) % interval == 0) {
				look(p);
			}
		}
	}

	/** Sends the list of settlements round the player if it changed and their client wants it. */
	static void look(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, MapPayload.TYPE)) {
			return;
		}
		HearthWorld w = HearthWorld.of(player.level());
		if (w == null) {
			return; // somewhere Hearth does not run: what the client has stays as it was
		}
		MapPayload payload = payload(w, player);
		int hash = payload.hashCode();
		Integer last = SENT.put(player.getUUID(), hash);
		if (last == null || last != hash) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static MapPayload payload(HearthWorld w, ServerPlayer player) {
		int px = player.getBlockX();
		int pz = player.getBlockZ();
		List<Settlement> list = new ArrayList<>();
		for (Settlement s : w.settlements()) {
			if (near(s.center().getX(), s.center().getZ(), radius(s.bounds()), px, pz, config.radius())) {
				list.add(s);
			}
		}
		if (list.size() > MapPayload.MAX) {
			list.sort(Comparator.comparingLong(s -> {
				long dx = s.center().getX() - px;
				long dz = s.center().getZ() - pz;
				return dx * dx + dz * dz;
			}));
			list = list.subList(0, MapPayload.MAX);
		}
		list.sort(Comparator.comparingLong(Settlement::id));
		StandingService standing = HearthServices.standing();
		UUID me = player.getUUID();
		String dim = w.level().dimension().identifier().toString();
		List<MapPayload.Entry> entries = new ArrayList<>(list.size());
		for (Settlement s : list) {
			int st = standing == null ? s.reputation(me) : standing.standing(s.id(), me);
			String title = standing == null ? "" : standing.title(s.id(), me).getSerializedName();
			// a post in the wild goes to Atlas as a place of its own kind, hearth:post/<type> (docs/LIVING_WORLD_2.md §7.6)
			String tier = s.kind() == dev.voidmc.hearth.api.SettlementKind.POST ? "post/" + s.postType() : s.tier().getSerializedName();
			entries.add(new MapPayload.Entry(s.id(), s.name(), tier, s.culture().getSerializedName(), s.population(),
				s.center().getX(), s.center().getY(), s.center().getZ(), dim, radius(s.bounds()), st, title, owns(s, me)));
		}
		return new MapPayload(entries);
	}

	/** Roughly how far a settlement reaches from its centre. */
	static int radius(BoundingBox b) {
		return Math.max(8, Math.max(b.getXSpan(), b.getZSpan()) / 2);
	}

	/** Whether a settlement centred at (cx, cz) reaching {@code reach} blocks lies within {@code radius} of (px, pz). */
	static boolean near(int cx, int cz, int reach, int px, int pz, int radius) {
		long dx = cx - px;
		long dz = cz - pz;
		long r = (long) radius + reach;
		return dx * dx + dz * dz <= r * r;
	}

	private static boolean owns(Settlement s, UUID player) {
		for (var plot : s.plots()) {
			if (player.equals(plot.owner())) {
				return true;
			}
		}
		return false;
	}
}
