package dev.voidmc.hearth.law;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthDataKey;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService.Offence;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.state.LawState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** The law module's shared bits: its settings, its saved state per settlement, the calendar, and who the law exempts. */
public final class Law {
	/** One settlement's warrants, suspicion, pending reports, unsolved crimes and lock overrides. */
	public static final HearthDataKey<LawState> STATE = HearthDataKey.register(Hearth.id("law/state"), HearthDataKey.Scope.SETTLEMENT,
		LawState.CODEC, LawState::new);

	private static volatile LawConfig raw = LawConfig.DEFAULTS;
	private static volatile LawConfig config = LawConfig.DEFAULTS.effective();

	private Law() {
	}

	/** The settings in force (the mode applied). */
	public static LawConfig config() {
		return config;
	}

	/** The settings as written in the file. */
	public static LawConfig rawConfig() {
		return raw;
	}

	public static void setConfig(LawConfig c) {
		raw = c;
		config = c.effective();
	}

	public static LawState state(Settlement settlement) {
		return settlement.data(STATE);
	}

	/** Whether this kind of crime is detected at all under the settings. */
	public static boolean detects(Offence offence) {
		LawConfig.Crimes c = config.crimes();
		return switch (offence) {
			case THEFT, ROBBERY -> c.theft();
			case PICKPOCKETING -> c.pickpocketing();
			case LOCKPICKING -> c.lockpicking();
			case TRESPASS -> c.trespass();
			case POACHING -> c.animals();
			case VANDALISM -> c.vandalism();
			case ASSAULT, RESISTING_ARREST -> c.assault();
			case MURDER -> c.murder();
			case JAILBREAK -> config.justice().jail();
		};
	}

	/** Players the law does not apply to: creative and spectators (and game masters if configured). */
	public static boolean exempt(ServerPlayer player) {
		LawConfig c = config;
		if (c.exemptCreative() && (player.isCreative() || player.isSpectator())) {
			return true;
		}
		return c.exemptOps() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static @Nullable HearthWorld world(ServerLevel level) {
		return HearthWorld.of(level);
	}

	/** The settlement whose ground {@code pos} is, or null. */
	public static @Nullable Settlement settlementAt(ServerLevel level, BlockPos pos) {
		HearthWorld world = HearthWorld.of(level);
		return world == null ? null : world.settlementAt(pos);
	}

	/** The sim day (the sim's calendar where it runs, otherwise the overworld's day count). */
	public static long day(ServerLevel level) {
		HearthWorld world = HearthWorld.of(level);
		return world != null ? world.day() : Math.floorDiv(level.getServer().overworld().getOverworldClockTime(), 24000L);
	}

	/** The minute of the day, 0–1439 (06:00 is day time 0). */
	public static int minuteOfDay(ServerLevel level) {
		long t = Math.floorMod(level.getServer().overworld().getOverworldClockTime(), 24000L);
		return (int) ((t * 1440L / 24000L + 6 * 60) % 1440);
	}

	/** Whether {@code minute} lies in the span from {@code start} to {@code end} (which may wrap past midnight). */
	public static boolean within(int minute, int start, int end) {
		return start <= end ? minute >= start && minute < end : minute >= start || minute < end;
	}

	/** What a stack is worth to the town it came from, in bits: the market's buying price, or a guess by rarity. */
	public static long value(@Nullable Settlement settlement, ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		EconomyService economy = HearthServices.economy();
		if (economy != null && settlement != null) {
			try {
				long price = economy.price(settlement, stack, false);
				if (price > 0) {
					return price;
				}
			} catch (RuntimeException e) {
				Hearth.LOG.debug("law: no price for {}", stack, e);
			}
		}
		int each = switch (stack.getRarity()) {
			case COMMON -> 1;
			case UNCOMMON -> 5;
			case RARE -> 15;
			case EPIC -> 40;
		};
		return (long) each * stack.getCount();
	}
}
