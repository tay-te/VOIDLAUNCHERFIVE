package dev.voidmc.atlas.api;


/**
 * Names regions and natural places in place of Atlas's own generated names (VOID Expanse gives its lands their real
 * names this way). Asked once per place, on a worker thread, in priority order; the first name that is not null wins.
 * Return null to leave a place to the next provider (or to Atlas's generator). Must be thread-safe and quick.
 */
@FunctionalInterface
public interface RegionNameProvider {
	String name(RegionNameQuery query);
}
