package dev.voidmc.hearth.social.lives;

import dev.voidmc.hearth.Hearth;
import dev.voidmc.hearth.api.AgeStage;
import dev.voidmc.hearth.api.HearthServices;
import dev.voidmc.hearth.api.JobType;
import dev.voidmc.hearth.api.NpcService;
import dev.voidmc.hearth.api.Resident;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.sim.lives.Life;
import dev.voidmc.hearth.sim.lives.Lives;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * An apprentice at the master's side (docs/LIVING_WORLD_2.md §4.3, priority 105): through the working day, while the
 * master is at their trade nearby, the apprentice stands beside them with the trade's tool, watches, lends a hand (a
 * swing, the craft pose), and carries what the master carries.
 */
final class ApprenticeBehaviour implements NpcService.FolkBehaviour {
	static final Identifier ID = Hearth.id("lives/apprentice");
	static final int PRIORITY = 105;
	static final int FROM = 8 * 60;
	static final int TO = 16 * 60 + 30;
	private final Map<UUID, State> states = new HashMap<>();

	private static final class State {
		final UUID master;
		final ItemStack tool;
		long nextMove;
		long nextWork;

		State(UUID master, ItemStack tool) {
			this.master = master;
			this.tool = tool;
		}
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int priority() {
		return PRIORITY;
	}

	@Override
	public boolean wants(NpcService.FolkHandle folk) {
		long t0 = Folks.t0();
		try {
			return master(folk) != null;
		} finally {
			Folks.t1(t0);
		}
	}

	/** The master's folk when the apprentice should be at their side now, or null. */
	private static NpcService.@Nullable FolkHandle master(NpcService.FolkHandle folk) {
		Resident r = folk.resident();
		Settlement s = folk.settlement();
		if (r.age != AgeStage.CHILD || !Lives.config().lives().enabled() || !s.embodied() || !folk.awake()) {
			return null;
		}
		UUID master = Life.master(r);
		if (master == null || !Folks.within(Folks.minute(s), FROM, TO) || HearthServices.calendar().restDay(s, Folks.today(s))) {
			return null;
		}
		NpcService npc = HearthServices.npc();
		NpcService.FolkHandle m = npc == null ? null : npc.folk(master);
		if (m == null || !m.awake() || m.entity().distanceToSqr(folk.entity()) > 48 * 48) {
			return null;
		}
		Resident mr = m.resident();
		return mr.work != Resident.NO_PLOT && npc.onDuty(s, mr) ? m : null;
	}

	@Override
	public void start(NpcService.FolkHandle folk) {
		UUID master = Life.master(folk.resident());
		JobType trade = Life.apprenticeTrade(folk.resident());
		State st = new State(master, new ItemStack(tool(trade)));
		this.states.put(folk.resident().id, st);
		folk.hold(st.tool.copy());
	}

	static Item tool(@Nullable JobType trade) {
		if (trade == null) {
			return Items.STICK;
		}
		return switch (trade) {
			case SMITH -> Items.IRON_INGOT;
			case CARPENTER, BOATWRIGHT -> Items.OAK_PLANKS;
			case MASON -> Items.STONE_BRICKS;
			case BAKER -> Items.BREAD;
			case BREWER -> Items.GLASS_BOTTLE;
			case TAILOR -> Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE);
			case TANNER -> Items.LEATHER;
			case BUTCHER -> Items.PORKCHOP;
			case CHANDLER -> Items.CANDLE;
			case GLASSMAKER -> Items.GLASS;
			case SMELTER -> Items.RAW_IRON;
			case KILN_WORKER -> Items.BRICK;
			case PAPERMAKER -> Items.PAPER;
			case DYER -> Items.DYE.pick(net.minecraft.world.item.DyeColor.RED);
			case HEALER -> Items.SWEET_BERRIES;
			case MINTER -> Items.GOLD_NUGGET;
			case MERCHANT -> Items.BOOK;
			case SALTER -> Items.SUGAR;
			case BEEKEEPER -> Items.HONEYCOMB;
			default -> Items.STICK;
		};
	}

	@Override
	public boolean tick(NpcService.FolkHandle folk) {
		long t0 = Folks.t0();
		try {
			State st = this.states.get(folk.resident().id);
			if (st == null) {
				return false;
			}
			long t = folk.gameTime();
			NpcService npc = HearthServices.npc();
			NpcService.FolkHandle m = npc == null ? null : npc.folk(st.master);
			if (m == null || (t & 63) == 0 && master(folk) == null) {
				return false;
			}
			BlockPos side = beside(m);
			if (!Folks.near(folk, side, 1.6)) {
				if (t >= st.nextMove) {
					Folks.walk(folk, side, 0.55);
					st.nextMove = t + 30;
				}
				return true;
			}
			folk.stop();
			folk.lookAt(m.entity());
			if (t >= st.nextWork) {
				// carry what the master carries, else the trade's own tool
				ItemStack theirs = m.entity().getMainHandItem();
				folk.hold(theirs.isEmpty() ? st.tool.copy() : theirs.copyWithCount(1));
				folk.pose(st.tool.is(Items.IRON_INGOT) ? "hammer" : "craft", 50);
				folk.swing();
				st.nextWork = t + 70 + ((t ^ folk.resident().id.hashCode()) & 31);
			}
			return true;
		} finally {
			Folks.t1(t0);
		}
	}

	/** A block beside the master, on the side away from where they face. */
	private static BlockPos beside(NpcService.FolkHandle m) {
		var e = m.entity();
		double yaw = Math.toRadians(e.getYRot() + 90);
		return BlockPos.containing(e.getX() - Math.sin(yaw) * 1.6, e.getY(), e.getZ() + Math.cos(yaw) * 1.6);
	}

	@Override
	public void stop(NpcService.FolkHandle folk) {
		if (this.states.remove(folk.resident().id) != null) {
			folk.pose("none", 0);
			folk.hold(ItemStack.EMPTY);
		}
	}
}
