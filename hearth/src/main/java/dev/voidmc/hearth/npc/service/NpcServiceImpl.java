package dev.voidmc.hearth.npc.service;

import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcModule;
import dev.voidmc.hearth.npc.embody.EmbodimentManager;
import dev.voidmc.hearth.npc.entity.Folk;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The npc module's {@link NpcService}: folk lookups, perception, the behaviours other modules teach folk, and escorts.
 * Registered in {@code NpcModule.init()}. Server thread only.
 */
public final class NpcServiceImpl implements NpcService {
	private final EmbodimentManager embodiment;

	public NpcServiceImpl(EmbodimentManager embodiment) {
		this.embodiment = embodiment;
	}

	@Override
	public void registerBehaviour(FolkBehaviour behaviour) {
		Behaviours.register(behaviour);
	}

	@Override
	public @Nullable FolkHandle folk(UUID resident) {
		Folk f = this.embodiment.entityOf(resident);
		FolkBrain brain = f == null ? null : f.brain();
		return brain == null || f.isFolded() || !f.isAlive() ? null : brain.handle();
	}

	@Override
	public List<FolkHandle> folkNear(ServerLevel level, Vec3 at, double radius) {
		double r2 = radius * radius;
		List<Folk> found = level.getEntitiesOfClass(Folk.class, new AABB(at, at).inflate(radius),
			f -> f.brain() != null && !f.isFolded() && f.isAlive() && f.distanceToSqr(at) <= r2);
		found.sort(Comparator.comparingDouble(f -> f.distanceToSqr(at)));
		List<FolkHandle> out = new ArrayList<>(found.size());
		for (Folk f : found) {
			out.add(f.brain().handle());
		}
		return out;
	}

	@Override
	public @Nullable UUID follow(ServerPlayer player, Settlement settlement, Resident resident, BlockPos destination) {
		if (!(player.level() instanceof ServerLevel level) || level.dimension() != settlement.dimension() || settlement.resident(resident.id) == null) {
			return null;
		}
		Folk f = this.embodiment.entityOf(resident.id);
		if (f == null || f.isFolded()) {
			// Made flesh beside the player, if the server has room for one more.
			f = this.embodiment.spawnNear(level, settlement, resident, player.blockPosition());
			if (f == null) {
				return null;
			}
		}
		Escorts.begin(resident.id, player, destination);
		return resident.id;
	}

	@Override
	public @Nullable EscortStatus escort(UUID resident) {
		Escorts.Status st = Escorts.status(resident);
		return st == null ? null : EscortStatus.valueOf(st.name());
	}

	@Override
	public boolean onDuty(Settlement settlement, Resident resident) {
		net.minecraft.server.MinecraftServer server = dev.voidmc.hearth.npc.NpcTime.server();
		if (server == null) {
			return true;
		}
		dev.voidmc.hearth.npc.routine.Schedule day = dev.voidmc.hearth.npc.routine.RoutinePlacement.schedule(settlement, resident,
			dev.voidmc.hearth.npc.NpcTime.today());
		return day.at(dev.voidmc.hearth.npc.NpcTime.minuteOfDay(server.overworld())).activity() == dev.voidmc.hearth.npc.routine.Activity.WORK;
	}

	@Override
	public @Nullable FolkHandle embodyNear(ServerLevel level, Settlement settlement, Resident resident, BlockPos near) {
		if (level.dimension() != settlement.dimension() || settlement.resident(resident.id) == null) {
			return null;
		}
		Folk f = this.embodiment.entityOf(resident.id);
		if (f == null || f.isFolded()) {
			f = this.embodiment.spawnNear(level, settlement, resident, near);
		}
		return f == null || f.brain() == null ? null : f.brain().handle();
	}

	@Override
	public java.util.Set<String> traits(Resident resident) {
		java.util.Set<String> out = new java.util.HashSet<>();
		for (dev.voidmc.hearth.npc.person.Trait t : dev.voidmc.hearth.npc.person.NpcData.of(resident).traits()) {
			out.add(t.getSerializedName());
		}
		return out;
	}

	/** How an escort stands (active, arrived, lost), for modules that started one; null if there is none. */
	public static Escorts.@Nullable Status escortStatus(UUID resident) {
		return Escorts.status(resident);
	}

	/** The service as registered, for the npc module's own commands. */
	public static @Nullable NpcServiceImpl get() {
		return NpcModule.service();
	}
}
