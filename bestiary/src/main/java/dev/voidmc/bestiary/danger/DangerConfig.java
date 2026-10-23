package dev.voidmc.bestiary.danger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.voidmc.bestiary.Bestiary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server config for danger and difficulty ({@code config/bestiary.json}, written with the defaults on first start).
 * Every ring distance and step of BESTIARY.md §6 lives here.
 */
public final class DangerConfig {
	/** Ring distances from world spawn in blocks (each adds 1 danger). */
	public double[] rings = {2000, 5000, 10000};
	/** Blocks over which each ring eases in. */
	public double ringBlend = 600;
	public int tunnelLow = 12;
	public int tunnelHigh = 44;
	public float night = 0.5F;
	public float netherMin = 4;
	public float endMin = 5;
	/** Mythic (server option): danger +1 everywhere, elites twice as common, +1 token. */
	public boolean mythic = false;
	/** At most one elite within this many blocks. */
	public int eliteSpacing = 64;
	/** Danger from which an elite may lead a champion pack, and the chance it does. */
	public float packFrom = 3;
	public float packChance = 0.5F;
	/** Fixed danger everywhere for tests and showcases (0: off). */
	public float override = 0;

	private static DangerConfig current = new DangerConfig();

	public static DangerConfig get() {
		return current;
	}

	public static void load() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("bestiary.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(file)) {
				DangerConfig c = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), DangerConfig.class);
				if (c != null) {
					current = c;
				}
			} else {
				Files.createDirectories(file.getParent());
				Files.writeString(file, gson.toJson(current), StandardCharsets.UTF_8);
			}
		} catch (IOException | RuntimeException e) {
			Bestiary.LOGGER.warn("config/bestiary.json unreadable, using defaults: {}", e.toString());
		}
	}
}
