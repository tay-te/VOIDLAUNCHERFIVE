package dev.voidmc.atlas.client.ui;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * A player's pixel face (atlas/face: the 8 x 8 head of their skin, with the hat layer), for friends' markers, rows and
 * cards. The skin is the one the server sent for a player in the same world; otherwise the default skin their profile
 * id picks (no skin is ever fetched from anywhere by Atlas).
 */
public final class Faces {
	/** The friends' ring colour (#3DD68C). */
	public static final int RING = 0xFF3DD68C;

	private Faces() {
	}

	private static PlayerSkin skin(@Nullable UUID profile) {
		Minecraft mc = Minecraft.getInstance();
		if (profile != null && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(profile);
			if (info != null) {
				return info.getSkin();
			}
		}
		return profile == null ? DefaultPlayerSkin.getDefaultSkin() : DefaultPlayerSkin.get(profile);
	}

	/** The face at whole design pixels, {@code size} across. */
	public static void draw(Ui ui, @Nullable UUID profile, float x, float y, int size) {
		ui.flush();
		PlayerFaceExtractor.extractRenderState(ui.g, skin(profile), Math.round(x), Math.round(y), size);
	}

	/**
	 * The face in its tile: a shell square with the face inside and, for a friend online in this world, the green ring
	 * (a 1.5 px rounded border); {@code size} is the tile's side.
	 */
	public static void tile(Ui ui, @Nullable UUID profile, float x, float y, float size, boolean ring, float alpha) {
		float r = size * 0.3F;
		ui.round(x, y, size, size, r, Theme.alpha(Theme.SHELL, alpha));
		ui.border(x, y, size, size, r, 1.5F, ring ? Theme.alpha(RING, alpha) : Theme.alpha(0xFF6B6E73, alpha));
		int face = Math.round(size * 0.62F);
		draw(ui, profile, x + (size - face) / 2F, y + (size - face) / 2F, face);
	}
}
