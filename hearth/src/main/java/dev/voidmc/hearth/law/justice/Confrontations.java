package dev.voidmc.hearth.law.justice;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.crime.CrimeDesk;
import dev.voidmc.hearth.law.folk.FolkTraits;
import dev.voidmc.hearth.law.net.LawPayloads;
import dev.voidmc.hearth.law.own.Ownership;
import dev.voidmc.hearth.law.state.LawState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * A folk stopping a player and the player's answer (docs/LIVING_WORLD.md §4): a guard's arrest (pay, come quietly, the
 * pillory, persuade, bribe, run, fight), a guard's stop-and-search, or a brave witness demanding an account (bribe,
 * threaten, walk away). One open at a time per player. The folk behaviours open them and read the outcome; the screen
 * only shows the choices and sends one back.
 */
public final class Confrontations {
	/** A talk left unanswered this long counts as walking away. */
	static final long TIMEOUT_TICKS = 20 * 45;
	/** Walking this far from the folk ends it (as fleeing, for an arrest). */
	static final double LEAVE_DISTANCE = 9.0;
	private static final Map<UUID, Talk> OPEN = new HashMap<>();

	private Confrontations() {
	}

	public enum Kind {
		ARREST, SEARCH, WITNESS
	}

	public enum State {
		OPEN,
		/** Paid, talked round, searched clean, bribed: no further business. */
		SETTLED,
		/** Off to the cell or the pillory. */
		SENTENCED,
		/** Ran or fought: chase them. */
		RESISTED,
		/** A witness who will not be bought: they hurry off to report it. */
		REFUSED,
		/** Ended without an answer (a witness talk walked away from). */
		CLOSED
	}

	/** One confrontation. */
	public static final class Talk {
		public final UUID player;
		public final UUID folk;
		public final long settlementId;
		public final int entity;
		public Kind kind;
		public State state = State.OPEN;
		final long opened;
		final String name;
		Component speech;
		boolean insisted;

		Talk(UUID player, UUID folk, long settlementId, int entity, Kind kind, long opened, String name) {
			this.player = player;
			this.folk = folk;
			this.settlementId = settlementId;
			this.entity = entity;
			this.kind = kind;
			this.opened = opened;
			this.name = name;
		}
	}

	public static @Nullable Talk of(UUID player) {
		return OPEN.get(player);
	}

	/** A folk confronts a player; replaces any talk the player had open. */
	public static Talk open(ServerPlayer player, FolkHandle folk, Kind kind) {
		Resident r = folk.resident();
		Talk talk = new Talk(player.getUUID(), r.id, folk.settlement().id(), folk.entity().getId(), kind, player.level().getGameTime(), r.fullName());
		talk.speech = opening(player, folk.settlement(), kind);
		OPEN.put(player.getUUID(), talk);
		folk.lookAt(player);
		send(player, talk, folk.settlement(), r);
		return talk;
	}

	private static Component opening(ServerPlayer player, Settlement s, Kind kind) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		return switch (kind) {
			case ARREST -> w != null && w.murder() ? Component.translatable("hearth.law.guard.murder", player.getName())
				: Component.translatable("hearth.law.guard.halt", Component.translatable(w == null ? "hearth.offence.trespass" : w.worst().translationKey()),
				w == null ? 0 : w.bounty());
			case SEARCH -> Component.translatable("hearth.law.guard.search");
			case WITNESS -> Component.translatable("hearth.law.witness.confront");
		};
	}

	/** The player's answer. */
	public static void choose(ServerPlayer player, int entity, String choice) {
		Talk talk = OPEN.get(player.getUUID());
		if (talk == null || talk.entity != entity || talk.state != State.OPEN) {
			return;
		}
		Settlement s = Ownership.settlement(talk.settlementId, player.level().getServer());
		Resident folk = s == null ? null : s.resident(talk.folk);
		if (s == null || folk == null) {
			close(player);
			return;
		}
		if (choice.isEmpty()) {
			walkedAway(player, talk, s);
			return;
		}
		switch (talk.kind) {
			case ARREST -> arrest(player, talk, s, folk, choice);
			case SEARCH -> search(player, talk, s, folk, choice);
			case WITNESS -> witness(player, talk, s, folk, choice);
		}
		if (talk.state == State.OPEN) {
			send(player, talk, s, folk);
		} else {
			finish(player, talk);
		}
	}

	private static void arrest(ServerPlayer player, Talk talk, Settlement s, Resident guard, String choice) {
		Justice.Result result = switch (choice) {
			case "pay" -> Justice.pay(player, s);
			case "submit" -> Justice.submit(player, s, false);
			case "pillory" -> Justice.submit(player, s, true);
			case "persuade" -> Justice.persuade(player, s, guard);
			case "bribe" -> Justice.bribe(player, s, guard);
			case "flee", "resist" -> Justice.resist(player, s);
			default -> Justice.Result.UNABLE;
		};
		switch (result) {
			case SETTLED -> {
				talk.state = State.SETTLED;
				say(player, talk, Component.translatable(choice.equals("bribe") ? "hearth.law.guard.bribed" : "hearth.law.guard.settled"));
			}
			case SENTENCED -> {
				talk.state = State.SENTENCED;
				say(player, talk, Component.translatable("hearth.law.guard.take_away"));
			}
			case REDUCED -> talk.speech = Component.translatable("hearth.law.guard.reduced", bounty(s, player));
			case FAILED -> talk.speech = Component.translatable(choice.equals("bribe") ? "hearth.law.guard.bribe_failed" : "hearth.law.guard.persuade_failed",
				bounty(s, player));
			case UNABLE -> talk.speech = Component.translatable("hearth.law.guard.unable");
			case RESISTED -> {
				talk.state = State.RESISTED;
				say(player, talk, Component.translatable(choice.equals("flee") ? "hearth.law.guard.flee" : "hearth.law.guard.resist"));
			}
		}
	}

	private static void search(ServerPlayer player, Talk talk, Settlement s, Resident guard, String choice) {
		switch (choice) {
			case "allow" -> {
				long found = Justice.search(player, s, guard.id);
				if (found > 0) {
					// caught with the goods: now it is an arrest
					talk.kind = Kind.ARREST;
					talk.speech = Component.translatable("hearth.law.guard.found", bounty(s, player));
				} else {
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.guard.clean"));
				}
			}
			case "refuse" -> {
				if (talk.insisted || Law.state(s).suspicion(player.getUUID()) >= 80) {
					Justice.resist(player, s);
					talk.state = State.RESISTED;
					say(player, talk, Component.translatable("hearth.law.guard.resist"));
				} else {
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.guard.watching"));
				}
			}
			case "persuade" -> {
				if (player.getRandom().nextDouble() < searchPersuadeOdds(player, s, guard)) {
					Law.state(s).addSuspicion(player.getUUID(), -50);
					s.markDirty();
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.guard.let_go"));
				} else {
					talk.insisted = true;
					talk.speech = Component.translatable("hearth.law.guard.insist");
				}
			}
			case "bribe" -> {
				EconomyService economy = HearthServices.economy();
				long amount = searchBribe();
				double odds = SpeechCheck.bribe(amount, amount, 2, FolkTraits.of(guard).listener());
				if (economy != null && economy.balance(player) >= amount && player.getRandom().nextDouble() < odds) {
					economy.bribe(player, s, guard, amount);
					Law.state(s).addSuspicion(player.getUUID(), -50);
					s.markDirty();
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.guard.bribed"));
				} else {
					talk.insisted = true;
					talk.speech = Component.translatable("hearth.law.guard.insist");
				}
			}
			default -> {
			}
		}
	}

	private static void witness(ServerPlayer player, Talk talk, Settlement s, Resident witness, String choice) {
		List<LawState.PendingReport> reports = CrimeDesk.reportsBy(s, witness.id, player.getUUID());
		long fine = reports.stream().mapToLong(p -> Offences.fine(p.crime(), Law.config().fines())).max().orElse(10);
		switch (choice) {
			case "bribe" -> {
				EconomyService economy = HearthServices.economy();
				long amount = SpeechCheck.witnessBribe(fine);
				double odds = SpeechCheck.bribe(amount, SpeechCheck.witnessBribe(fine), worst(reports), FolkTraits.of(witness).listener());
				if (economy != null && economy.balance(player) >= amount && player.getRandom().nextDouble() < odds) {
					economy.bribe(player, s, witness, amount);
					CrimeDesk.silence(s, witness.id, player.getUUID());
					CrimeDesk.deed(s, player.getUUID(), "law:bribed_witness", -2, witness.id, player.blockPosition(), false);
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.witness.bribed"));
				} else {
					talk.state = State.REFUSED;
					say(player, talk, Component.translatable("hearth.law.witness.refused"));
				}
			}
			case "threaten" -> {
				if (player.getRandom().nextDouble() < threatenOdds(player, witness)) {
					CrimeDesk.silence(s, witness.id, player.getUUID());
					CrimeDesk.deed(s, player.getUUID(), "law:threatened_witness", -8, witness.id, player.blockPosition(), false);
					talk.state = State.SETTLED;
					say(player, talk, Component.translatable("hearth.law.witness.cowed"));
				} else {
					talk.state = State.REFUSED;
					say(player, talk, Component.translatable("hearth.law.witness.defiant"));
				}
			}
			default -> {
				talk.state = State.CLOSED;
			}
		}
	}

	private static int worst(List<LawState.PendingReport> reports) {
		return reports.stream().mapToInt(p -> Offences.severity(p.crime().offence())).max().orElse(1);
	}

	private static void walkedAway(ServerPlayer player, Talk talk, Settlement s) {
		if (talk.kind == Kind.ARREST || talk.kind == Kind.SEARCH && talk.insisted) {
			Justice.resist(player, s);
			talk.state = State.RESISTED;
		} else if (talk.kind == Kind.WITNESS) {
			talk.state = State.REFUSED;
		} else {
			talk.state = State.CLOSED;
		}
		finish(player, talk);
	}

	/** Times out unanswered talks and notices players who walked off. Once a second. */
	public static void tick(MinecraftServer server) {
		if (OPEN.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Talk>> it = OPEN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Talk> e = it.next();
			Talk talk = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			if (talk.state != State.OPEN) {
				continue;
			}
			net.minecraft.world.entity.Entity folk = player.level().getEntity(talk.entity);
			boolean gone = folk == null || !folk.isAlive() || folk.distanceTo(player) > LEAVE_DISTANCE;
			if (gone || player.level().getGameTime() - talk.opened > TIMEOUT_TICKS) {
				Settlement s = Ownership.settlement(talk.settlementId, server);
				if (s != null && folk != null && folk.isAlive()) {
					walkedAway(player, talk, s);
				} else {
					talk.state = State.CLOSED;
					finish(player, talk);
				}
			}
		}
	}

	/** Forgets talks whose outcome the folk behaviour has read. */
	public static void release(UUID player) {
		Talk t = OPEN.get(player);
		if (t != null && t.state != State.OPEN) {
			OPEN.remove(player);
		}
	}

	public static void close(ServerPlayer player) {
		Talk t = OPEN.remove(player.getUUID());
		if (t != null && ServerPlayNetworking.canSend(player, LawPayloads.Confront.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.Confront(t.entity, false, "", Component.empty(), 0, List.of()));
		}
	}

	private static void finish(ServerPlayer player, Talk talk) {
		if (ServerPlayNetworking.canSend(player, LawPayloads.Confront.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.Confront(talk.entity, false, "", Component.empty(), 0, List.of()));
		}
	}

	public static void forget(UUID player) {
		OPEN.remove(player);
	}

	public static void clear() {
		OPEN.clear();
	}

	// ---------------------------------------------------------------- the screen

	private static void send(ServerPlayer player, Talk talk, Settlement s, Resident folk) {
		List<LawPayloads.Choice> choices = switch (talk.kind) {
			case ARREST -> arrestChoices(player, s, folk);
			case SEARCH -> searchChoices(player, s, folk, talk.insisted);
			case WITNESS -> witnessChoices(player, s, folk);
		};
		long bounty = talk.kind == Kind.ARREST ? bounty(s, player) : 0;
		if (ServerPlayNetworking.canSend(player, LawPayloads.Confront.TYPE)) {
			ServerPlayNetworking.send(player, new LawPayloads.Confront(talk.entity, true, talk.name, talk.speech, bounty, choices));
		}
	}

	static List<LawPayloads.Choice> arrestChoices(ServerPlayer player, Settlement s, Resident guard) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		LawConfig cfg = Law.config();
		List<LawPayloads.Choice> out = new ArrayList<>();
		long bounty = w == null ? 0 : w.bounty();
		EconomyService economy = HearthServices.economy();
		Component coin = economy == null ? Component.literal(Long.toString(bounty)) : economy.format(bounty);
		out.add(new LawPayloads.Choice("pay", Component.translatable("hearth.law.choice.pay", coin), Justice.canAfford(player, bounty)));
		boolean cell = cfg.justice().jail() && Jail.cell(s, player.blockPosition()) != null;
		int minutes = Offences.jailMinutes(bounty, w != null && w.murder(), cfg.justice());
		out.add(new LawPayloads.Choice("submit", cell ? Component.translatable("hearth.law.choice.jail", minutes)
			: Component.translatable("hearth.law.choice.submit"), true));
		if (w != null && Offences.pilloryEligible(w, cfg.justice()) && Jail.pillory(s, player.blockPosition()) != null) {
			out.add(new LawPayloads.Choice("pillory", Component.translatable("hearth.law.choice.pillory", cfg.justice().pilloryMinutes()), true));
		}
		boolean murder = w != null && w.murder();
		int persuade = SpeechCheck.percent(Justice.persuadeOdds(player, s, guard));
		out.add(new LawPayloads.Choice("persuade", Component.translatable("hearth.law.choice.persuade", persuade),
			w != null && !w.persuadeTried() && !murder));
		if (cfg.witnesses().bribes() && economy != null) {
			long amount = Justice.bribeAmount(s, player.getUUID());
			out.add(new LawPayloads.Choice("bribe", Component.translatable("hearth.law.choice.bribe", economy.format(amount),
				SpeechCheck.percent(Justice.bribeOdds(player, s, guard))), w != null && !w.bribeTried() && Justice.canAfford(player, amount)));
		}
		out.add(new LawPayloads.Choice("flee", Component.translatable("hearth.law.choice.flee"), true));
		out.add(new LawPayloads.Choice("resist", Component.translatable("hearth.law.choice.resist"), true));
		return out;
	}

	private static List<LawPayloads.Choice> searchChoices(ServerPlayer player, Settlement s, Resident guard, boolean insisted) {
		List<LawPayloads.Choice> out = new ArrayList<>();
		out.add(new LawPayloads.Choice("allow", Component.translatable("hearth.law.choice.allow"), true));
		out.add(new LawPayloads.Choice("refuse", Component.translatable(insisted ? "hearth.law.choice.refuse_hard" : "hearth.law.choice.refuse"), true));
		if (!insisted) {
			out.add(new LawPayloads.Choice("persuade", Component.translatable("hearth.law.choice.persuade",
				SpeechCheck.percent(searchPersuadeOdds(player, s, guard))), true));
			EconomyService economy = HearthServices.economy();
			if (economy != null && Law.config().witnesses().bribes()) {
				long amount = searchBribe();
				out.add(new LawPayloads.Choice("bribe", Component.translatable("hearth.law.choice.bribe", economy.format(amount),
					SpeechCheck.percent(SpeechCheck.bribe(amount, amount, 2, FolkTraits.of(guard).listener()))), Justice.canAfford(player, amount)));
			}
		}
		return out;
	}

	private static List<LawPayloads.Choice> witnessChoices(ServerPlayer player, Settlement s, Resident witness) {
		List<LawState.PendingReport> reports = CrimeDesk.reportsBy(s, witness.id, player.getUUID());
		long fine = reports.stream().mapToLong(p -> Offences.fine(p.crime(), Law.config().fines())).max().orElse(10);
		List<LawPayloads.Choice> out = new ArrayList<>();
		EconomyService economy = HearthServices.economy();
		if (economy != null && Law.config().witnesses().bribes()) {
			long amount = SpeechCheck.witnessBribe(fine);
			out.add(new LawPayloads.Choice("bribe", Component.translatable("hearth.law.choice.hush", economy.format(amount),
				SpeechCheck.percent(SpeechCheck.bribe(amount, amount, worst(reports), FolkTraits.of(witness).listener()))), Justice.canAfford(player, amount)));
		}
		if (Law.config().witnesses().threats()) {
			out.add(new LawPayloads.Choice("threaten", Component.translatable("hearth.law.choice.threaten",
				SpeechCheck.percent(threatenOdds(player, witness))), true));
		}
		out.add(new LawPayloads.Choice("leave", Component.translatable("hearth.law.choice.leave"), true));
		return out;
	}

	static double threatenOdds(ServerPlayer player, Resident witness) {
		StandingService standing = HearthServices.standing();
		float menace = standing != null ? standing.menace(player) : fallbackMenace(player);
		return SpeechCheck.threaten(menace, FolkTraits.of(witness).listener());
	}

	/** Without the standing module: armour and a weapon in hand make a threat. */
	static float fallbackMenace(ServerPlayer player) {
		float armour = player.getArmorValue() / 20.0F;
		boolean armed = player.getMainHandItem().has(net.minecraft.core.component.DataComponents.WEAPON);
		return Math.min(1.0F, armour * 0.6F + (armed ? 0.35F : 0.0F));
	}

	private static double searchPersuadeOdds(ServerPlayer player, Settlement s, Resident guard) {
		StandingService standing = HearthServices.standing();
		int st = standing != null ? standing.standing(s.id(), player.getUUID()) : s.reputation(player.getUUID());
		float charisma = standing != null ? standing.charisma(player) : 0;
		return SpeechCheck.persuade(st, 0, charisma, 1, FolkTraits.of(guard).listener());
	}

	private static long searchBribe() {
		return 15;
	}

	private static long bounty(Settlement s, ServerPlayer player) {
		Warrant w = Law.state(s).warrant(player.getUUID());
		return w == null ? 0 : w.bounty();
	}

	/** The folk says the last word aloud (the screen closes). */
	private static void say(ServerPlayer player, Talk talk, Component line) {
		NpcService npc = HearthServices.npc();
		FolkHandle folk = npc == null ? null : npc.folk(talk.folk);
		if (folk != null) {
			try {
				folk.say(line, player);
			} catch (RuntimeException e) {
				Hearth.LOG.debug("law: folk could not speak", e);
			}
		} else {
			player.sendSystemMessage(Component.literal("<" + talk.name + "> ").append(line));
		}
	}
}
