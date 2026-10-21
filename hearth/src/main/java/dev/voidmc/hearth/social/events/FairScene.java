package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.social.Hooks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The fair (docs/LIVING_WORLD_2.md §3.1, §3.3): three times the stalls (with the stall wares on their counters), a
 * juggler and a fire-eater on the stage, and the <b>archery contest</b> — vanilla target blocks at 20, 30 and 40 blocks
 * from the mark; ten arrows each; a hit scores the target's own strength (1–15, by how near the middle). Three folk
 * shoot (their scores the books'); a player enters for a fee at any of the town's folk ("Enter the archery contest")
 * and gets ten marked arrows (and a bow, lent). The table goes to the action bar of those at the range, and to the
 * contest line under the quest HUD (F6, {@link ContestLines}).
 */
final class FairScene implements SceneDirector {
	static final FairScene INSTANCE = new FairScene();
	static final String ARROW_TAG = "hearth.contest";
	static final String ITEM_KEY = "hearth_contest";

	private FairScene() {
	}

	/** A player's go at the targets: arrows loosed and the score so far. */
	static final class Entrant {
		final String name;
		int shots;
		int score;
		/** What each arrow scored as it landed (0 a miss), for the contest line (F6). */
		final List<Integer> arrows = new ArrayList<>(10);

		Entrant(String name) {
			this.name = name;
		}
	}

	static Map<UUID, Entrant> entrants(SceneRun run) {
		Map<UUID, Entrant> m = run.get("entrants");
		if (m == null) {
			m = new LinkedHashMap<>();
			run.put("entrants", m);
		}
		return m;
	}

	/** Whether the contest is open now (from its rule's {@code contest_from} to {@code contest_to} minutes). */
	static boolean open(SceneRun run) {
		return run.minute >= run.rule.param("contest_from", 30) && run.minute < run.rule.param("contest_to", 240) && run.get("targets") != null;
	}

	/** A player enters: ten marked arrows, and a lent bow if they carry none. */
	static boolean enter(SceneRun run, ServerPlayer player) {
		Map<UUID, Entrant> m = entrants(run);
		if (m.containsKey(player.getUUID()) || m.size() >= 4) {
			return false; // four players a round at most (§3.5)
		}
		m.put(player.getUUID(), new Entrant(player.getName().getString()));
		ItemStack arrows = new ItemStack(Items.ARROW, 10);
		mark(arrows, run);
		player.getInventory().add(arrows);
		if (!player.getInventory().contains(s -> s.is(Items.BOW) || s.is(Items.CROSSBOW))) {
			ItemStack bow = new ItemStack(Items.BOW);
			mark(bow, run);
			player.getInventory().add(bow);
		}
		return true;
	}

	private static void mark(ItemStack stack, SceneRun run) {
		net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
		t.putString(ITEM_KEY, run.key());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
	}

	/** An arrow came into the world: a contestant's, loosed near the mark, is the contest's. */
	static void arrowLoaded(Entity e) {
		if (!(e instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer p)) {
			return;
		}
		for (SceneRun run : Scenes.allRuns()) {
			if (run.event.kind() != EventKind.FAIR || !open(run) || run.level() != p.level()) {
				continue;
			}
			Entrant en = entrants(run).get(p.getUUID());
			if (en != null && en.shots < 10 && p.blockPosition().distSqr(run.venue("range")) <= 12 * 12) {
				arrow.addTag(ARROW_TAG);
				en.shots++;
				return;
			}
		}
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		ServerLevel level = run.level();
		// the stage: a juggler and a fire-eater
		for (UUID id : run.role("entertainer")) {
			var f = EventDirector.folk(id);
			if (f != null && f.entity().blockPosition().distSqr(run.venue("stage")) < 36) {
				EventDressings.particles(level, (gameTime / 20) % 2 == 0 ? "juggle" : "fire_eater", f.entity().blockPosition().above(), 3);
			}
		}
		if (!open(run)) {
			return;
		}
		List<BlockPos> targets = run.get("targets");
		// arrows that have come to rest in a target score its strength; any other that has stopped is a miss
		BlockPos mark = run.venue("range");
		for (AbstractArrow a : level.getEntitiesOfClass(AbstractArrow.class, new AABB(mark).inflate(56), x -> x.entityTags().contains(ARROW_TAG))) {
			if (a.getDeltaMovement().lengthSqr() > 1.0E-4 && a.tickCount < 200) {
				continue;
			}
			int score = 0;
			for (BlockPos t : targets) {
				score = Math.max(score, hit(t, a.position()));
			}
			if (a.getOwner() instanceof ServerPlayer p) {
				Entrant en = entrants(run).get(p.getUUID());
				if (en != null) {
					en.score += score;
					en.arrows.add(score);
					p.sendSystemMessage(Component.translatable(score > 0 ? "hearth.events.archery.hit" : "hearth.events.archery.miss", score, en.score,
						en.shots), true);
					run.iv.best(p.getUUID(), en.name, "archery", en.score);
					if (score > 0) {
						level.playSound(null, a.blockPosition(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7F, 1.2F);
					}
				}
			}
			a.discard();
		}
		// the folk archers loose in turn: a twang and a thud, their score growing as the afternoon goes
		List<UUID> comps = run.role("competitors");
		if (!comps.isEmpty() && gameTime % 60 == 0) {
			var f = EventDirector.folk(comps.get((int) ((gameTime / 60) % comps.size())));
			if (f != null) {
				f.swing();
				level.playSound(null, f.entity().blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.8F, 1.0F);
				if (!targets.isEmpty()) {
					BlockPos t = targets.get((int) ((gameTime / 60) % targets.size()));
					level.playSound(null, t, SoundEvents.ARROW_HIT, SoundSource.NEUTRAL, 0.8F, 1.0F);
				}
			}
		}
		if (gameTime % 200 == 0) {
			Component table = table(run);
			for (ServerPlayer p : EventDirector.playersNear(level, mark, 40)) {
				p.sendSystemMessage(table, true);
			}
		}
	}

	/** The contest's table: the folk archers (their scores as far as they have got) and the players. */
	static Component table(SceneRun run) {
		double share = Math.min(1, Math.max(0, (run.minute - run.rule.param("contest_from", 30)) / Math.max(1, run.rule.param("contest_to", 240)
			- run.rule.param("contest_from", 30))));
		List<String> parts = new ArrayList<>();
		for (Outcomes.Archer a : Outcomes.archers(run.event)) {
			parts.add(a.name() + " " + Math.round(a.score() * share));
		}
		for (Entrant e : entrants(run).values()) {
			parts.add(e.name + " " + e.score + " (" + e.shots + "/10)");
		}
		return Component.translatable("hearth.events.archery.table", String.join(" · ", parts)).withStyle(ChatFormatting.GOLD);
	}

	/** The strength a hit at {@code at} scores on a target block at {@code t} (0 when it is not in the target). */
	static int hit(BlockPos t, Vec3 at) {
		double x = at.x - t.getX();
		double y = at.y - t.getY();
		double z = at.z - t.getZ();
		double m = 0.2;
		if (x < -m || x > 1 + m || y < -m || y > 1 + m || z < -m || z > 1 + m) {
			return 0;
		}
		// the face it struck is the one it is nearest; the score is how near the middle of that face
		double dx = Math.min(Math.abs(x), Math.abs(1 - x));
		double dz = Math.min(Math.abs(z), Math.abs(1 - z));
		double off = dx < dz ? Math.max(Math.abs(y - 0.5), Math.abs(z - 0.5)) : Math.max(Math.abs(y - 0.5), Math.abs(x - 0.5));
		return Outcomes.strength(off);
	}

	@Override
	public void end(SceneRun run, Settlement s) {
		Map<UUID, Entrant> m = entrants(run);
		if (m.isEmpty()) {
			return;
		}
		int bestFolk = 0;
		for (Outcomes.Archer a : Outcomes.archers(run.event)) {
			bestFolk = Math.max(bestFolk, a.score());
		}
		for (Map.Entry<UUID, Entrant> e : m.entrySet()) {
			ServerPlayer p = run.level().getServer().getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				continue;
			}
			// what was lent is taken back
			var inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				CustomData d = inv.getItem(i).get(DataComponents.CUSTOM_DATA);
				if (d != null && d.copyTag().getStringOr(ITEM_KEY, "").equals(run.key())) {
					inv.setItem(i, ItemStack.EMPTY);
				}
			}
			boolean won = e.getValue().score > bestFolk && e.getValue().score >= run.iv.get("archery");
			ContestLines.result(p, ContestLines.archery(run, e.getKey(), won ? "won" : "lost"));
			if (won) {
				long purse = (long) run.rule.param("purse", 30);
				Hooks.pay(p, purse);
				Hooks.deed(p, s, "social:contest_won", 3, null, run.anchor, true);
				p.sendSystemMessage(Component.translatable("hearth.events.archery.won", purse).withStyle(ChatFormatting.GOLD));
			} else {
				p.sendSystemMessage(Component.translatable("hearth.events.archery.lost", e.getValue().score, bestFolk));
			}
		}
	}
}
