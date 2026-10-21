package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import dev.voidmc.hearth.npc.caravan.Caravans;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;

/**
 * Tax day at the town hall (docs/LIVING_WORLD_2.md §3.1): the bailiff's party (lane A1's journey) rides in, the reeve
 * hands over the coin at the hall while folk grumble, and a poor household that cannot pay has its pig led away — down
 * the street to the bailiff's cart and out of town — unless a player pays for them ("Pay the … family's tax").
 */
final class TaxScene implements SceneDirector {
	static final TaxScene INSTANCE = new TaxScene();

	private TaxScene() {
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		if (!run.event.args().contains("poor_family")) {
			return;
		}
		Pig pig = run.get("pig");
		if (run.iv.flag("paid_for")) {
			if (pig != null && !pig.isRemoved()) {
				// paid for: the pig goes home
				BlockPos home = run.venue("home");
				pig.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
			}
			return;
		}
		if (pig == null && run.minute >= run.rule.param("pig_at", 20)) {
			UUID poor = UUID.fromString(run.event.args().getStringOr("poor_family", new UUID(0, 0).toString()));
			var r = s.resident(poor);
			var home = r == null || r.home < 0 ? null : s.plot(r.home);
			BlockPos from = home == null ? run.anchor : SceneVenues.door(s, home);
			BlockPos ground = EventDressings.ground(run.level(), from);
			if (ground == null) {
				return;
			}
			pig = EntityTypes.PIG.create(run.level(), EntitySpawnReason.EVENT);
			if (pig == null) {
				return;
			}
			pig.addTag(Caravans.TAG);
			pig.setPersistenceRequired();
			pig.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0, 0);
			if (run.level().addFreshEntity(pig)) {
				run.spawned(pig);
				run.put("pig", pig);
			}
			return;
		}
		if (pig != null && !pig.isRemoved() && gameTime % 60 == 0) {
			// led to the hall, then out of the gate after the cart
			BlockPos to = run.minute < run.rule.param("pig_out", 60) ? run.venue("town_hall") : run.venue("gate");
			pig.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.9);
		}
	}
}
