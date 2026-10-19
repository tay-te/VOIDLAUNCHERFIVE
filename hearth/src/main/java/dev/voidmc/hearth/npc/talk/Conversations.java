package dev.voidmc.hearth.npc.talk;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Talking to folk, on the server. Right-clicking a folk opens the conversation: their name, trade and town, a greeting
 * composed for the moment, and the options every registered {@link DialogueProvider} offers, sorted by
 * {@link DialogueProvider.Option#order}. Picking one calls its provider; the npc module's own options (news, rumours,
 * about them, goodbye) answer with a line of speech.
 *
 * <p>Other modules add options by registering a provider ({@code DialogueProvider.register}); see docs/FOLK.md.
 */
public final class Conversations {
	/** Blocks a player may stand from the folk and keep talking (the client screen closes at the same distance). */
	public static final double RANGE = 10.0;

	private final Map<UUID, Session> sessions = new HashMap<>();

	private static final class Session {
		final Folk folk;
		final Map<String, DialogueProvider> owners = new HashMap<>();
		final java.util.Set<String> locked = new java.util.HashSet<>();
		Component speech = Component.empty();

		Session(Folk folk) {
			this.folk = folk;
		}
	}

	public void open(ServerPlayer player, Folk folk) {
		FolkBrain brain = folk.brain();
		if (brain == null) {
			return;
		}
		Settlement s = brain.settlement;
		Resident r = brain.resident;
		// shaken awake (the player sneaked to do it): they get up for it, and say what they think of that
		boolean woken = folk.isSleeping();
		brain.engage(player);
		Component greeting = woken ? Component.translatable(WOKEN[brain.random.nextInt(WOKEN.length)])
			: Component.literal(Greetings.greeting(player.level(), s, r, player, folk.blockPosition(), brain.random));
		brain.data.recordMeeting(player.getUUID());
		s.markDirty();
		Session session = new Session(folk);
		this.sessions.put(player.getUUID(), session);
		show(player, session, greeting, true);
	}

	/** What folk say when shaken awake to talk. */
	private static final String[] WOKEN = {"hearth.npc.woken.0", "hearth.npc.woken.1", "hearth.npc.woken.2"};
	/** What they mumble when a player tries to talk to them asleep. */
	private static final String[] MUMBLES = {"hearth.npc.mumble.0", "hearth.npc.mumble.1", "hearth.npc.mumble.2"};
	/** When each player last had a sleeper mumble at them (game time), so a held use key does not flood the chat. */
	private final Map<UUID, Long> mumbled = new HashMap<>();

	/**
	 * A player tried to talk to a folk who is asleep (without sneaking, which wakes them): the sleeper mumbles, and the
	 * player is told whose sleep it is and how to wake them, rather than nothing happening at all.
	 */
	public void asleep(ServerPlayer player, Folk folk) {
		long now = player.level().getGameTime();
		Long last = this.mumbled.get(player.getUUID());
		if (last != null && now >= last && now - last < 30) {
			return;
		}
		this.mumbled.put(player.getUUID(), now);
		Component name = Component.literal(folk.folkName()).withStyle(net.minecraft.ChatFormatting.GOLD);
		player.sendSystemMessage(Component.translatable("hearth.npc.say", name, Component.translatable(MUMBLES[(int) Math.floorMod(now / 30, MUMBLES.length)]))
			.withStyle(net.minecraft.ChatFormatting.GRAY));
		player.sendOverlayMessage(Component.translatable("hearth.npc.asleep", folk.folkName()));
	}

	/** The player picked an option. */
	public void choose(ServerPlayer player, int entityId, String optionId) {
		Session session = this.sessions.get(player.getUUID());
		if (session == null || session.folk.getId() != entityId || !valid(player, session.folk)) {
			close(player, entityId);
			return;
		}
		FolkBrain brain = session.folk.brain();
		if (brain == null) {
			return;
		}
		brain.keepEngaged(player);
		DialogueProvider provider = session.owners.get(optionId);
		if (provider != null && session.locked.contains(optionId)) {
			// a locked option cannot be picked (a modified client, or the screen out of date)
			show(player, session, session.speech, false);
			return;
		}
		if (provider == null) {
			show(player, session, session.speech, false);
			return;
		}
		if (provider instanceof NpcDialogue npc) {
			Component reply = npc.reply(player, brain, optionId);
			if (reply == null) {
				// goodbye: the folk has had their last word, and the screen goes
				close(player, entityId);
				return;
			}
			brain.speak();
			show(player, session, reply, false);
			return;
		}
		Component reply = null;
		try {
			if (provider.select(player, brain.settlement, brain.resident, optionId)) {
				reply = provider.reply(player, brain.settlement, brain.resident, optionId);
			}
		} catch (RuntimeException e) {
			dev.voidmc.hearth.Hearth.LOG.warn("dialogue provider {} failed on {}", provider.getClass().getName(), optionId, e);
		}
		// Whatever it did (a trade screen, a quest accepted), the options may have changed; a reply is the folk's new line.
		if (this.sessions.get(player.getUUID()) == session) {
			if (reply != null) {
				brain.speak();
			}
			show(player, session, reply != null ? reply : session.speech, false);
		}
	}

	public void end(ServerPlayer player, int entityId) {
		Session session = this.sessions.remove(player.getUUID());
		if (session != null && session.folk.brain() != null && session.folk.brain().engagedWith(player)) {
			session.folk.brain().disengage();
		}
	}

	/** The server ends the conversation: the session goes and the player's screen is closed. */
	public void close(ServerPlayer player, int entityId) {
		end(player, entityId);
		if (ServerPlayNetworking.canSend(player, TalkPayloads.Close.TYPE)) {
			ServerPlayNetworking.send(player, new TalkPayloads.Close(entityId));
		}
	}

	/** The player left the server: their conversation ends. */
	public void forget(ServerPlayer player) {
		this.mumbled.remove(player.getUUID());
		Session session = this.sessions.remove(player.getUUID());
		if (session != null && session.folk.brain() != null && session.folk.brain().engagedWith(player)) {
			session.folk.brain().disengage();
		}
	}

	private static boolean valid(ServerPlayer player, Folk folk) {
		return folk.isAlive() && !folk.isRemoved() && folk.level() == player.level() && folk.distanceToSqr(player) <= RANGE * RANGE;
	}

	private void show(ServerPlayer player, Session session, Component speech, boolean open) {
		FolkBrain brain = session.folk.brain();
		if (brain == null) {
			return;
		}
		session.speech = speech;
		session.owners.clear();
		session.locked.clear();
		List<Ranked> ranked = new ArrayList<>();
		Map<String, DialogueProvider.Info> described = new HashMap<>();
		for (DialogueProvider p : DialogueProvider.providers()) {
			List<DialogueProvider.Option> out = new ArrayList<>();
			DialogueProvider.Registry.collectInfo(described);
			try {
				p.collect(player, brain.settlement, brain.resident, out);
			} catch (RuntimeException e) {
				dev.voidmc.hearth.Hearth.LOG.warn("dialogue provider {} failed", p.getClass().getName(), e);
				continue;
			} finally {
				DialogueProvider.Registry.collectInfo(null);
			}
			for (DialogueProvider.Option o : out) {
				if (!session.owners.containsKey(o.id())) {
					session.owners.put(o.id(), p);
					if (o.locked()) {
						session.locked.add(o.id());
					}
					ranked.add(new Ranked(o, ranked.size()));
				}
			}
		}
		// the same question from two providers is asked once (docs/OVERLAY.md: the better kept)
		List<Ranked> kept = OptionIntents.dedupe(ranked, k -> k.option.id(), k -> k.option.label().getString(), k -> k.option.locked(),
			k -> session.owners.get(k.option.id()));
		if (kept.size() != ranked.size()) {
			for (Ranked k : ranked) {
				if (!kept.contains(k)) {
					session.owners.remove(k.option.id());
					session.locked.remove(k.option.id());
				}
			}
			ranked = new ArrayList<>(kept);
		}
		ranked.sort(Comparator.<Ranked>comparingInt(r -> r.option.order()).thenComparingInt(r -> r.index));
		if (ranked.size() > TalkPayloads.MAX_OPTIONS) {
			// too many to send: the last (goodbye) stays
			Ranked last = ranked.get(ranked.size() - 1);
			ranked = new ArrayList<>(ranked.subList(0, TalkPayloads.MAX_OPTIONS - 1));
			ranked.add(last);
		}
		List<TalkPayloads.Option> options = new ArrayList<>(ranked.size());
		for (Ranked r : ranked) {
			options.add(new TalkPayloads.Option(r.option.id(), r.option.label(), r.option.locked(), r.option.hint(), r.option.tooltip()));
		}
		Resident r = brain.resident;
		String title = r.age == AgeStage.CHILD ? "hearth.npc.child" : "hearth.npc.job." + r.job.getSerializedName();
		ServerPlayNetworking.send(player, new TalkPayloads.Show(session.folk.getId(), open, r.fullName(), title, brain.settlement.name(), speech, options));
		if (ServerPlayNetworking.canSend(player, TalkPayloads.Detail.TYPE)) {
			ServerPlayNetworking.send(player, detail(player, session.folk, brain, title, ranked, described));
		}
	}

	/** The resident card and each option's facts, for the richer conversation screens (docs/OVERLAY.md). */
	private static TalkPayloads.Detail detail(ServerPlayer player, Folk folk, FolkBrain brain, String title, List<Ranked> ranked,
		Map<String, DialogueProvider.Info> described) {
		Resident r = brain.resident;
		Settlement s = brain.settlement;
		dev.voidmc.hearth.api.PlayerSkills skills = dev.voidmc.hearth.api.HearthServices.playerSkills();
		List<TalkPayloads.OptionInfo> infos = new ArrayList<>(ranked.size());
		for (Ranked k : ranked) {
			DialogueProvider.Option o = k.option;
			DialogueProvider.Info given = described.get(o.id());
			DialogueProvider.Info info = OptionKinds.withCost(OptionKinds.complete(given != null ? given : OptionKinds.infer(o.id(), o.order())), o);
			int level = info.skill() == null ? -1 : skills.level(player, info.skill());
			infos.add(new TalkPayloads.OptionInfo(o.id(), info.kind(), info.odds(), info.cost(), info.skill() == null ? "" : info.skill(), level));
		}
		dev.voidmc.hearth.npc.person.NpcData data = dev.voidmc.hearth.npc.person.NpcData.of(r);
		List<String> traits = new ArrayList<>();
		for (dev.voidmc.hearth.npc.person.Trait t : data.traits()) {
			traits.add(t.getSerializedName());
		}
		java.util.Collections.sort(traits);
		dev.voidmc.hearth.api.PlotView work = r.work == Resident.NO_PLOT ? null : s.plot(r.work);
		String place = work == null ? "" : work.spec().archetype().getPath();
		dev.voidmc.hearth.standing.speech.TalkCard card = dev.voidmc.hearth.standing.speech.TalkCard.of(player, s, r);
		int minute = player.level() instanceof net.minecraft.server.level.ServerLevel level ? dev.voidmc.hearth.npc.NpcTime.minuteOfDay(level) : 0;
		return new TalkPayloads.Detail(folk.getId(), title, place, traits, data.mood(), card.opinion(), card.memoryKind(), card.memoryWeight(),
			card.standing(), card.title(), card.nextTitle(), card.nextAt(), minute, skills.level(player, dev.voidmc.hearth.api.PlayerSkills.SPEECH), infos);
	}

	private record Ranked(DialogueProvider.Option option, int index) {
	}

	/** Forgets conversations whose folk has gone (folded away, died) or whose player wandered off. */
	public void tick() {
		this.sessions.entrySet().removeIf(e -> {
			Folk f = e.getValue().folk;
			return f.isRemoved() || f.brain() == null;
		});
	}

	public @Nullable Entity talkingTo(ServerPlayer player) {
		Session s = this.sessions.get(player.getUUID());
		return s == null ? null : s.folk;
	}
}
