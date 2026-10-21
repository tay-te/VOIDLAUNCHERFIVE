package dev.voidmc.hearth.npc.crowd;

import dev.voidmc.hearth.api.CrowdService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.NpcTime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** The npc module's {@link CrowdService} (docs/LIVING_WORLD_2.md §5): brawls, spectacles, and whether a town is shut up. */
final class CrowdServiceImpl implements CrowdService {
	private final CrowdDirector director;
	private final Brawls brawls;
	private final Shutters shutters;

	CrowdServiceImpl(CrowdDirector director, Brawls brawls, Shutters shutters) {
		this.director = director;
		this.brawls = brawls;
		this.shutters = shutters;
	}

	@Override
	public boolean brawl(Settlement s, UUID a, UUID b, String why) {
		return this.brawls.start(s, a, b, why);
	}

	@Override
	public void spectacle(Settlement s, BlockPos at, String kind, int seconds, int maxOnlookers) {
		ServerLevel level = level(s);
		if (level != null && seconds > 0 && maxOnlookers > 0) {
			this.director.spectacle(s, level, at, kind, seconds, maxOnlookers);
		}
	}

	@Override
	public boolean shuttered(Settlement s) {
		ServerLevel level = level(s);
		return level != null && this.shutters.closed(s, level);
	}

	@Override
	public boolean brawling(UUID player, UUID resident) {
		Brawls.Brawl b = this.brawls.of(resident);
		if (b == null || !b.fighter(resident)) {
			return false;
		}
		MinecraftServer server = NpcTime.server();
		var p = server == null ? null : server.getPlayerList().getPlayer(player);
		if (p == null || !p.getMainHandItem().isEmpty()) {
			return false;
		}
		b.joined.add(player);
		return true;
	}

	private static ServerLevel level(Settlement s) {
		MinecraftServer server = NpcTime.server();
		return server == null ? null : server.getLevel(s.dimension());
	}
}
