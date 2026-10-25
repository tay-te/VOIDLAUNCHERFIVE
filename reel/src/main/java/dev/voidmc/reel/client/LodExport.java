package dev.voidmc.reel.client;

import dev.voidmc.reel.Reel;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/**
 * VOID LOD's far land around a point, for Blender (tools/blender/reel_land.py, build_lod): every far tile it draws for
 * this player (LodApi.addVisibleTerrainListener, by reflection: Reel does not depend on LOD), written once LOD says
 * the far view is complete. Each cell's height (the water's surface where wet, trees included), its colour as drawn
 * (biome tints, snow, water mixed in) and its top block: the real land to the real horizon, as the game shows it.
 * Of two pictures of the same ground the newest wins. LOD draws nothing where the client has chunks, so a small view
 * distance leaves the least gap around the player.
 *
 * <p>renders/_lod/NAME: tiles.json {"centre", "radius", "tiles": [{"minX", "minZ", "cell", "size", "revision",
 * "offset", "palette": [block ids]}]} and tiles.bin, per tile at its offset: size x size heights (int16 big-endian,
 * -32768 for none), colours (3 bytes each), top blocks (an int8 index into the palette, -1 for none); rows of x.
 */
final class LodExport {
	private LodExport() {
	}

	static String export(String name, double cx, double cz, double radius, int waitSeconds) throws Exception {
		Class<?> api;
		Class<?> listenerType;
		try {
			api = Class.forName("dev.voidmc.lod.api.LodApi");
			listenerType = Class.forName("dev.voidmc.lod.api.VisibleTerrainListener");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException("VOID LOD (with its visible terrain API) is not loaded");
		}
		if (!(Boolean) api.getMethod("active").invoke(null)) {
			throw new IllegalStateException("VOID LOD is not drawing in this level");
		}
		Map<String, Object> tiles = new ConcurrentHashMap<>();
		Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] {listenerType}, (proxy, method, args) -> {
			switch (method.getName()) {
				case "onTerrain" -> {
					Object tile = args[0];
					String key = get(tile, "minX") + "," + get(tile, "minZ") + "," + get(tile, "cell");
					tiles.merge(key, tile, (old, now) -> (long) get(now, "revision") >= (long) get(old, "revision") ? now : old);
					return null;
				}
				case "hashCode" -> {
					return System.identityHashCode(proxy);
				}
				case "equals" -> {
					return proxy == args[0];
				}
				default -> {
					return "Reel LOD export";
				}
			}
		});
		Method complete = api.getMethod("complete");
		long start = System.nanoTime();
		long deadline = start + waitSeconds * 1_000_000_000L;
		try (AutoCloseable handle = (AutoCloseable) api.getMethod("addVisibleTerrainListener", listenerType).invoke(null, listener)) {
			// the tiles already drawn are built again to be handed over: give that a moment before trusting complete()
			Thread.sleep(3000);
			while (!(Boolean) complete.invoke(null) && System.nanoTime() < deadline) {
				Thread.sleep(500);
			}
			Thread.sleep(500);
		}
		boolean finished = (Boolean) complete.invoke(null);
		List<Object> kept = new ArrayList<>();
		for (Object tile : tiles.values()) {
			int minX = (int) get(tile, "minX");
			int minZ = (int) get(tile, "minZ");
			int span = (int) get(tile, "cell") * (int) get(tile, "size");
			double nx = Math.max(minX, Math.min(cx, minX + span));
			double nz = Math.max(minZ, Math.min(cz, minZ + span));
			if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= radius * radius) {
				kept.add(tile);
			}
		}
		kept.sort(Comparator.comparingLong(t -> (long) get(t, "revision")));
		Path out = Reel.renders().resolve("_lod").resolve(name);
		Files.createDirectories(out);
		long cells = 0;
		long offset = 0;
		StringBuilder index = new StringBuilder();
		try (DataOutputStream bin = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out.resolve("tiles.bin")), 1 << 20))) {
			for (Object tile : kept) {
				int size = (int) get(tile, "size");
				short[] heights = (short[]) get(tile, "heights");
				int[] colours = (int[]) get(tile, "colors");
				byte[] blocks = (byte[]) get(tile, "blocks");
				BlockState[] palette = (BlockState[]) get(tile, "blockPalette");
				for (short h : heights) {
					bin.writeShort(h);
				}
				for (int c : colours) {
					bin.writeByte(c >> 16);
					bin.writeByte(c >> 8);
					bin.writeByte(c);
				}
				bin.write(blocks);
				StringBuilder names = new StringBuilder();
				for (int i = 0; i < palette.length; i++) {
					names.append(i == 0 ? "" : ",").append('"').append(BuiltInRegistries.BLOCK.getKey(palette[i].getBlock())).append('"');
				}
				index.append(index.isEmpty() ? "" : ",\n").append(String.format(Locale.ROOT,
					"{\"minX\":%d,\"minZ\":%d,\"cell\":%d,\"size\":%d,\"revision\":%d,\"offset\":%d,\"palette\":[%s]}", (int) get(tile, "minX"),
					(int) get(tile, "minZ"), (int) get(tile, "cell"), size, (long) get(tile, "revision"), offset, names));
				offset += (long) size * size * 6;
				cells += (long) size * size;
			}
		}
		String meta = String.format(Locale.ROOT, "{\"name\":\"%s\",\"centre\":[%.1f,%.1f],\"radius\":%.0f,\"complete\":%b,\"tiles\":%d,\"cells\":%d,\"seconds\":%.1f}",
			Report.escape(name), cx, cz, radius, finished, kept.size(), cells, (System.nanoTime() - start) / 1e9);
		try (Writer w = Files.newBufferedWriter(out.resolve("tiles.json"), StandardCharsets.UTF_8)) {
			w.write(meta.substring(0, meta.length() - 1) + ",\"list\":[\n" + index + "\n]}");
		}
		return "{\"lod\":\"" + Report.escape(out.toString()) + "\",\"meta\":" + meta + "}";
	}

	private static Object get(Object record, String component) {
		try {
			return record.getClass().getMethod(component).invoke(record);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("VOID LOD's tile has no " + component + ": " + e);
		}
	}
}
