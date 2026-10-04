package dev.voidmc.hearth.npc.work;

import dev.voidmc.hearth.api.Poi;
import dev.voidmc.hearth.api.PoiType;
import dev.voidmc.hearth.npc.NpcTime;
import dev.voidmc.hearth.npc.entity.FolkPose;
import dev.voidmc.hearth.npc.place.ResidentPlaces;
import dev.voidmc.hearth.npc.routine.FolkBrain;
import dev.voidmc.hearth.npc.routine.Task;
import dev.voidmc.hearth.npc.routine.Tasks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The lord (docs/LIVING_WORLD.md §8.1): holds court in the keep's hall in the morning — seated on the high seat at the
 * {@code THRONE} POI, now and then giving audience (gesturing to whoever stands before them) — and in the afternoon
 * goes about the domain: the walls and viewpoints of the keep, the square, the stables, with the steward's ledger. A
 * town with no keep yet has its lord hold court at the town hall's high seat or their own table.
 */
final class LordWork implements WorkBehaviour {
	private int bout;

	@Override
	public @Nullable Task next(FolkBrain brain, ServerLevel level) {
		this.bout++;
		int minute = NpcTime.minuteOfDay(level);
		ResidentPlaces p = brain.places(level);
		Poi throne = OfficialWork.first(brain, level, PoiType.THRONE);
		if (minute < 12 * 60 + 30 || this.bout % 4 == 0) {
			if (throne != null) {
				// Court: long sittings, audiences given now and then.
				Tasks.Effect audience = Tasks.every(240, null, 0.0F, (b, lvl, e, at) -> {
					if (b.random.nextInt(3) == 0) {
						b.pose(FolkPose.TALK, 50);
					}
				}, false);
				return new Tasks.Sit(throne, 900 + brain.random.nextInt(600), ItemStack.EMPTY, audience);
			}
			Poi table = p.homeTables.isEmpty() ? null : p.homeTables.get(0);
			BlockPos at = table != null ? table.pos() : p.work;
			return new Tasks.Linger(at, null, FolkPose.READ, 600, new ItemStack(Items.WRITABLE_BOOK), ItemStack.EMPTY,
				Tasks.every(160, SoundEvents.BOOK_PAGE_TURN, 0.5F, null, false), "holding court at the table");
		}
		return switch (this.bout % 3) {
			case 0 -> {
				List<Poi> views = brain.town.places.all(level, brain.settlement, PoiType.VIEWPOINT);
				Poi view = views.isEmpty() ? null : views.get(brain.random.nextInt(views.size()));
				yield view != null ? new Tasks.Linger(view.pos(), view.pos().relative(view.facing(), 8), FolkPose.NONE, 500, ItemStack.EMPTY, ItemStack.EMPTY,
					null, "surveying the domain") : new Tasks.Wander(p.work, 12, 3);
			}
			case 1 -> {
				BlockPos site = OfficialWork.buildingSite(brain);
				yield site != null ? new Tasks.Linger(site, site, FolkPose.TALK, 300, ItemStack.EMPTY, ItemStack.EMPTY, null, "inspecting the works")
					: new Tasks.Wander(brain.settlement.center(), 10, 3);
			}
			default -> {
				Poi post = OfficialWork.random(brain, level, PoiType.HITCHING_POST);
				yield post != null ? new Tasks.Linger(post.pos(), null, FolkPose.TEND, 300, new ItemStack(Items.SADDLE), ItemStack.EMPTY, null,
					"at the stables") : new Tasks.Linger(brain.settlement.center(), null, FolkPose.NONE, 400, ItemStack.EMPTY, ItemStack.EMPTY, null,
					"in the square");
			}
		};
	}
}
