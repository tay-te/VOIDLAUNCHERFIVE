package dev.voidmc.hearth.social.events;

import com.mojang.math.Transformation;
import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.caravan.Caravans;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A funeral (docs/LIVING_WORLD_2.md §3.1): the bell tolls, four kin carry the bier from the house to the cemetery, the
 * mourners follow, the priest says the words, and a fresh grave with candles is left seven days (the timeline and the
 * dressings). This director carries the bier: a block display at the bearers' shoulders that glides with them.
 */
final class FuneralScene implements SceneDirector {
	static final FuneralScene INSTANCE = new FuneralScene();

	private FuneralScene() {
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		Vec3 at = shoulders(run);
		Display.BlockDisplay bier = run.get("bier");
		if (at == null) {
			if (bier != null) {
				bier.discard();
				run.put("bier", null);
			}
			return;
		}
		ServerLevel level = run.level();
		if (bier == null || bier.isRemoved()) {
			bier = EntityTypes.BLOCK_DISPLAY.create(level, EntitySpawnReason.EVENT);
			if (bier == null) {
				return;
			}
			bier.addTag(Caravans.TAG);
			bier.setBlockState(Blocks.SPRUCE_SLAB.defaultBlockState());
			// a plank bier, the length of a body, shrouded at its head
			bier.setTransformation(new Transformation(new Vector3f(-0.4F, 0, -1.0F), new Quaternionf(), new Vector3f(0.8F, 0.5F, 2.0F), new Quaternionf()));
			bier.setPosRotInterpolationDuration(20);
			bier.snapTo(at.x, at.y, at.z, 0, 0);
			if (!level.addFreshEntity(bier)) {
				return;
			}
			run.spawned(bier);
			run.put("bier", bier);
		} else {
			bier.setPos(at.x, at.y, at.z);
		}
	}

	/** The middle of the bearers who are about, at shoulder height, while they carry; else null. */
	static Vec3 shoulders(SceneRun run) {
		double x = 0;
		double y = 0;
		double z = 0;
		int n = 0;
		for (UUID id : run.role("bearers")) {
			Scenes.Order o = Scenes.order(id);
			var f = EventDirector.folk(id);
			if (f == null || o == null || !o.carry) {
				continue;
			}
			x += f.entity().getX();
			y += f.entity().getY();
			z += f.entity().getZ();
			n++;
		}
		return n < 2 ? null : new Vec3(x / n, y / n + 1.35, z / n);
	}
}
