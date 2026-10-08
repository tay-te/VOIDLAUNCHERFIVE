package dev.voidmc.hearth.api;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Stub of Hearth's settlement view: only the members Mettle calls (see package-info; residents are the tour's). */
public interface Settlement {
	long id();

	String name();

	SettlementTier tier();

	/** The founding point: the heart of the settlement (its well, bell or fire). */
	BlockPos center();

	Collection<Resident> residents();

	void addResident(Resident resident);

	boolean removeResident(UUID id);
}
