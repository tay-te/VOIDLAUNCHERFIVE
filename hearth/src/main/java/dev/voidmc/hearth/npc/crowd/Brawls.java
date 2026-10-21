package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.EconomyService;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.NpcService.FolkHandle;
import dev.voidmc.hearth.api.Settlement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Brawls (docs/LIVING_WORLD_2.md §5.1, §5.3; {@code CrowdService.brawl}): two folk come to blows with their fists —
 * swings, knockback, a point of damage a blow — and both stop when one is down to half health. A ring gathers (a
 * spectacle), and the nearest guard on duty comes to part them. A player who joins in with bare fists is brawling, not
 * assaulting anyone, and pays the guard a small fine ({@code brawls.fine_bits}) when it is broken up. Nothing is saved.
 */
public final class Brawls {
	private final CrowdDirector director;
	private final Map<UUID, Brawl> byFolk = new HashMap<>();
	private final List<Brawl> all = new ArrayList<>();

	Brawls(CrowdDirector director) {
		this.director = director;
	}

	public enum End {
		/** One of them is down to half health. */
		YIELDED,
		/** A guard came between them. */
		PARTED,
		/** Nobody stopped it, and they tired. */
		TIRED,
		/** One of them is gone (folded away, hurt by something else, asleep). */
		GONE
	}

	public static final class Brawl {
		public final long settlement;
		public final UUID a;
		public final UUID b;
		public final String why;
		public final long started;
		public final long until;
		public final BlockPos at;
		public @Nullable UUID guard;
		public final Set<UUID> joined = new HashSet<>();
		public @Nullable End ended;

		Brawl(long settlement, UUID a, UUID b, String why, long started, long until, BlockPos at) {
			this.settlement = settlement;
			this.a = a;
			this.b = b;
			this.why = why;
			this.started = started;
			this.until = until;
			this.at = at;
		}

		public UUID opponent(UUID me) {
			return me.equals(this.a) ? this.b : this.a;
		}

		public boolean fighter(UUID me) {
			return me.equals(this.a) || me.equals(this.b);
		}
	}

	/** Two residents come to blows; false if they cannot now (not both about and awake, a guard, a child, too far apart). */
	public boolean start(Settlement s, UUID a, UUID b, String why) {
		CrowdConfig.Brawls cfg = Crowds.config().brawls();
		NpcService npc = HearthServices.npc();
		if (!cfg.enabled() || !Crowds.config().crowds().enabled() || npc == null || a.equals(b) || this.byFolk.containsKey(a) || this.byFolk.containsKey(b)) {
			return false;
		}
		int here = 0;
		for (Brawl x : this.all) {
			if (x.settlement == s.id()) {
				here++;
			}
		}
		if (here >= cfg.maxPerTown()) {
			return false;
		}
		FolkHandle fa = npc.folk(a);
		FolkHandle fb = npc.folk(b);
		if (!fighter(fa, s) || !fighter(fb, s) || fa.entity().level() != fb.entity().level() || fa.entity().distanceTo(fb.entity()) > 24) {
			return false;
		}
		ServerLevel level = (ServerLevel) fa.entity().level();
		long now = level.getGameTime();
		BlockPos at = BlockPos.containing((fa.entity().getX() + fb.entity().getX()) / 2, Math.min(fa.entity().getY(), fb.entity().getY()),
			(fa.entity().getZ() + fb.entity().getZ()) / 2);
		Brawl brawl = new Brawl(s.id(), a, b, why, now, now + cfg.maxSeconds() * 20L, at);
		brawl.guard = nearestGuard(npc, level, s, at);
		this.all.add(brawl);
		this.byFolk.put(a, brawl);
		this.byFolk.put(b, brawl);
		if (brawl.guard != null) {
			this.byFolk.put(brawl.guard, brawl);
		}
		this.director.spectacle(s, level, at, "brawl", cfg.maxSeconds(), 8);
		CrowdStats.BRAWLS.increment();
		return true;
	}

	private static boolean fighter(@Nullable FolkHandle f, Settlement s) {
		if (f == null || !f.awake() || f.settlement().id() != s.id() || f.resident().age != AgeStage.ADULT) {
			return false;
		}
		JobType job = f.resident().job;
		return job != JobType.GUARD && job != JobType.JAILER && CrowdDirector.available(f);
	}

	private static @Nullable UUID nearestGuard(NpcService npc, ServerLevel level, Settlement s, BlockPos at) {
		for (FolkHandle h : npc.folkNear(level, net.minecraft.world.phys.Vec3.atCenterOf(at), 48)) {
			JobType job = h.resident().job;
			if ((job == JobType.GUARD || job == JobType.JAILER) && h.awake() && h.settlement().id() == s.id() && CrowdDirector.available(h)) {
				return h.resident().id;
			}
		}
		return null;
	}

	/** The brawl a folk is in (as a fighter or the guard sent to part it), or null. */
	public @Nullable Brawl of(UUID folk) {
		Brawl b = this.byFolk.get(folk);
		return b == null || b.ended != null ? null : b;
	}

	public List<Brawl> active() {
		return List.copyOf(this.all);
	}

	/** Ends a brawl: everyone stops, and a player who joined with their fists is fined if a guard parted them. */
	public void end(Brawl b, End how, @Nullable MinecraftServer server) {
		if (b.ended != null) {
			return;
		}
		b.ended = how;
		this.all.remove(b);
		this.byFolk.values().removeIf(x -> x == b);
		if (how != End.PARTED || server == null || b.joined.isEmpty()) {
			return;
		}
		EconomyService economy = HearthServices.economy();
		long fine = Crowds.config().brawls().fineBits();
		for (UUID id : b.joined) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || fine <= 0) {
				continue;
			}
			boolean paid = economy != null && economy.charge(p, fine);
			p.sendSystemMessage(Component.translatable(paid ? "hearth.crowd.brawl.fined" : "hearth.crowd.brawl.warned", fine).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Every 10 ticks: brawls that ran too long, or lost a fighter. */
	public void tick(MinecraftServer server) {
		if (this.all.isEmpty()) {
			return;
		}
		NpcService npc = HearthServices.npc();
		long now = server.overworld().getGameTime();
		for (Brawl b : List.copyOf(this.all)) {
			FolkHandle fa = npc == null ? null : npc.folk(b.a);
			FolkHandle fb = npc == null ? null : npc.folk(b.b);
			if (fa == null || fb == null || !fa.awake() || !fb.awake()) {
				end(b, End.GONE, server);
			} else if (now > b.until) {
				end(b, End.TIRED, server);
			}
		}
	}

	/** A blow landed on someone: a player with bare fists hitting a brawler has joined the brawl. */
	public void onDamage(LivingEntity target, DamageSource source) {
		if (this.all.isEmpty() || !(source.getEntity() instanceof ServerPlayer p) || !p.getMainHandItem().isEmpty()) {
			return;
		}
		NpcService npc = HearthServices.npc();
		if (npc == null) {
			return;
		}
		for (Brawl b : this.all) {
			for (UUID id : new UUID[]{b.a, b.b}) {
				FolkHandle h = npc.folk(id);
				if (h != null && h.entity() == target) {
					b.joined.add(p.getUUID());
					return;
				}
			}
		}
	}

	public void clear() {
		this.all.clear();
		this.byFolk.clear();
	}
}
