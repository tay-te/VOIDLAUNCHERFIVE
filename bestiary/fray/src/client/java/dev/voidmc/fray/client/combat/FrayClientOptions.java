package dev.voidmc.fray.client.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.voidmc.fray.Fray;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The player's combat HUD and accessibility options (COMBAT.md §15; Figma P1-b, P1-c), in
 * {@code config/fray-combat-client.json}. The Hub's Combat settings page edits the same file.
 */
public final class FrayClientOptions {
	public enum Nameplates {
		HURT_AND_ELITES, ELITES, OFF
	}

	public enum StaminaPlacement {
		BESIDE, ABOVE
	}

	/** Fray's HUD on (off: vanilla hearts, effects and boss bars). */
	public boolean hud = true;
	public boolean damageNumbers = false;
	public Nameplates nameplates = Nameplates.HURT_AND_ELITES;
	/** Shape-coded cues and edge flash for red and purple tells. */
	public boolean telegraphCues = true;
	/** Also cue white and orange tells. */
	public boolean cueAllColours = false;
	public boolean hitMarkers = true;
	public boolean flashReduction = false;
	public StaminaPlacement stamina = StaminaPlacement.BESIDE;
	public float hudScale = 1.0F;
	public boolean lockOnEase = true;
	public boolean autoLockOn = false;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FrayClientOptions instance;

	public static FrayClientOptions get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("fray-combat-client.json");
	}

	private static FrayClientOptions load() {
		try {
			Path f = file();
			if (Files.exists(f)) {
				FrayClientOptions o = GSON.fromJson(Files.readString(f), FrayClientOptions.class);
				if (o != null) {
					return o;
				}
			}
		} catch (Exception e) {
			Fray.LOGGER.warn("couldn't read {}: {}", file(), e.toString());
		}
		FrayClientOptions o = new FrayClientOptions();
		o.save();
		return o;
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this));
		} catch (Exception e) {
			Fray.LOGGER.warn("couldn't write {}: {}", file(), e.toString());
		}
	}
}
