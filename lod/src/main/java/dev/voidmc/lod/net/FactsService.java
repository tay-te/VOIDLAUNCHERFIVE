package dev.voidmc.lod.net;

import dev.voidmc.expanse.world.terrain.EarthChunkGenerator;
import dev.voidmc.expanse.world.terrain.TerrainModel;
import dev.voidmc.lod.mixin.MaterialSystemAccessor;
import dev.voidmc.lod.source.ExpanseFacts;
import dev.voidmc.lod.source.TreeKinds;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's side of VOID LOD: on an Expanse world it tells joining clients that run VOID LOD it can describe the land
 * past their view, and answers their tile requests with what its terrain model and biome source say there
 * ({@link LodNet.TileFacts}), worked out on two threads at minimum priority, never on the server's own. Each player
 * gets a bounded queue: a client asking for more than the server keeps up with waits, and asks again. Only loaded
 * with Expanse installed. Server classes only: it runs on a dedicated server, which has no client classes.
 */
public final class FactsService {
	private static final Logger LOGGER = LoggerFactory.getLogger("VOID LOD");
	/** Tiles a player may have waiting; past it requests are dropped, and asked again when the client still wants them. */
	private static final int MAX_QUEUED = 96;
	private static final int MAX_SIZE = 80;
	private static final ExecutorService WORK = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "VOID LOD server facts");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private static final Map<UUID, AtomicInteger> QUEUED = new ConcurrentHashMap<>();
	public static final AtomicLong TILES = new AtomicLong();

	private FactsService() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> hello(handler.player, handler.player.level()));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> hello(player, to));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QUEUED.remove(handler.player.getUUID()));
		ServerPlayNetworking.registerGlobalReceiver(LodNet.TileRequest.TYPE, (request, context) -> request(request, context.player()));
	}

	/** Offers the player's client the land of {@code level}, if it is Expanse's and the client listens. */
	private static void hello(ServerPlayer player, ServerLevel level) {
		if (!(level.getChunkSource().getGenerator() instanceof EarthChunkGenerator) || !ServerPlayNetworking.canSend(player, LodNet.Hello.TYPE)) {
			return;
		}
		List<String> strata = new ArrayList<>();
		try {
			for (BlockState band : ((MaterialSystemAccessor) level.getChunkSource().randomState().surfaceSystem()).voidLod$clayBands()) {
				strata.add(BlockStateParser.serialize(band));
			}
		} catch (RuntimeException e) {
			// no strata: the client draws its looks' own
		}
		ServerPlayNetworking.send(player, new LodNet.Hello(LodNet.VERSION, level.dimension().identifier().toString(),
			level.getChunkSource().getGenerator().getSeaLevel(), strata, TREES.computeIfAbsent(level, FactsService::trees)));
	}

	/** The trees of each biome, worked out once a level: the same for every player. */
	private static final Map<ServerLevel, Map<String, List<TreeKinds.Kind>>> TREES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	private static Map<String, List<TreeKinds.Kind>> trees(ServerLevel level) {
		Map<String, List<TreeKinds.Kind>> trees = new HashMap<>();
		for (var biome : level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).listElements().toList()) {
			trees.put(biome.key().identifier().toString(), TreeKinds.of(biome.value(), level));
		}
		return trees;
	}

	private static void request(LodNet.TileRequest r, ServerPlayer player) {
		if (r.size() <= 0 || r.size() > MAX_SIZE || r.cell() <= 0 || r.cell() > 512 || Integer.bitCount(r.cell()) != 1) {
			return;
		}
		AtomicInteger queued = QUEUED.computeIfAbsent(player.getUUID(), u -> new AtomicInteger());
		if (queued.incrementAndGet() > MAX_QUEUED) {
			queued.decrementAndGet();
			return;
		}
		ServerLevel level = player.level();
		var server = player.level().getServer();
		WORK.execute(() -> {
			try {
				byte[] data = level.getChunkSource().getGenerator() instanceof EarthChunkGenerator earth ? facts(level, earth, r) : new byte[0];
				TILES.incrementAndGet();
				server.execute(() -> {
					if (!player.hasDisconnected()) {
						ServerPlayNetworking.send(player, new LodNet.TileFacts(r.key(), r.size(), data));
					}
				});
			} catch (RuntimeException e) {
				LOGGER.warn("VOID LOD: could not describe tile {} for {}", r.key(), player.getName().getString(), e);
			} finally {
				queued.decrementAndGet();
			}
		});
	}

	/** Per worker: the biome resolver (the caching one keeps state) and the facts of the column at hand. */
	private static final class Worker {
		ServerLevel level;
		BiomeResolver resolver;
		final ExpanseFacts facts = new ExpanseFacts();
	}

	private static final ThreadLocal<Worker> WORKERS = ThreadLocal.withInitial(Worker::new);

	/** The facts of request {@code r}'s grid, as {@link LodNet.TileFacts} carries them. */
	private static byte[] facts(ServerLevel level, EarthChunkGenerator earth, LodNet.TileRequest r) {
		Worker w = WORKERS.get();
		var randomState = level.getChunkSource().randomState();
		if (w.level != level) {
			w.level = level;
			w.resolver = earth.getBiomeSource().createCachingResolver(randomState);
		}
		TerrainModel model = earth.model(randomState);
		int seaLevel = earth.getSeaLevel();
		Noise bands = null;
		try {
			bands = ((MaterialSystemAccessor) randomState.surfaceSystem()).voidLod$clayBandsOffsetNoise();
		} catch (RuntimeException e) {
			// no strata
		}
		int n = r.size() * r.size();
		Map<Holder<Biome>, Integer> biomeIndex = new HashMap<>();
		List<String> biomeNames = new ArrayList<>();
		ByteBuffer body = ByteBuffer.allocate(n * (ExpanseFacts.BYTES + 3));
		// far out the samples of each square two cells across share one biome lookup, as the singleplayer source does
		Map<Long, Holder<Biome>> shared = new HashMap<>();
		for (int j = 0; j < r.size(); j++) {
			for (int i = 0; i < r.size(); i++) {
				int x = r.x0() + (i - r.border()) * r.cell();
				int z = r.z0() + (j - r.border()) * r.cell();
				ExpanseFacts f = w.facts.of(model.sample(x, z));
				f.write(body);
				f.geometry(r.cell(), seaLevel);
				int y = f.biomeY(seaLevel);
				Holder<Biome> biome;
				if (r.cell() >= 8) {
					long square = (long) Math.floorDiv(x, 2 * r.cell()) << 32 ^ Math.floorDiv(z, 2 * r.cell()) & 0xFFFFFFFFL;
					biome = shared.get(square);
					if (biome == null) {
						biome = w.resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
						shared.put(square, biome);
					}
				} else {
					biome = w.resolver.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
				}
				Integer index = biomeIndex.get(biome);
				if (index == null) {
					index = biomeNames.size();
					biomeNames.add(biome.unwrapKey().map(k -> k.identifier().toString()).orElse(""));
					biomeIndex.put(biome, index);
				}
				body.putShort(index.shortValue());
				body.put((byte) (bands == null ? 0 : Math.round((float) bands.get(x, 0, z) * 4)));
			}
		}
		ByteArrayOutputStream raw = new ByteArrayOutputStream();
		java.io.DataOutputStream out = new java.io.DataOutputStream(raw);
		try {
			out.writeShort(biomeNames.size());
			for (String name : biomeNames) {
				out.writeUTF(name);
			}
			out.write(body.array(), 0, body.position());
		} catch (java.io.IOException e) {
			throw new IllegalStateException(e);
		}
		Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try {
			deflater.setInput(raw.toByteArray());
			deflater.finish();
			ByteArrayOutputStream zipped = new ByteArrayOutputStream();
			byte[] chunk = new byte[16384];
			while (!deflater.finished()) {
				zipped.write(chunk, 0, deflater.deflate(chunk));
			}
			return zipped.toByteArray();
		} finally {
			deflater.end();
		}
	}
}
