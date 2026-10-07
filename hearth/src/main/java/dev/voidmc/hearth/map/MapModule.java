package dev.voidmc.hearth.map;

import com.mojang.serialization.Codec;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The map module: which settlements each player knows of (sighted from afar, then discovered by walking up to them),
 * kept with the player ({@code hearth:map/known}), and the list of them sent to the player's client for a map (VOID Atlas,
 * see docs/ARCHITECTURE.md §11). Each player is looked at every couple of seconds, staggered; the list is sent only when
 * it changed, and only to clients that asked for it (those with Atlas). Server side only; touches nothing of Atlas.
 */
public final class MapModule {
	/**
	 * @param sight    blocks from a settlement's bounds within which it is sighted (a "?" on the map)
	 * @param reach    blocks from its bounds within which it is discovered (Atlas discovers its own places at 24)
	 * @param interval ticks between looks at each player
	 */
	public record Config(boolean enabled, int sight, int reach, int interval) {
		public static final Config DEFAULTS = new Config(true, 160, 24, 40);
		public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("enabled", DEFAULTS.enabled).forGetter(Config::enabled),
			Codec.intRange(0, 1024).optionalFieldOf("sight_radius", DEFAULTS.sight).forGetter(Config::sight),
			Codec.intRange(0, 256).optionalFieldOf("discover_radius", DEFAULTS.reach).forGetter(Config::reach),
			Codec.intRange(1, 1200).optionalFieldOf("interval_ticks", DEFAULTS.interval).forGetter(Config::interval)
		).apply(i, Config::new));
	}

	/** The attachment, in its own class so {@link MapKnowledge} loads without Fabric (tests). */
	private static final class Known {
		static final AttachmentType<MapKnowledge> TYPE = AttachmentRegistry.create(Hearth.id("map/known"),
			b -> b.persistent(MapKnowledge.CODEC).copyOnDeath().initializer(() -> MapKnowledge.EMPTY));
	}

	private static Config config = Config.DEFAULTS;
	/** The last list sent to each player (its hash), so an unchanged list is not sent again. */
	private static final Map<UUID, Integer> SENT = new HashMap<>();

	private MapModule() {
	}

	public static void init() {
		config = HearthConfig.load("map", Config.CODEC, Config.DEFAULTS);
		Hearth.LOG.debug("map knowledge attachment {}", Known.TYPE.identifier());
		PayloadTypeRegistry.clientboundPlay().register(MapPayload.TYPE, MapPayload.CODEC);
		if (!config.enabled()) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(MapModule::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SENT.clear());
	}

	public static MapKnowledge knowledge(ServerPlayer player) {
		return player.getAttachedOrElse(Known.TYPE, MapKnowledge.EMPTY);
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

	/** Notes the settlements the player is near, and sends their list if it changed and their client wants it. */
	static void look(ServerPlayer player) {
		HearthWorld w = HearthWorld.of(player.level());
		if (w == null) {
			return; // somewhere Hearth does not run: what the client has stays as it was
		}
		int px = player.getBlockX();
		int pz = player.getBlockZ();
		MapKnowledge known = knowledge(player);
		MapKnowledge now = known;
		if (!player.isSpectator()) {
			for (Settlement s : w.settlements()) {
				now = now.seen(s.id(), distance(s.bounds(), px, pz), config.reach(), config.sight());
			}
		}
		if (now != known) {
			player.setAttached(Known.TYPE, now);
		}
		if (!ServerPlayNetworking.canSend(player, MapPayload.TYPE)) {
			return;
		}
		MapPayload payload = payload(w, player, now);
		int hash = payload.hashCode();
		Integer last = SENT.put(player.getUUID(), hash);
		if (last == null || last != hash) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static MapPayload payload(HearthWorld w, ServerPlayer player, MapKnowledge known) {
		List<Settlement> list = new ArrayList<>();
		for (Settlement s : w.settlements()) {
			if (known.knows(s.id())) {
				list.add(s);
			}
		}
		int px = player.getBlockX();
		int pz = player.getBlockZ();
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
			BoundingBox b = s.bounds();
			int radius = Math.max(8, Math.max(b.getXSpan(), b.getZSpan()) / 2);
			if (known.discovered(s.id())) {
				int st = standing == null ? s.reputation(me) : standing.standing(s.id(), me);
				String title = standing == null ? "" : standing.title(s.id(), me).getSerializedName();
				entries.add(new MapPayload.Entry(s.id(), true, s.name(), s.tier().getSerializedName(), s.culture().getSerializedName(), s.population(),
					s.center().getX(), s.center().getY(), s.center().getZ(), dim, radius, st, title, owns(s, me)));
			} else {
				entries.add(new MapPayload.Entry(s.id(), false, "", s.tier().getSerializedName(), "", 0, s.center().getX(), s.center().getY(),
					s.center().getZ(), dim, radius, 0, "", false));
			}
		}
		return new MapPayload(entries);
	}

	private static boolean owns(Settlement s, UUID player) {
		for (var plot : s.plots()) {
			if (player.equals(plot.owner())) {
				return true;
			}
		}
		return false;
	}

	/** Horizontal distance from a column to a box (0 inside). */
	static int distance(BoundingBox b, int x, int z) {
		int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
		int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
		return (int) Math.sqrt((double) dx * dx + (double) dz * dz);
	}
}
