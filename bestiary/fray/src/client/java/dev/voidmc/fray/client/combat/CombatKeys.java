package dev.voidmc.fray.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.voidmc.fray.Fray;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Fray's combat keys (ROADMAP D2): Left Alt dodge (fires on Alt pressed and released alone), C technique (sneak + C:
 * the Art; ignored with a hotbar number or F3 held), middle mouse lock-on. Ordinary key mappings, rebindable in
 * Controls; the lone-key and chord rules live in {@link CombatInput#onKey}. Attack and Guard are vanilla's attack and
 * use keys. Conflicts with any other key mapping are found at start, logged and kept for the Hub's Combat settings
 * page (P1-a).
 */
public final class CombatKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Fray.id("combat"));
	public static KeyMapping dodge;
	public static KeyMapping technique;
	public static KeyMapping lockOn;

	/** "Fray key ↔ other key (key name)" lines found at start. */
	public static final List<String> CONFLICTS = new ArrayList<>();
	private static boolean checked;

	private CombatKeys() {
	}

	public static void register() {
		dodge = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fray.dodge", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, CATEGORY));
		technique = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fray.technique", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));
		lockOn = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.fray.lock_on", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_MIDDLE, CATEGORY));
	}

	/** Finds every other key mapping on the same key as a Fray key (once, after the options load). */
	public static void checkConflicts(Minecraft mc) {
		if (checked || mc.options == null) {
			return;
		}
		checked = true;
		CONFLICTS.clear();
		for (KeyMapping mine : new KeyMapping[] {dodge, technique, lockOn}) {
			for (KeyMapping other : mc.options.keyMappings) {
				if (other != mine && !other.isUnbound() && other.same(mine)) {
					CONFLICTS.add(mine.getName() + " <-> " + other.getName() + " (" + mine.getTranslatedKeyMessage().getString() + ")");
				}
			}
		}
		for (String c : CONFLICTS) {
			Fray.LOGGER.info("Fray combat key conflict: {}", c);
		}
	}
}
