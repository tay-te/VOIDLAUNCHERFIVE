package dev.voidmc.atlas.api;

public interface AtlasPlaces {
	void registerNameProvider(String id, int priority, RegionNameProvider provider);
}
