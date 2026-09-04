/**
 * Vitest setup for the UI tests.
 *
 * `globals: false` keeps `describe`/`it`/`expect` as explicit imports, which is what the
 * rest of this repo does — but it also means React Testing Library's automatic cleanup
 * never registers, because that hook looks for a global `afterEach`. Register it here
 * instead, or every test renders into the DOM the previous one left behind.
 */
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
