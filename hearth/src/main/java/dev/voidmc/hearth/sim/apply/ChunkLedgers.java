package dev.voidmc.hearth.sim.apply;

import dev.voidmc.hearth.Hearth;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.level.chunk.ChunkAccess;

/** The chunk attachment {@code hearth:sim/applied} that carries each chunk's {@link AppliedLedger}. */
public final class ChunkLedgers {
	public static final AttachmentType<AppliedLedger> TYPE = AttachmentRegistry.create(Hearth.id("sim/applied"), b -> b.persistent(AppliedLedger.CODEC));

	private ChunkLedgers() {
	}

	public static void init() {
		// loading the class registers the attachment type
	}

	public static AppliedLedger of(ChunkAccess chunk) {
		AppliedLedger l = ((AttachmentTarget) chunk).getAttached(TYPE);
		return l == null ? AppliedLedger.EMPTY : l;
	}

	/** Stores the ledger on the chunk and marks the chunk to be saved. */
	public static void store(ChunkAccess chunk, AppliedLedger ledger) {
		((AttachmentTarget) chunk).setAttached(TYPE, ledger.isEmpty() ? null : ledger);
		chunk.markUnsaved();
	}
}
