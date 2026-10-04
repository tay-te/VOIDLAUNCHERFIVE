package dev.voidmc.hearth.standing;

import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.HearthWorld;
import dev.voidmc.hearth.api.LawService;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.api.SettlementEvents;
import dev.voidmc.hearth.api.StandingService;
import dev.voidmc.hearth.standing.appearance.Appearances;
import dev.voidmc.hearth.standing.model.RegionData;
import dev.voidmc.hearth.standing.model.StandingKeys;
import dev.voidmc.hearth.standing.rules.DeedRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The standing engine's view of the running game: settlements from the sim, folk from the npc module, players online. */
final class GameHost implements StandingHost {
	private @Nullable MinecraftServer server;
	/** Stands in for the world data when no Hearth world is loaded (a server without the sim): nothing is saved. */
	private RegionData orphan = new RegionData();

	void setServer(@Nullable MinecraftServer server) {
		this.server = server;
		this.orphan = new RegionData();
	}

	@Nullable MinecraftServer server() {
		return this.server;
	}

	/** The Hearth world that keeps world-level standing state: the overworld's, else the first there is. */
	@Nullable HearthWorld main() {
		MinecraftServer s = this.server;
		if (s == null) {
			return null;
		}
		ServerLevel overworld = s.overworld();
		HearthWorld w = overworld == null ? null : HearthWorld.of(overworld);
		if (w != null) {
			return w;
		}
		for (ServerLevel level : s.getAllLevels()) {
			w = HearthWorld.of(level);
			if (w != null) {
				return w;
			}
		}
		return null;
	}

	@Nullable HearthWorld worldOf(Settlement settlement) {
		MinecraftServer s = this.server;
		if (s == null) {
			return null;
		}
		ServerLevel level = s.getLevel(settlement.dimension());
		return level == null ? null : HearthWorld.of(level);
	}

	/** The player if online (null early in server start, before the player list exists). */
	@Nullable ServerPlayer online(UUID player) {
		MinecraftServer s = this.server;
		return s == null || s.getPlayerList() == null ? null : s.getPlayerList().getPlayer(player);
	}

	@Override
	public long day() {
		HearthWorld w = main();
		if (w != null) {
			return w.day();
		}
		MinecraftServer s = this.server;
		ServerLevel overworld = s == null ? null : s.overworld();
		return overworld == null ? 0 : overworld.getOverworldClockTime() / 24000L;
	}

	@Override
	public @Nullable Settlement settlement(long id) {
		MinecraftServer s = this.server;
		if (s == null) {
			return null;
		}
		for (ServerLevel level : s.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w != null) {
				Settlement found = w.settlement(id);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	@Override
	public Collection<? extends Settlement> settlements() {
		MinecraftServer s = this.server;
		if (s == null) {
			return List.of();
		}
		List<Settlement> out = new ArrayList<>();
		for (ServerLevel level : s.getAllLevels()) {
			HearthWorld w = HearthWorld.of(level);
			if (w != null) {
				out.addAll(w.settlements());
			}
		}
		return out;
	}

	@Override
	public List<HearthWorld.SiteInfo> sitesNear(Settlement origin, int radius) {
		HearthWorld w = worldOf(origin);
		return w == null ? List.of() : w.knownSites(origin.center(), radius);
	}

	@Override
	public RegionData region() {
		HearthWorld w = main();
		return w == null ? this.orphan : w.data(StandingKeys.REGION);
	}

	@Override
	public void regionDirty() {
		HearthWorld w = main();
		if (w != null) {
			w.markDirty();
		}
	}

	@Override
	public List<UUID> witnesses(StandingService.Deed deed, int radius, int max) {
		NpcService npc = HearthServices.npc();
		MinecraftServer s = this.server;
		if (npc == null || s == null) {
			return List.of();
		}
		ServerPlayer player = online(deed.player());
		Settlement town = settlement(deed.settlementId());
		if (player == null || town == null || !town.embodied() || player.level().dimension() != town.dimension()) {
			return List.of();
		}
		List<UUID> out = new ArrayList<>();
		for (NpcService.FolkHandle f : npc.folkNear(player.level(), Vec3.atCenterOf(deed.pos()), radius)) {
			if (out.size() >= max) {
				break;
			}
			if (f.settlement().id() == town.id() && f.awake() && f.canSee(player)) {
				out.add(f.resident().id);
			}
		}
		return out;
	}

	@Override
	public void standingChanged(Settlement settlement, UUID player, StandingService.Title from, StandingService.Title to) {
		HearthWorld w = worldOf(settlement);
		if (w != null) {
			SettlementEvents.STANDING_CHANGED.invoker().onStandingChanged(w, settlement, player, from, to);
		}
	}

	@Override
	public void gossipArrived(UUID player, Settlement town, String from, int delta) {
		ServerPlayer p = online(player);
		if (p == null) {
			return;
		}
		String key = delta > 0 ? "hearth.standing.gossip.arrived.good" : "hearth.standing.gossip.arrived.bad";
		p.sendSystemMessage(Component.translatable(key, town.name(), from).withStyle(delta > 0 ? ChatFormatting.GOLD : ChatFormatting.RED));
	}

	@Override
	public String playerName(UUID player) {
		MinecraftServer s = this.server;
		if (s == null) {
			return "";
		}
		ServerPlayer p = online(player);
		if (p != null) {
			return p.nameAndId().name();
		}
		return s.services().nameToIdCache().get(player).map(n -> n.name()).orElse("");
	}

	@Override
	public boolean outlawByLaw(UUID player, long settlementId) {
		LawService law = HearthServices.law();
		return law != null && law.outlaw(player, settlementId);
	}

	@Override
	public void action(StandingService.Deed deed, DeedRule rule) {
		ServerPlayer p = online(deed.player());
		if (p != null && rule.washes()) {
			Appearances.bathe(p);
		}
	}
}
