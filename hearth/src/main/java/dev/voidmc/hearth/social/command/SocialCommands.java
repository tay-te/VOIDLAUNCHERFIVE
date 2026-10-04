package dev.voidmc.hearth.social.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.FolkFacts;
import dev.voidmc.hearth.social.SocialConfig;
import dev.voidmc.hearth.social.SocialLedger;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.calendar.Festival;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.calendar.TownCalendar;
import dev.voidmc.hearth.social.calendar.TownEvent;
import dev.voidmc.hearth.social.dice.DiceTables;
import dev.voidmc.hearth.social.life.Matchmaker;
import dev.voidmc.hearth.social.life.Notices;
import dev.voidmc.hearth.social.life.TownLife;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /hearth social ...}: what a settlement has on, its notices and officials, and dev tools to hold a market, a
 * festival, a wedding or the court now, ring the bell, run the day's business, and sit down to dice with a resident.
 */
public final class SocialCommands {
	private static final SimpleCommandExceptionType NOT_IN_TOWN = new SimpleCommandExceptionType(Component.translatable("hearth.social.command.not_in_town"));

	private SocialCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("hearth").then(Commands.literal("social")
			.then(Commands.literal("events").executes(SocialCommands::events))
			.then(Commands.literal("notices").executes(SocialCommands::notices))
			.then(Commands.literal("offices").executes(SocialCommands::offices))
			.then(Commands.literal("status").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				for (String line : TownLife.stats()) {
					c.getSource().sendSuccess(() -> Component.literal(line), false);
				}
				return 1;
			}))
			.then(Commands.literal("force").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("market").executes(c -> force(c, TownEvent.Kind.MARKET, null)))
				.then(Commands.literal("court").executes(c -> force(c, TownEvent.Kind.COURT, null)))
				.then(Commands.literal("wedding").executes(SocialCommands::wedding))
				.then(Commands.literal("festival")
					.executes(c -> force(c, TownEvent.Kind.FESTIVAL, Festival.MIDSUMMER))
					.then(Commands.argument("name", StringArgumentType.word()).suggests((c, b) -> {
						for (Festival f : Festival.values()) {
							b.suggest(f.id());
						}
						return b.buildFuture();
					}).executes(c -> {
						Festival f = Festival.byId(StringArgumentType.getString(c, "name"));
						return force(c, TownEvent.Kind.FESTIVAL, f == null ? Festival.MIDSUMMER : f);
					}))))
			.then(Commands.literal("bell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				boolean ok = TownLife.ringNow(here(c));
				c.getSource().sendSuccess(() -> Component.translatable(ok ? "hearth.social.command.bell" : "hearth.social.command.no_bell"), false);
				return ok ? 1 : 0;
			}))
			.then(Commands.literal("day").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(c -> {
				Settlement s = here(c);
				TownLife.runDay(s);
				TownLife.refresh(s);
				c.getSource().sendSuccess(() -> Component.translatable("hearth.social.command.day", s.name()), true);
				return 1;
			}))
			.then(Commands.literal("dice").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> dice(c, 0))
				.then(Commands.argument("pick", IntegerArgumentType.integer(0, 10_000)).executes(c -> dice(c, IntegerArgumentType.getInteger(c, "pick")))))));
	}

	private static Settlement here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		HearthWorld w = HearthWorld.of(level);
		Settlement s = w == null ? null : w.settlementAt(net.minecraft.core.BlockPos.containing(c.getSource().getPosition()));
		if (s == null && w != null) {
			s = w.nearest(net.minecraft.core.BlockPos.containing(c.getSource().getPosition()), 400);
		}
		if (s == null) {
			throw NOT_IN_TOWN.create();
		}
		return s;
	}

	private static int events(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		HearthWorld w = HearthWorld.of(c.getSource().getLevel());
		SocialConfig cfg = SocialModule.config();
		long today = w == null ? 0 : TownLife.today(w, s);
		long now = TownLife.now(s);
		CommandSourceStack src = c.getSource();
		src.sendSuccess(() -> Component.translatable("hearth.social.command.events", s.name(), today, SocialConfig.formatClock(SocialTime.minute(now)),
			TownCalendar.weekday(s.id(), today, cfg.market().weekDays())).withStyle(ChatFormatting.GOLD), false);
		for (TownEvent e : TownLife.events(s)) {
			String line = " " + e.key() + "  " + SocialConfig.formatClock(SocialTime.minute(e.start())) + "–" + SocialConfig.formatClock(SocialTime.minute(e.end()))
				+ (e.activeAt(now) ? "  (on now)" : "") + (e.names().isEmpty() ? "" : "  " + String.join(" & ", e.names()));
			src.sendSuccess(() -> Component.literal(line), false);
		}
		TownCalendar.Upcoming f = TownCalendar.nextFestival(today, s.culture(), s.foundedDay(), s.tier(), cfg.festivals());
		long market = TownCalendar.nextMarketDay(s.id(), today, cfg.market());
		src.sendSuccess(() -> Component.literal(" next market: day " + market + (f == null ? "" : ", next festival: " + f.festival().id() + " on day " + f.day())),
			false);
		return 1;
	}

	private static int notices(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		HearthWorld w = HearthWorld.of(c.getSource().getLevel());
		long today = w == null ? 0 : TownLife.today(w, s);
		List<SocialLedger.Notice> list = Notices.current(s.data(SocialModule.LEDGER), today);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.social.command.notices", s.name(), list.size()).withStyle(ChatFormatting.GOLD), false);
		for (SocialLedger.Notice n : list) {
			c.getSource().sendSuccess(() -> Component.literal(" • ").append(Notices.text(n, today)), false);
		}
		return list.size();
	}

	private static int offices(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.social.command.offices", s.name()).withStyle(ChatFormatting.GOLD), false);
		int n = 0;
		for (Office o : Office.values()) {
			Resident r = Officials.holder(s, ledger, o);
			if (r != null) {
				n++;
				c.getSource().sendSuccess(() -> Component.literal(" ").append(Component.translatable(o.translationKey())).append(": " + r.fullName()
					+ " (" + r.job.getSerializedName() + ")"), false);
			}
		}
		return n;
	}

	private static int force(CommandContext<CommandSourceStack> c, TownEvent.Kind kind, Festival f) throws CommandSyntaxException {
		Settlement s = here(c);
		TownEvent e = TownLife.force(s, kind, f, kind == TownEvent.Kind.MARKET ? 4 : 3);
		if (e == null) {
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("hearth.social.command.forced", e.key(), s.name()), true);
		return 1;
	}

	/** Marries a match now and holds the wedding and its feast at once (dev). */
	private static int wedding(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		HearthWorld w = HearthWorld.of(c.getSource().getLevel());
		long day = w == null ? 0 : TownLife.today(w, s);
		Matchmaker.Match m = Matchmaker.find(s.residents(), new HashSet<>(), false, 0, new SplittableRandom(c.getSource().getLevel().getGameTime()));
		if (m == null) {
			c.getSource().sendFailure(Component.translatable("hearth.social.command.no_match"));
			return 0;
		}
		FolkFacts.marry(m.a(), m.b(), day);
		SocialLedger ledger = s.data(SocialModule.LEDGER);
		List<String> names = List.of(m.a().fullName(), m.b().fullName());
		ledger.family.add(new SocialLedger.FamilyEvent(SocialLedger.FamilyEvent.Kind.WEDDING, day, List.of(m.a().id, m.b().id), names,
			FolkFacts.family(s, m.a()), ""));
		Notices.post(ledger, "wedding", day, day, "hearth.social.notice.banns", names, SocialModule.config().life().maxNotices());
		s.markDirty();
		// today's agenda puts the wedding at its usual hour; to see it now, hold it now, the feast after
		List<java.util.UUID> couple = List.of(m.a().id, m.b().id);
		List<java.util.UUID> kin = FolkFacts.family(s, m.a());
		TownEvent wed = TownLife.force(s, TownEvent.Kind.WEDDING, null, 0, 1, couple, kin, names);
		TownLife.force(s, TownEvent.Kind.FEAST, null, 1, 3, couple, kin, names);
		c.getSource().sendSuccess(() -> Component.translatable("hearth.social.command.wedding", names.get(0), names.get(1)), true);
		return wed == null ? 0 : 1;
	}

	private static int dice(CommandContext<CommandSourceStack> c, int pick) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Settlement s = here(c);
		List<Resident> adults = s.residents().stream().filter(r -> r.age == AgeStage.ADULT).toList();
		if (adults.isEmpty()) {
			return 0;
		}
		Resident r = adults.get(pick % adults.size());
		return DiceTables.open(player, s, r) ? 1 : 0;
	}
}
