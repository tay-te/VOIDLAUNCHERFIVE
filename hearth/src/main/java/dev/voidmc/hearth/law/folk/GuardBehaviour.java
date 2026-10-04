package dev.voidmc.hearth.law.folk;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService.FolkBehaviour;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.law.Law;
import dev.voidmc.hearth.law.LawConfig;
import dev.voidmc.hearth.law.crime.Presence;
import dev.voidmc.hearth.law.justice.Confrontations;
import dev.voidmc.hearth.law.justice.Jail;
import dev.voidmc.hearth.law.justice.Justice;
import dev.voidmc.hearth.law.justice.Warrant;
import dev.voidmc.hearth.law.state.LawState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Guards on duty (docs/LIVING_WORLD.md §4), a {@link FolkBehaviour} for GUARD and JAILER residents. A guard who sees a
 * wanted player walks up and confronts them ({@link Confrontations}): pay, come quietly, the pillory, talk, bribe, run or
 * fight. Suspected players are stopped and searched. Those who run or fight are chased and beaten down until they yield
 * (then taken in); an outlaw is attacked on sight. At most two guards go after one player at a time.
 */
public final class GuardBehaviour implements FolkBehaviour {
	public static final Identifier ID = Hearth.id("law/guard");
	/** How far a guard notices a wanted face. */
	static final double NOTICE = 28.0;
	/** Beyond this a fleeing player has got away. */
	static final double LOSE = 48.0;
	static final int MAX_GUARDS_PER_PLAYER = 2;
	/** Days between stop-and-searches of one player in one town. */
	static final long SEARCH_COOLDOWN_TICKS = 24000;

	private final Map<UUID, Job> jobs = new HashMap<>();
	/** The player a guard decided to go after in {@link #wants}, picked up by {@link #start}. */
	private final Map<UUID, Pick> picked = new HashMap<>();
	private final Map<String, Long> searched = new HashMap<>();

	private enum Mode {
		APPROACH, TALK, CHASE, HUNT
	}

	private record Pick(UUID player, Confrontations.Kind kind, boolean outlaw) {
	}

	private static final class Job {
		final UUID player;
		final Confrontations.Kind kind;
		Mode mode;
		int ticks;
		int repath;
		int unseen;
		int cooldown;

		Job(UUID player, Confrontations.Kind kind, Mode mode) {
			this.player = player;
			this.kind = kind;
			this.mode = mode;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return 320;
	}

	@Override
	public boolean wants(FolkHandle folk) {
		JobType job = folk.resident().job;
		if (job != JobType.GUARD && job != JobType.JAILER || !folk.awake() || !Law.config().enabled()) {
			return false;
		}
		Settlement s = folk.settlement();
		MinecraftServer server = folk.entity().level().getServer();
		if (server == null || !Presence.anyone()) {
			return false;
		}
		LawState state = Law.state(s);
		if (state.warrants().isEmpty() && state.suspicionMap().isEmpty()) {
			return false;
		}
		LawConfig.Justice cfg = Law.config().justice();
		for (ServerPlayer player : Presence.playersIn(server, s.id())) {
			if (Law.exempt(player) || Jail.serving(player) || !player.isAlive() || folk.entity().distanceTo(player) > NOTICE) {
				continue;
			}
			Confrontations.Talk talk = Confrontations.of(player.getUUID());
			if (talk != null && talk.state == Confrontations.State.OPEN || chasing(player.getUUID()) >= MAX_GUARDS_PER_PLAYER) {
				continue;
			}
			Warrant w = state.warrant(player.getUUID());
			Pick pick = null;
			if (w != null && w.outlaw() && cfg.outlaws()) {
				pick = new Pick(player.getUUID(), Confrontations.Kind.ARREST, true);
			} else if (w != null && w.active()) {
				pick = new Pick(player.getUUID(), Confrontations.Kind.ARREST, false);
			} else if (state.suspicion(player.getUUID()) >= cfg.suspicionSearchThreshold() && !searchedRecently(s, player, folk.gameTime())) {
				pick = new Pick(player.getUUID(), Confrontations.Kind.SEARCH, false);
			}
			if (pick != null && folk.canSee(player)) {
				this.picked.put(folk.resident().id, pick);
				return true;
			}
		}
		return false;
	}

	private boolean searchedRecently(Settlement s, ServerPlayer player, long now) {
		Long at = this.searched.get(s.id() + ":" + player.getUUID());
		return at != null && now - at < SEARCH_COOLDOWN_TICKS && now >= at;
	}

	private int chasing(UUID player) {
		int n = 0;
		for (Job j : this.jobs.values()) {
			if (j.player.equals(player)) {
				n++;
			}
		}
		return n;
	}

	@Override
	public void start(FolkHandle folk) {
		Pick pick = this.picked.remove(folk.resident().id);
		if (pick == null) {
			return;
		}
		Mode mode = pick.outlaw ? Mode.HUNT : Mode.APPROACH;
		this.jobs.put(folk.resident().id, new Job(pick.player, pick.kind, mode));
		folk.hold(new ItemStack(Items.IRON_SWORD));
		ServerPlayer player = player(folk, pick.player);
		if (player != null) {
			folk.lookAt(player);
			folk.say(Component.translatable(pick.outlaw ? "hearth.law.say.outlaw" : pick.kind == Confrontations.Kind.SEARCH ? "hearth.law.say.stop"
				: "hearth.law.say.halt"), null);
		}
		if (pick.kind == Confrontations.Kind.SEARCH && player != null) {
			this.searched.put(folk.settlement().id() + ":" + player.getUUID(), folk.gameTime());
		}
	}

	@Override
	public boolean tick(FolkHandle folk) {
		Job job = this.jobs.get(folk.resident().id);
		if (job == null) {
			return false;
		}
		ServerPlayer player = player(folk, job.player);
		if (player == null || !player.isAlive() || Law.exempt(player) || Jail.serving(player)) {
			return false;
		}
		job.ticks++;
		job.cooldown--;
		job.repath--;
		double d = folk.entity().distanceTo(player);
		switch (job.mode) {
			case APPROACH -> {
				if (d > LOSE || job.ticks > 20 * 40) {
					return false;
				}
				if (d <= 3.5) {
					folk.stop();
					Confrontations.open(player, folk, job.kind);
					job.mode = Mode.TALK;
					job.ticks = 0;
				} else if (job.repath <= 0) {
					folk.moveTo(player.blockPosition(), 1.0);
					job.repath = 10;
				}
			}
			case TALK -> {
				folk.lookAt(player);
				Confrontations.Talk talk = Confrontations.of(player.getUUID());
				if (talk == null || !talk.folk.equals(folk.resident().id)) {
					return false;
				}
				switch (talk.state) {
					case OPEN -> {
						return true;
					}
					case RESISTED -> {
						Confrontations.release(player.getUUID());
						if (!Law.config().justice().guardsAttack()) {
							folk.say(Component.translatable("hearth.law.say.come_back"), null);
							return false;
						}
						job.mode = Mode.CHASE;
						job.ticks = 0;
					}
					default -> {
						Confrontations.release(player.getUUID());
						return false;
					}
				}
			}
			case CHASE, HUNT -> {
				return chase(folk, player, job, d);
			}
		}
		return true;
	}

	/** Runs the player down and strikes until they yield (a chase) or die (an outlaw); gives up when they get away. */
	private boolean chase(FolkHandle folk, ServerPlayer player, Job job, double d) {
		if (d > LOSE) {
			escaped(folk, player);
			return false;
		}
		boolean sees = folk.canSee(player);
		job.unseen = sees ? 0 : job.unseen + 1;
		if (job.unseen > 20 * 12 || job.ticks > 20 * 120) {
			escaped(folk, player);
			return false;
		}
		folk.lookAt(player);
		if (d <= 2.6) {
			if (job.cooldown <= 0) {
				folk.swing();
				folk.entity().doHurtTarget((ServerLevel) folk.entity().level(), player);
				job.cooldown = 18;
			}
			if (job.mode == Mode.CHASE && player.getHealth() <= Law.config().justice().yieldHealth()) {
				// beaten: taken in
				folk.say(Component.translatable("hearth.law.say.yield"), player);
				Settlement s = folk.settlement();
				Justice.submit(player, s, false);
				return false;
			}
		} else if (job.repath <= 0) {
			folk.moveTo(player.blockPosition(), 1.2);
			job.repath = 8;
		}
		return true;
	}

	private static void escaped(FolkHandle folk, ServerPlayer player) {
		folk.say(Component.translatable("hearth.law.say.escaped"), null);
		Warrant w = Law.state(folk.settlement()).warrant(player.getUUID());
		if (w != null) {
			w.addEvaded();
			folk.settlement().markDirty();
		}
	}

	@Override
	public void stop(FolkHandle folk) {
		Job job = this.jobs.remove(folk.resident().id);
		this.picked.remove(folk.resident().id);
		folk.stop();
		folk.hold(ItemStack.EMPTY);
		if (job != null && job.mode == Mode.TALK) {
			ServerPlayer player = player(folk, job.player);
			Confrontations.Talk talk = player == null ? null : Confrontations.of(player.getUUID());
			if (talk != null && talk.folk.equals(folk.resident().id)) {
				if (talk.state == Confrontations.State.OPEN) {
					Confrontations.close(player);
				} else {
					Confrontations.release(player.getUUID());
				}
			}
		}
	}

	private static @Nullable ServerPlayer player(FolkHandle folk, UUID id) {
		MinecraftServer server = folk.entity().level().getServer();
		return server == null ? null : server.getPlayerList().getPlayer(id);
	}

	/** Forgets everything (server stop). */
	public void clear() {
		this.jobs.clear();
		this.picked.clear();
		this.searched.clear();
	}
}
