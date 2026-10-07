/**
 * Compile-time stand-ins for the part of VOID Atlas's API that Expanse uses (the real classes are in Atlas's own jar,
 * {@code atlas/src/main/java/dev/voidmc/atlas/api}). Only compiled against, never shipped: Fabric calls Expanse's
 * {@code "atlas"} entrypoint only when Atlas is installed, and then Atlas's own classes are the ones loaded. Keep each
 * signature exactly as Atlas has it.
 */
package dev.voidmc.atlas.api;
