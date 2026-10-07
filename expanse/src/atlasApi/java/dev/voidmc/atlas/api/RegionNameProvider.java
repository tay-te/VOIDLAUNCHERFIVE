package dev.voidmc.atlas.api;

@FunctionalInterface
public interface RegionNameProvider {
	String name(RegionNameQuery query);
}
