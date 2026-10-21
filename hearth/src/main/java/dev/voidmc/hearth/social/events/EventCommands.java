package dev.voidmc.hearth.social.events;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.justice.Trials;
import dev.voidmc.hearth.repair.fire.FireLeash;
import dev.voidmc.hearth.social.calendar.SocialTime;
import dev.voidmc.hearth.social.life.TownLife;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * {@code /hearth events status|book} (what the event book holds and what the scenes are doing, with their timings) and,
 * for game masters, {@code /hearth events force <kind>} (also {@code /hearth social force <kind>} for the kinds the
 * social module's own force does not have): an event of that kind in the town here, now — for tours and testing; a
 * forced execution never kills (it ends in mercy). {@code /hearth events shell} lays the town's burnt shells now.
 */
final class EventCommands {
	private static final SimpleCommandExceptionType NOT_IN_TOWN = new SimpleCommandExceptionType(Component.translatable("hearth.social.command.not_in_town"));
	private static final SimpleCommandExceptionType CANNOT = new SimpleCommandExceptionType(Component.literal("that event cannot be held here now"));

	private EventCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		LiteralArgumentBuilder<CommandSourceStack> force = Commands.literal("force").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		LiteralArgumentBuilder<CommandSourceStack> socialForce = Commands.literal("force").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (EventKind k : EventKind.VALUES) {
			if (k == EventKind.SCENE) {
				continue;
			}
			force.then(Commands.literal(k.id()).executes(c -> force(c, k)));
			// the social module's own force holds a market, a festival, a wedding or the court
			if (k != EventKind.WEDDING) {
				socialForce.then(Commands.literal(k.id()).executes(c -> force(c, k)));
			}
		}
		d.register(Commands.literal("hearth").then(Commands.literal("events")
			.then(Commands.literal("status").executes(EventCommands::status))
			.then(Commands.literal("book").executes(EventCommands::book))
			.then(Commands.literal("shell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(EventCommands::shell))
			.then(Commands.literal("stage").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("scene", StringArgumentType.word()).executes(c -> {
					Settlement s = here(c);
					boolean ok = Scenes.SERVICE.stage(s, dev.voidmc.hearth.Hearth.id(StringArgumentType.getString(c, "scene")), List.of(),
						c.getSource().getPlayer(), new CompoundTag());
					c.getSource().sendSuccess(() -> Component.literal(ok ? "staged" : "could not stage it"), false);
					return ok ? 1 : 0;
				})))
			.then(force)));
		d.register(Commands.literal("hearth").then(Commands.literal("social").then(socialForce)));
	}

	private static int status(CommandContext<CommandSourceStack> c) {
		List<String> lines = new ArrayList<>(EventDirector.stats());
		lines.addAll(Scenes.stats());
		lines.add(FireLeash.stats());
		for (String l : lines) {
			c.getSource().sendSuccess(() -> Component.literal(l), false);
		}
		return 1;
	}

	private static int book(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		EventBook b = s.data(EventBook.KEY);
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": " + b.scheduled.size() + " events, " + b.defendants.size() + " awaiting trial, "
			+ b.burnt.size() + " burnt plots, last tax day " + (b.lastTax == Long.MIN_VALUE ? "never" : b.lastTax)), false);
		for (Scheduled e : b.scheduled) {
			c.getSource().sendSuccess(() -> Component.literal(" #" + e.id() + " " + e.kind().id() + " day " + e.day() + " " + EventDialogue.clock(e.start()) + "–"
				+ EventDialogue.clock(e.end()) + " " + e.phase().getSerializedName() + (e.plot() >= 0 ? " plot " + e.plot() : "")), false);
		}
		for (Outcome o : b.recent) {
			c.getSource().sendSuccess(() -> Component.literal(" > " + o.kind().id() + " day " + o.day() + ": " + o.result() + (o.sentence().isEmpty() ? "" : " ("
				+ o.sentence() + ")") + (o.coin() != 0 ? " coin " + o.coin() : "") + (o.goodsLost() != 0 ? " goods " + o.goodsLost() : "")
				+ (o.winner().isEmpty() ? "" : " won by " + o.winner()) + (o.intervened() ? " [players]" : "")), false);
		}
		return 1;
	}

	private static int shell(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Settlement s = here(c);
		HearthWorld w = TownLife.world(s);
		int n = w == null ? 0 : TownEvents.applyShells(w, s);
		c.getSource().sendSuccess(() -> Component.literal(n + " blocks of burnt shell laid"), false);
		return n;
	}

	/** Holds an event of a kind in the town here, now. */
	static int force(CommandContext<CommandSourceStack> c, EventKind kind) throws CommandSyntaxException {
		Settlement s = here(c);
		Scheduled e = force(s, kind);
		if (e == null) {
			throw CANNOT.create();
		}
		c.getSource().sendSuccess(() -> Component.literal(s.name() + ": " + kind.id() + " #" + e.id() + " from " + EventDialogue.clock(e.start()) + " to "
			+ EventDialogue.clock(e.end())), true);
		return 1;
	}

	/** Holds an event now (commands, tours): the town's facts, a cast where the kind needs one, its outcome's facts filled. */
	static @Nullable Scheduled force(Settlement s, EventKind kind) {
		HearthWorld w = TownLife.world(s);
		EventRule rule = EventRules.of(kind);
		if (w == null || rule == null) {
			return null;
		}
		EventBook book = s.data(EventBook.KEY);
		long now = TownLife.now(w, s);
		long day = SocialTime.day(now);
		TownFacts f = FactsReader.read(w, s, day);
		List<UUID> principals = new ArrayList<>();
		List<UUID> family = new ArrayList<>();
		CompoundTag a = new CompoundTag();
		a.putBoolean("forced", true);
		List<Resident> adults = new ArrayList<>();
		for (Resident r : s.residents()) {
			if (r.age == AgeStage.ADULT) {
				adults.add(r);
			}
		}
		adults.sort((x, y) -> x.id.compareTo(y.id));
		int plot = -1;
		switch (kind) {
			case WEDDING -> {
				if (adults.size() < 2) {
					return null;
				}
				principals.add(adults.get(0).id);
				principals.add(adults.get(1).id);
				a.putString("names", adults.get(0).fullName() + " & " + adults.get(1).fullName());
			}
			case FUNERAL -> {
				a.putString("names", "old " + (adults.isEmpty() ? "Tom" : adults.get(0).familyName));
				if (!adults.isEmpty()) {
					family.add(adults.get(adults.size() - 1).id);
				}
			}
			case TRIAL, PILLORY, EXECUTION -> {
				Resident accused = null;
				for (Resident r : adults) {
					if (Trials.poor(r)) {
						accused = r;
						break;
					}
				}
				if (accused == null && !adults.isEmpty()) {
					accused = adults.get(adults.size() / 2);
				}
				if (accused == null) {
					return null;
				}
				principals.add(accused.id);
				String offence = kind == EventKind.EXECUTION ? "murder" : "theft";
				a.putString("offence", offence);
				a.putDouble("evidence", 0.7);
				a.putDouble("leniency", 0);
				a.putBoolean("poor", true);
				a.putLong("fine", Trials.fine(offence));
				a.putString("executions", "staged");
				a.putString("accused", accused.fullName());
				a.put("witnesses", Outcomes.uuidList(new ArrayList<>(EventDirector.playersIn(w, s))));
			}
			case TOPPING_OUT -> {
				// the largest building of the town gets its bough
				dev.voidmc.hearth.api.PlotView best = null;
				for (dev.voidmc.hearth.api.PlotView p : s.plots()) {
					String path = p.spec().archetype().getPath();
					if (p.state() == dev.voidmc.hearth.api.PlotState.BUILT && (path.startsWith("house/") || path.startsWith("civic/"))
						&& (best == null || p.spec().sizeX() * p.spec().sizeZ() > best.spec().sizeX() * best.spec().sizeZ())) {
						best = p;
					}
				}
				if (best == null) {
					return null;
				}
				plot = best.id();
				a.putString("building", "@hearth.events.building.house");
			}
			case RAID -> {
				a.putInt("strength", Math.max(4, f.defence() + 3));
				a.putInt("defence", f.defence());
			}
			case TAX -> {
				long seat = f.seat() != 0 && f.seat() != s.id() ? f.seat() : 0;
				f = new TownFacts(f.id(), f.tier(), f.population(), f.adults(), f.prosperity(), f.culture(), f.timber(), f.lord(), f.courtDay(), f.festivalDay(),
					f.marketDay(), f.restDay(), f.fairDay(), f.tourneyDay(), seat, f.threat(), f.threatBand(), f.defence(), f.places(), f.season());
			}
			default -> {
			}
		}
		int minute = SocialTime.minute(now);
		EventScheduler.Offer offer = new EventScheduler.Offer(kind, minute, rule.duration(), principals, family, plot, a);
		Scheduled e = EventScheduler.single(s.id(), day, offer, rule, book.newId());
		EventScheduler.Draft d = EventScheduler.draft(e);
		if (!EventDirector.fill(w, s, book, f, kind, d)) {
			return null;
		}
		if (kind == EventKind.RAID) {
			d.args.putInt("strength", Math.max(4, f.defence() + 3));
		}
		e = EventScheduler.withDraft(e, d);
		if (!book.add(e)) {
			return null;
		}
		s.markDirty();
		EventDirector.watch(w, s);
		return e;
	}

	private static Settlement here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerLevel level = c.getSource().getLevel();
		HearthWorld w = HearthWorld.of(level);
		BlockPos at = BlockPos.containing(c.getSource().getPosition());
		Settlement s = w == null ? null : w.settlementAt(at);
		if (s == null && w != null) {
			s = w.nearest(at, 400);
		}
		if (s == null) {
			throw NOT_IN_TOWN.create();
		}
		return s;
	}
}
