package dev.voidmc.hearth.social.events;

import dev.voidmc.hearth.api.Settlement;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The pillory and the gallows (docs/LIVING_WORLD_2.md §3.1). The pillory: the convicted resident stands in it two hours
 * while the crowd jeers and children throw rotten vegetables; a player may throw too, pay the fine to have them let out,
 * or free them (a crime). The gallows (rare: murder; §13: staged up to the steps): dawn, a drum, the bell, the crowd —
 * and the scene ends as the condemned is led up, with a cut (a moment's darkness for those watching); the resident is
 * gone, and the board says they were hanged at dawn.
 */
final class PilloryScene implements SceneDirector {
	static final PilloryScene INSTANCE = new PilloryScene();

	private PilloryScene() {
	}

	@Override
	public void tick(SceneRun run, Settlement s, long gameTime) {
		if (run.event.kind() != EventKind.PILLORY || gameTime % 40 != 0) {
			return;
		}
		List<UUID> kids = run.role("children");
		List<UUID> convict = run.role("convict");
		if (kids.isEmpty() || convict.isEmpty()) {
			return;
		}
		var target = EventDirector.folk(convict.get(0));
		var child = EventDirector.folk(kids.get((int) ((gameTime / 40) % kids.size())));
		if (target == null || child == null || child.entity().distanceToSqr(target.entity()) > 12 * 12) {
			return;
		}
		ServerLevel level = run.level();
		child.lookAt(target.entity());
		child.swing();
		BlockPos at = target.entity().blockPosition();
		EventDressings.particles(level, "veg", at.above(), 5);
		level.playSound(null, at, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.8F, 1.1F);
	}

	@Override
	public void end(SceneRun run, Settlement s) {
		if (run.event.kind() != EventKind.EXECUTION || run.iv.flag("mercy")) {
			return;
		}
		// the cut: those watching see the condemned led up the steps, and then nothing
		BlockPos at = run.venue("gallows");
		for (ServerPlayer p : EventDirector.playersNear(run.level(), at, 48)) {
			p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
			p.sendSystemMessage(Component.translatable("hearth.events.execution.cut", EventEffects.name(s, run.event.principals())), true);
		}
	}
}
