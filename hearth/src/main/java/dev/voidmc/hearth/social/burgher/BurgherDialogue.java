package dev.voidmc.hearth.social.burgher;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.DialogueProvider;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.PlotView;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.SceneService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.economy.property.PropertyNames;
import dev.voidmc.hearth.npc.companion.PlaceConfig;
import dev.voidmc.hearth.social.Hooks;
import dev.voidmc.hearth.social.SocialModule;
import dev.voidmc.hearth.social.office.Office;
import dev.voidmc.hearth.social.office.Officials;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * The burghers at the reeve's (docs/LIVING_WORLD_2.md §8.1; order 176–178 and the council 195–198): the burgher's oath —
 * its fee, and why not yet when the player is not ready — and, when the council sits, a sworn burgher's say in which of
 * the next public works the town builds first. The oath is a small scene through the town events' runner when it is
 * there (lane B1), else a beat of the conversation: the reeve speaks the oath of the town's culture and the player swears.
 */
public final class BurgherDialogue implements DialogueProvider {
	static final String P = "burgher:";
	static final String OATH = P + "oath";
	static final String RIGHTS = P + "rights";
	static final String VOTE = P + "vote/";
	public static final net.minecraft.resources.Identifier SCENE = Hearth.id("burgher_oath");

	private static final Map<UUID, Component> ANSWER = new ConcurrentHashMap<>();

	@Override
	public void collect(ServerPlayer player, Settlement s, Resident r, List<Option> out) {
		if (!reeve(s, r) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		PlaceConfig.Burghers cfg = Burghers.cfg();
		BurgherBook book = s.data(Burghers.KEY);
		long today = today(level);
		UUID me = player.getUUID();
		if (book.isSworn(me)) {
			out.add(new Option(RIGHTS, Component.translatable("hearth.burgher.talk.rights"), 178));
			if (book.sittingOn(today)) {
				int i = 0;
				for (int plot : book.table) {
					PlotView p = s.plot(plot);
					if (p == null) {
						continue;
					}
					boolean mine = Integer.valueOf(plot).equals(book.votes.get(me));
					out.add(new Option(VOTE + plot, Component.translatable(mine ? "hearth.burgher.talk.voted" : "hearth.burgher.talk.vote",
						PropertyNames.lower(p.spec().archetype().getPath())), 195 + i++, mine, null, null));
				}
			}
			return;
		}
		if (book.owners.containsKey(me) || owns(s, me)) {
			long fee = BurgherRules.fee(s.tier(), cfg);
			BurgherRules.Oath why = check(level, s, book, player, today);
			boolean ok = why == BurgherRules.Oath.OK;
			out.add(new Option(OATH, Component.translatable("hearth.burgher.talk.oath", Hooks.coins(fee)), 176, !ok, null,
				ok ? null : reason(why, s, book, me, today, cfg)));
			DialogueProvider.describe(OATH, new DialogueProvider.Info("service", -1, fee, null));
		}
	}

	@Override
	public boolean select(ServerPlayer player, Settlement s, Resident r, String id) {
		if (!id.startsWith(P) || !reeve(s, r) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ANSWER.remove(player.getUUID());
		BurgherBook book = s.data(Burghers.KEY);
		long today = today(level);
		UUID me = player.getUUID();
		if (id.equals(OATH)) {
			if (check(level, s, book, player, today) != BurgherRules.Oath.OK) {
				return false;
			}
			long fee = BurgherRules.fee(s.tier(), Burghers.cfg());
			if (!Hooks.charge(player, fee, r, s)) {
				return true;
			}
			s.deposit(fee);
			book.sworn.put(me, today);
			s.markDirty();
			// the oath before the town: a scene where the events' runner is there, a beat of the talk where it is not
			SceneService scenes = HearthServices.scenes();
			CompoundTag args = new CompoundTag();
			args.putString("player", me.toString());
			boolean staged = scenes != null && scenes.stage(s, SCENE, List.of(r.id), player, args);
			player.sendSystemMessage(Component.translatable("hearth.burgher.oath.text." + s.culture().getSerializedName())
				.withStyle(net.minecraft.ChatFormatting.ITALIC, net.minecraft.ChatFormatting.GRAY));
			Hooks.deed(player, s, "social:burgher_oath", 5, r.id, player.blockPosition(), true);
			answer(player, Component.translatable(staged ? "hearth.burgher.talk.sworn_scene" : "hearth.burgher.talk.sworn", player.getName(), s.name()));
			dev.voidmc.hearth.npc.companion.PlaceSummary.send(player);
			return true;
		}
		if (id.equals(RIGHTS)) {
			answer(player, Component.translatable("hearth.burgher.talk.rights_text", Math.round(Burghers.cfg().dues() * 100)));
			return true;
		}
		if (id.startsWith(VOTE)) {
			int plot;
			try {
				plot = Integer.parseInt(id.substring(VOTE.length()));
			} catch (NumberFormatException e) {
				return false;
			}
			if (!BurgherRules.vote(book, me, plot, today)) {
				return false;
			}
			s.markDirty();
			PlotView p = s.plot(plot);
			answer(player, Component.translatable("hearth.burgher.talk.vote_taken", p == null ? "?" : PropertyNames.lower(p.spec().archetype().getPath())));
			return true;
		}
		return false;
	}

	@Override
	public @Nullable Component reply(ServerPlayer player, Settlement s, Resident r, String id) {
		return id.startsWith(P) ? ANSWER.remove(player.getUUID()) : null;
	}

	private static void answer(ServerPlayer player, Component line) {
		ANSWER.put(player.getUUID(), line);
	}

	static boolean reeve(Settlement s, Resident r) {
		return Officials.officeOf(s.data(SocialModule.LEDGER), r.id) == Office.REEVE;
	}

	private static boolean owns(Settlement s, UUID player) {
		for (PlotView p : s.plots()) {
			if (player.equals(p.owner())) {
				return true;
			}
		}
		return false;
	}

	static BurgherRules.Oath check(ServerLevel level, Settlement s, BurgherBook book, ServerPlayer player, long today) {
		if (!book.owners.containsKey(player.getUUID()) && owns(s, player.getUUID())) {
			// an owner the day's books have not seen yet: from today
			book.owners.put(player.getUUID(), today);
			s.markDirty();
		}
		StandingService standing = HearthServices.standing();
		int st = standing == null ? s.reputation(player.getUUID()) : standing.standing(s.id(), player.getUUID());
		boolean market = HearthServices.calendar().marketDay(s, today);
		return BurgherRules.check(book, player.getUUID(), st, book.owners.get(player.getUUID()), today, market, Burghers.keepsMarket(s, today),
			Burghers.cfg());
	}

	private static Component reason(BurgherRules.Oath why, Settlement s, BurgherBook book, UUID me, long today, PlaceConfig.Burghers cfg) {
		return switch (why) {
			case STANDING -> Component.translatable("hearth.burgher.why.standing", cfg.minStanding());
			case NOT_LONG_ENOUGH -> Component.translatable("hearth.burgher.why.held", cfg.heldDays(),
				Math.max(0, cfg.heldDays() - (today - book.owners.getOrDefault(me, today))));
			case NOT_MARKET_DAY -> Component.translatable("hearth.burgher.why.market");
			case NOT_OWNER -> Component.translatable("hearth.burgher.why.owner");
			default -> Component.translatable("hearth.burgher.why.already");
		};
	}

	private static long today(ServerLevel level) {
		HearthWorld w = HearthWorld.of(level);
		return w == null ? 0 : w.day();
	}
}
