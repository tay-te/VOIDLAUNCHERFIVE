package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.PlayerSkills;
import dev.voidmc.hearth.api.QuestService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.npc.talk.DialogueTemplates;
import dev.voidmc.hearth.npc.talk.Greetings;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import dev.voidmc.hearth.sim.lives.LivesBook;
import dev.voidmc.hearth.sim.lives.LivesView;
import dev.voidmc.hearth.sim.lives.Threads;
import dev.voidmc.hearth.sim.lives.TownLives;
import dev.voidmc.hearth.social.Hooks;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The lives in conversation (docs/LIVING_WORLD_2.md §4.1, order band 50–79): a named character tells where their story
 * stands ("How are things with you?") and offers the choices of its beat — pay a debt, take him to the priest, speak for
 * the lovers — open to the first player who takes one; anyone can tell the player who the folk of note are and where
 * their stories stand (the People list, until the journal's People page is designed: requests/lwB2.md, F4); a head of a
 * feuding house can be talked into making peace; the bereaved can be comforted. No new screen.
 */
final class LivesDialogue implements DialogueProvider {
	static final String STORY = "lives:story";
	static final String CHOICE = "lives:choice/";
	static final String PEOPLE = "lives:people";
	static final String PEACE = "lives:peace";
	static final String CONDOLE = "lives:condole";
	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();
	/** Condolences and mediations tried today: player|resident|day. */
	private static final java.util.Set<String> TRIED = ConcurrentHashMap.newKeySet();

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (!Lives.config().lives().enabled()) {
			return;
		}
		TownLives.Notable n = LivesView.notable(s, r.id);
		if (n != null) {
			if (n.meet(player.getUUID())) {
				s.markDirty();
				LivesFolk.sendPeople(player);
			}
			givePending(player, s, r, n);
			out.add(new Option(STORY, Component.translatable("hearth.lives.talk.story"), 50));
			DialogueProvider.describe(STORY, Info.of("talk"));
			LivesBook.Beat beat = LivesView.beat(n);
			if (beat != null && !beat.end()) {
				int order = 55;
				long day = Folks.today(s);
				for (LivesBook.Choice c : beat.choices()) {
					if (c.speech() != null && TRIED.contains(player.getUUID() + "|" + r.id + "|" + day + "|" + c.id())) {
						continue;
					}
					String id = CHOICE + c.id();
					Component label = Component.translatable("hearth.lives.option." + n.thread + "." + c.id(), r.givenName);
					Component hint = null;
					String kind = "talk";
					long cost = c.cost();
					if (c.speech() != null) {
						StandingService.Odds odds = odds(player, s, r, c);
						hint = Component.literal(odds.percent() + " %");
						kind = c.speech().toLowerCase(Locale.ROOT);
						DialogueProvider.describe(id, new Info(kind, odds.percent(), cost > 0 ? cost : -1, PlayerSkills.SPEECH));
					} else {
						if (c.quest() != null) {
							kind = "quest";
						} else if (cost > 0) {
							kind = "service";
						}
						DialogueProvider.describe(id, new Info(kind, -1, cost > 0 ? cost : -1, null));
					}
					if (cost > 0 && hint == null) {
						hint = Hooks.coins(cost);
					}
					out.add(new Option(id, label, order++, false, hint, null));
				}
			}
		}
		if (r.age != AgeStage.CHILD && !LivesView.notables(s).isEmpty()) {
			out.add(new Option(PEOPLE, Component.translatable("hearth.lives.talk.people"), 60));
			DialogueProvider.describe(PEOPLE, Info.of("talk"));
		}
		long today = Folks.today(s);
		String key = player.getUUID() + "|" + r.id + "|" + today;
		if (Life.peek(r) != null && Life.mourning(r, today) && !TRIED.contains(key + "|c")) {
			out.add(new Option(CONDOLE, Component.translatable("hearth.lives.talk.condole"), 52));
			DialogueProvider.describe(CONDOLE, Info.of("talk"));
		}
		if (r.age != AgeStage.CHILD && !TRIED.contains(key + "|p")) {
			TownLives.Feud f = feudOf(s, r);
			if (f != null) {
				StandingService st = HearthServices.standing();
				double difficulty = peaceDifficulty(f);
				Component hint = null;
				int pct = -1;
				if (st != null) {
					pct = st.odds(player, s, r, StandingService.Speech.PERSUADE, difficulty, 0).percent();
					hint = Component.literal(pct + " %");
				}
				Resident other = s.resident(f.headA.equals(LivesView.head(s, r).id) ? f.headB : f.headA);
				String name = other == null ? "?" : other.familyName.isEmpty() ? other.givenName : other.familyName;
				out.add(new Option(PEACE, Component.translatable("hearth.lives.talk.peace", name), 58, false, hint, null));
				DialogueProvider.describe(PEACE, new Info("persuade", pct, -1, PlayerSkills.SPEECH));
			}
		}
	}

	static double peaceDifficulty(TownLives.Feud f) {
		return 0.15 + f.heat / 250.0;
	}

	private static TownLives.@Nullable Feud feudOf(Settlement s, Resident r) {
		var foes = LivesView.foes(s, r.id);
		if (foes == null || foes.isEmpty()) {
			return null;
		}
		UUID head = LivesView.head(s, r).id;
		for (TownLives.Feud f : LivesView.town(s).feuds) {
			if (f.involves(head)) {
				return f;
			}
		}
		return null;
	}

	private static StandingService.Odds odds(ServerPlayer player, Settlement s, Resident r, LivesBook.Choice c) {
		StandingService st = HearthServices.standing();
		StandingService.Speech kind = speech(c.speech());
		if (st == null) {
			return new StandingService.Odds(kind, Math.max(0.05, 0.75 - c.difficulty()), List.of());
		}
		return st.odds(player, s, r, kind, c.difficulty(), 0);
	}

	private static StandingService.Speech speech(@Nullable String id) {
		if (id == null) {
			return StandingService.Speech.PERSUADE;
		}
		return switch (id) {
			case "intimidate" -> StandingService.Speech.INTIMIDATE;
			case "flatter" -> StandingService.Speech.FLATTER;
			default -> StandingService.Speech.PERSUADE;
		};
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String optionId) {
		if (optionId.equals(STORY)) {
			TownLives.Notable n = LivesView.notable(s, r.id);
			ANSWER.put(player.getUUID(), n == null ? Component.empty() : story(player, s, r, n));
			return n != null;
		}
		if (optionId.equals(PEOPLE)) {
			ANSWER.put(player.getUUID(), LivesFolk.peopleLine(s, r));
			LivesFolk.sendPeople(player);
			return true;
		}
		long today = Folks.today(s);
		String key = player.getUUID() + "|" + r.id + "|" + today;
		if (optionId.equals(CONDOLE)) {
			if (!TRIED.add(key + "|c")) {
				return false;
			}
			StandingService st = HearthServices.standing();
			if (st != null) {
				st.remember(r.id, player.getUUID(), "lives:condoled", 3);
			}
			ANSWER.put(player.getUUID(), line(player, s, r, "lives.condolence", Component.translatable("hearth.lives.talk.condole.reply")));
			return true;
		}
		if (optionId.equals(PEACE)) {
			TownLives.Feud f = feudOf(s, r);
			if (f == null || !TRIED.add(key + "|p")) {
				return false;
			}
			StandingService st = HearthServices.standing();
			boolean ok = st == null ? player.getRandom().nextDouble() < 0.5
				: st.attempt(player, s, r, StandingService.Speech.PERSUADE, peaceDifficulty(f), 0);
			if (ok) {
				LivesView.makePeace(s, f, "mediation");
				Hooks.deed(player, s, "lives:mediated", 6, r.id, player.blockPosition(), true);
				ANSWER.put(player.getUUID(), line(player, s, r, "lives.peace.yes", Component.translatable("hearth.lives.talk.peace.yes")));
			} else {
				f.heat = Math.min(100, f.heat + 5);
				ANSWER.put(player.getUUID(), line(player, s, r, "lives.peace.no", Component.translatable("hearth.lives.talk.peace.no")));
			}
			s.markDirty();
			return true;
		}
		if (optionId.startsWith(CHOICE)) {
			return choose(player, s, r, optionId.substring(CHOICE.length()));
		}
		return false;
	}

	private boolean choose(ServerPlayer player, Settlement s, Resident r, String choiceId) {
		TownLives.Notable n = LivesView.notable(s, r.id);
		LivesBook.Beat beat = n == null ? null : LivesView.beat(n);
		LivesBook.Choice c = null;
		if (beat != null) {
			for (LivesBook.Choice x : beat.choices()) {
				if (x.id().equals(choiceId)) {
					c = x;
				}
			}
		}
		if (c == null) {
			return false; // someone else took the choice first, or the story moved on
		}
		long today = Folks.today(s);
		String name = player.getName().getString();
		List<Threads.PlayerEffect> effects;
		if (c.quest() != null) {
			QuestService q = HearthServices.quests();
			CompoundTag vars = new CompoundTag();
			vars.putString("thread", n.thread);
			vars.putString("giver", r.givenName);
			if (q != null && q.offerPersonal(player, s, r, c.quest(), vars)) {
				n.flags.add("offered:" + c.quest());
				effects = Threads.choose(s, LivesView.town(s), n, c, c.to(), true, player.getUUID(), name, today);
			} else if (c.fallback() != null) {
				// no personal quest to be had (lane B1's offerPersonal not there): the plain way, for coin
				if (!Hooks.charge(player, c.fallback().cost(), r, s)) {
					return true;
				}
				effects = Threads.choose(s, LivesView.town(s), n, c, c.fallback().to(), true, player.getUUID(), name, today);
			} else {
				return false;
			}
		} else {
			if (c.cost() > 0 && !Hooks.charge(player, c.cost(), r, s)) {
				return true;
			}
			boolean ok = true;
			if (c.speech() != null) {
				// one try a day at talking them round
				if (!TRIED.add(player.getUUID() + "|" + r.id + "|" + today + "|" + c.id())) {
					return false;
				}
				StandingService st = HearthServices.standing();
				ok = st == null ? player.getRandom().nextDouble() < odds(player, s, r, c).chance()
					: st.attempt(player, s, r, speech(c.speech()), c.difficulty(), 0);
			}
			effects = Threads.choose(s, LivesView.town(s), n, c, null, ok, player.getUUID(), name, today);
			if (!ok) {
				s.markDirty();
				ANSWER.put(player.getUUID(), line(player, s, r, "lives.refused", Component.translatable("hearth.lives.talk.failed")));
				LivesFolk.sendPeople(player);
				return true;
			}
		}
		s.markDirty();
		give(player, s, r, n, effects);
		ANSWER.put(player.getUUID(), story(player, s, r, n));
		LivesFolk.sendPeople(player);
		return true;
	}

	/** Pending effects for this player (a thread that moved on after their turn): given now, with a word. */
	private static void givePending(ServerPlayer player, Settlement s, Resident r, TownLives.Notable n) {
		List<Threads.PlayerEffect> pending = Threads.takePending(n, player.getUUID());
		if (!pending.isEmpty()) {
			give(player, s, r, n, pending);
			s.markDirty();
			Hooks.reply(player, r, s, Component.translatable("hearth.lives.talk.reward"));
		}
	}

	static void give(ServerPlayer player, Settlement s, Resident r, TownLives.Notable n, List<Threads.PlayerEffect> effects) {
		StandingService st = HearthServices.standing();
		for (Threads.PlayerEffect e : effects) {
			switch (e.type()) {
				case "gift" -> {
					Identifier id = Identifier.tryParse(e.item());
					Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
					if (item != null) {
						ItemStack stack = new ItemStack(item, Math.max(1, e.amount()));
						if (!player.getInventory().add(stack)) {
							player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
						}
					}
				}
				case "coin" -> Hooks.pay(player, e.amount());
				case "memory" -> {
					if (st != null) {
						st.remember(r.id, player.getUUID(), "lives:" + n.thread, e.amount());
					}
				}
				case "deed" -> Hooks.deed(player, s, e.kind().isEmpty() ? "lives:helped" : e.kind(), e.amount(), r.id, player.blockPosition(), true);
				default -> {
				}
			}
		}
	}

	/** What the character says of where their story stands now. */
	static Component story(ServerPlayer player, Settlement s, Resident r, TownLives.Notable n) {
		Component beat = line(player, s, r, "thread." + n.thread + "." + n.beat, Component.translatable("hearth.lives.story." + n.thread + "." + n.beat, r.givenName));
		if (n.by == null) {
			return beat;
		}
		// they remember who moved their story, and say so: to that player, and to the others who ask (§4.5)
		String cat = n.by.equals(player.getUUID()) ? "lives.story.by_you" : "lives.story.by_other";
		Component more = line(player, s, r, cat, Component.empty());
		return more.getString().isEmpty() ? beat : Component.empty().append(beat).append(" ").append(more);
	}

	/** A line of a dialogue category in the resident's voice, or the fallback. */
	static Component line(ServerPlayer player, Settlement s, Resident r, String category, Component fallback) {
		var level = player.level();
		DialogueTemplates.Context ctx = Greetings.context(level, s, r, player, player.blockPosition());
		String text = DialogueTemplates.pick(category, ctx, player.getRandom());
		if (text == null) {
			return fallback;
		}
		Map<String, String> v = Greetings.values(s, r, player);
		TownLives.Notable n = LivesView.notable(s, r.id);
		if (n != null && n.partner != null) {
			Resident p = s.resident(n.partner);
			v.put("partner", p == null ? "them" : p.givenName);
		} else {
			v.put("partner", "them");
		}
		v.put("helper", n != null && !n.byName.isEmpty() ? n.byName : "a stranger");
		return Component.literal(DialogueTemplates.fill(text, v));
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement settlement, Resident resident, String optionId) {
		return ANSWER.remove(player.getUUID());
	}

	static void clear() {
		ANSWER.clear();
		TRIED.clear();
	}
}
