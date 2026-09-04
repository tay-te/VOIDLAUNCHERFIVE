/**
 * Single import point for the shared component set.
 *
 * `@void/ui` owns the components and the design tokens for both bundles (§9).
 * This module re-exports it so the overlay's screens have one import to change
 * if the package is ever split.
 *
 * The package is consumed from source through the aliases in `vite.config.ts`;
 * see README.md, "Consuming @void/ui and @void/protocol".
 */

export * from '@void/ui';
