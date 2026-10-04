package dev.voidmc.hearth.mixin.repair;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.voidmc.hearth.api.SettlementEvents.Damaged.Cause;
import dev.voidmc.hearth.repair.RepairHooks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Creepers, TNT, anything that explodes: what the blast took from settlements' buildings, and who lit it. */
@Mixin(ServerExplosion.class)
abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Shadow
	public abstract LivingEntity getIndirectSourceEntity();

	@WrapMethod(method = "interactWithBlocks(Ljava/util/List;)V")
	private void hearth$repairTrack(List<BlockPos> targets, Operation<Void> original) {
		if (!RepairHooks.active(this.level)) {
			original.call(targets);
			return;
		}
		// the explosion shuffles the list in place: keep our own copy of the order we read
		List<BlockPos> order = new ArrayList<>(targets);
		BlockState[] before = RepairHooks.beforeExplosion(this.level, order);
		if (before == null) {
			original.call(targets);
			return;
		}
		LivingEntity source = this.getIndirectSourceEntity();
		RepairHooks.push(Cause.EXPLOSION, source instanceof ServerPlayer p ? p : null);
		try {
			original.call(targets);
			RepairHooks.afterExplosion(this.level, order, before, source);
		} finally {
			RepairHooks.pop();
		}
	}
}
