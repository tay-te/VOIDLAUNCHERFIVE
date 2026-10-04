package dev.voidmc.hearth.standing.appearance;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.Culture;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.standing.StandingConfig;
import dev.voidmc.hearth.standing.rules.StandingRules;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * Reads a player into a {@link Look} and keeps the body state it needs ({@link PlayerState}, persisted per player). A
 * handful of inventory reads per call — cheap enough for every speech check and price.
 */
public final class Appearances {
	public static final AttachmentType<PlayerState> STATE = AttachmentRegistry.create(Hearth.id("standing/player"), b -> b
		.persistent(PlayerState.CODEC)
		.initializer(() -> PlayerState.FRESH)
		.copyOnDeath());

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final long TICKS_PER_DAY = 24000;

	private Appearances() {
	}

	/** Forces the attachment to register during init. */
	public static void init() {
	}

	public static PlayerState state(Player player) {
		PlayerState s = player.getAttached(STATE);
		return s == null ? PlayerState.FRESH : s;
	}

	public static void set(Player player, PlayerState state) {
		player.setAttached(STATE, state);
	}

	/** The time the standing module measures body state in: the overworld's game time, the same in every dimension. */
	public static long now(ServerPlayer player) {
		return player.level().getServer().overworld().getGameTime();
	}

	/**
	 * How the player looks now.
	 *
	 * @param culture  the culture whose dress counts as local (the town the player is in or nearest), or null
	 * @param violence the player's reputation for violence, 0…100
	 */
	public static Look look(ServerPlayer player, @Nullable Culture culture, double violence, StandingConfig.Appearance cfg) {
		long now = now(player);
		PlayerState s = state(player);
		Set<DyeColor> local = culture == null ? Set.of() : StandingRules.get().dress(culture.getSerializedName());
		int localPieces = 0;
		int foreign = 0;
		int plain = 0;
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
			if (dye != null) {
				DyeColor nearest = nearest(dye.rgb());
				if (local.isEmpty()) {
					plain++;
				} else if (local.contains(nearest)) {
					localPieces++;
				} else {
					foreign++;
				}
			} else if (isLeather(stack)) {
				plain++;
			}
		}
		double clean = cfg.enabled() ? s.cleanliness(now, (long) (cfg.dirtyAfterDays() * TICKS_PER_DAY)) : 0.5;
		boolean bathed = cfg.enabled() && s.bathed(now, cfg.bathBonusHours() * 1000L);
		boolean blood = cfg.enabled() && s.bloodied(now, cfg.bloodMinutes() * 1200L);
		return new Look(clean, bathed, blood, weaponDrawn(player), player.getArmorValue(), localPieces, foreign, plain, violence);
	}

	public static boolean weaponDrawn(Player player) {
		ItemStack hand = player.getMainHandItem();
		return !hand.isEmpty() && (hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) || hand.is(ItemTags.SPEARS) || hand.is(Items.MACE)
			|| hand.is(Items.TRIDENT) || hand.is(Items.BOW) || hand.is(Items.CROSSBOW));
	}

	private static boolean isLeather(ItemStack stack) {
		return stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS);
	}

	/** The dye colour nearest an RGB colour (leather is dyed by mixing, so match by distance). */
	public static DyeColor nearest(int rgb) {
		int r = rgb >> 16 & 0xFF;
		int g = rgb >> 8 & 0xFF;
		int b = rgb & 0xFF;
		DyeColor best = DyeColor.WHITE;
		long least = Long.MAX_VALUE;
		for (DyeColor c : DyeColor.values()) {
			int t = c.getTextureDiffuseColor();
			long dr = (t >> 16 & 0xFF) - r;
			long dg = (t >> 8 & 0xFF) - g;
			long db = (t & 0xFF) - b;
			long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (d < least) {
				least = d;
				best = c;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- body state changes

	/**
	 * Called every few seconds per player: under water or swimming washes them; in water at a town's bath is a proper
	 * bath. Returns whether anything changed.
	 */
	public static boolean checkWater(ServerPlayer player, @Nullable Settlement in) {
		if (!player.isInWater()) {
			return false;
		}
		long now = now(player);
		PlayerState s = state(player);
		if (in != null && nearBath(in, player.blockPosition())) {
			if (!s.bathed(now, 200)) {
				set(player, s.bathedAt(now));
				return true;
			}
			return false;
		}
		if (player.isUnderWater() || player.isSwimming()) {
			if (s.washed() == Long.MIN_VALUE || now - s.washed() > 100) {
				set(player, s.washedAt(now));
				return true;
			}
		}
		return false;
	}

	private static boolean nearBath(Settlement s, BlockPos pos) {
		for (Poi p : s.pois(PoiType.BATH)) {
			if (p.pos().distManhattan(pos) <= 4) {
				return true;
			}
		}
		return false;
	}

	/** A proper bath, reported by another module (the bathhouse) as a {@code wash} deed. */
	public static void bathe(ServerPlayer player) {
		set(player, state(player).bathedAt(now(player)));
	}

	public static void bloody(ServerPlayer player) {
		set(player, state(player).bloodiedAt(now(player)));
	}

	/** The culture whose dress counts as local where the player stands: the town they are in, or the nearest within 512 blocks. */
	public static @Nullable Culture localCulture(ServerPlayer player) {
		HearthWorld w = HearthWorld.of(player.level());
		if (w == null) {
			return null;
		}
		Settlement s = w.settlementAt(player.blockPosition());
		if (s == null) {
			s = w.nearest(player.blockPosition(), 512);
		}
		return s == null ? null : s.culture();
	}
}
