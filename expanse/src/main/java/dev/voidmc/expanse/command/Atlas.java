package dev.voidmc.expanse.command;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * A map of the world as the generator sees it, without generating a single chunk: each pixel asks the
 * biome source which biome is there and the chunk generator how high the ground would stand, then
 * shades the biome's colour by height and slope. It is how this mod's biome placement and Earth terrain
 * terrain were tuned, and it is left in as {@code /expanse atlas} for anyone tuning a world of their own.
 */
public final class Atlas {
	/** Map colours for the biomes worth telling apart at a glance; anything else gets a stable hash colour. */
	private static final Map<String, Integer> COLOURS = new HashMap<>();

	static {
		Object[][] c = {
			{"expanse:frostbloom_tundra", 0xBFE3F0}, {"expanse:heather_moor", 0x9A6FB0}, {"expanse:wisteria_vale", 0xC596F0},
			{"expanse:redwood_giants", 0x8E3B2A}, {"expanse:lumen_grove", 0x2FE0C8}, {"expanse:willow_bayou", 0x5E8A3C},
			{"expanse:amber_steppe", 0xE0B040}, {"expanse:opal_dunes", 0xF2B6D2}, {"expanse:jade_karst", 0x3FBF8F},
			{"expanse:cloud_forest", 0x9FC9B0}, {"expanse:verdant_peaks", 0x7FD060}, {"expanse:prismatic_peaks", 0x9FB8FF},
			{"expanse:palm_coast", 0xFFE79A},
			// cold & temperate biomes
			{"expanse:maple_highlands", 0xC8361E}, {"expanse:aspen_parkland", 0xE8D24A}, {"expanse:larch_taiga", 0xD8A040},
			{"expanse:boreal_muskeg", 0x7A6A3A}, {"expanse:bluebell_woods", 0x5A6AE0}, {"expanse:pine_heath", 0xA8B898},
			{"minecraft:ocean", 0x2850A0}, {"minecraft:deep_ocean", 0x1A3A80}, {"minecraft:cold_ocean", 0x2A4A90},
			{"minecraft:deep_cold_ocean", 0x1A3070}, {"minecraft:lukewarm_ocean", 0x2A60B0}, {"minecraft:deep_lukewarm_ocean", 0x1F4A96},
			{"minecraft:warm_ocean", 0x2A78C0}, {"minecraft:frozen_ocean", 0x7A90C0}, {"minecraft:deep_frozen_ocean", 0x50709C},
			{"minecraft:river", 0x3A6AD0}, {"minecraft:frozen_river", 0x9AB0E0}, {"minecraft:beach", 0xE8DCA0},
			{"minecraft:snowy_beach", 0xEAF0F0}, {"minecraft:stony_shore", 0x8A8A8A}, {"minecraft:plains", 0x8DB360},
			{"minecraft:sunflower_plains", 0xB0C060}, {"minecraft:forest", 0x3E7A2A}, {"minecraft:birch_forest", 0x5E9A4A},
			{"minecraft:dark_forest", 0x2A4A1A}, {"minecraft:taiga", 0x2E6A50}, {"minecraft:snowy_taiga", 0x6A8A80},
			{"minecraft:snowy_plains", 0xF0F6F8}, {"minecraft:desert", 0xF0D890}, {"minecraft:savanna", 0xBDB25F},
			{"minecraft:jungle", 0x2A9A1A}, {"minecraft:swamp", 0x4A6A40}, {"minecraft:mangrove_swamp", 0x3A7A50},
			{"minecraft:badlands", 0xD06A2A}, {"minecraft:meadow", 0x9AD07A}, {"minecraft:cherry_grove", 0xF0A8C8},
			{"minecraft:jagged_peaks", 0xD8E0E8}, {"minecraft:frozen_peaks", 0xC8D8F0}, {"minecraft:stony_peaks", 0x9A9A9A},
			{"minecraft:snowy_slopes", 0xE0E8F0}, {"minecraft:grove", 0x8AB0A0}, {"minecraft:mushroom_fields", 0xB070B0},
			{"minecraft:dappled_forest", 0xD07A30}, {"minecraft:pale_garden", 0xB8B8B0},
		};
		for (Object[] e : c) {
			COLOURS.put((String) e[0], (Integer) e[1]);
		}
	}

	public record Result(Path image, Map<String, Integer> counts, int samples, int minHeight, int maxHeight) {
	}

	private Atlas() {
	}

	/**
	 * @param radius half-width of the map in blocks
	 * @param step   blocks per pixel
	 */
	public static Result render(ServerLevel level, int centreX, int centreZ, int radius, int step) throws IOException {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		BiomeResolver biomes = generator.getBiomeSource().createUncachedResolver(random);
		int size = radius * 2 / step;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		int[][] heights = new int[size][size];
		String[][] ids = new String[size][size];
		Map<String, Integer> counts = new TreeMap<>();
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int x = centreX - radius + px * step;
				int z = centreZ - radius + pz * step;
				int h = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random);
				Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(h, 63)), QuartPos.fromBlock(z));
				String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				heights[px][pz] = h;
				ids[px][pz] = id;
				counts.merge(id, 1, Integer::sum);
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
		}
		for (int px = 0; px < size; px++) {
			for (int pz = 0; pz < size; pz++) {
				int h = heights[px][pz];
				int base = COLOURS.getOrDefault(ids[px][pz], 0x404040 | (ids[px][pz].hashCode() & 0x7F7F7F));
				// Hillshade from the north-west, plus a gentle lift with altitude.
				int hw = heights[Math.max(0, px - 1)][Math.max(0, pz - 1)];
				float shade = 1.0F + Math.max(-0.45F, Math.min(0.45F, (h - hw) / (float) step * 0.6F));
				shade *= 0.8F + 0.45F * Math.max(0, Math.min(1, (h - 40) / 280.0F));
				if (h < 62) {
					shade *= 0.75F + 0.25F * Math.max(0, (h + 20) / 82.0F);
				}
				img.setRGB(px, pz, scale(base, shade));
			}
		}
		Path dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("expanse_atlas");
		Files.createDirectories(dir);
		Path file = dir.resolve("atlas_" + centreX + "_" + centreZ + "_r" + radius + ".png");
		ImageIO.write(img, "png", file.toFile());
		return new Result(file, counts, size * size, min, max);
	}

	public static Map<String, String> summarise(Result r) {
		Map<String, String> out = new LinkedHashMap<>();
		r.counts().entrySet().stream()
			.sorted((a, b) -> b.getValue() - a.getValue())
			.forEach(e -> out.put(e.getKey(), String.format("%.2f%%", 100.0 * e.getValue() / r.samples())));
		return out;
	}

	private static int scale(int rgb, float f) {
		int r = Math.min(255, Math.max(0, Math.round(((rgb >> 16) & 0xFF) * f)));
		int g = Math.min(255, Math.max(0, Math.round(((rgb >> 8) & 0xFF) * f)));
		int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * f)));
		return r << 16 | g << 8 | b;
	}
}
