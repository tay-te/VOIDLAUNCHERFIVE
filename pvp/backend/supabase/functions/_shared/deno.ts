/**
 * The only module that touches the Deno global. Each function's `index.ts` is two lines on
 * top of this; everything testable lives in `handler.ts`.
 */

import { makeDeps } from './deps.ts';
import type { Deps } from './deps.ts';

declare const Deno: {
  env: { get(name: string): string | undefined };
  serve(handler: (req: Request) => Response | Promise<Response>): unknown;
};

export function serve(handler: (deps: Deps) => (req: Request) => Promise<Response>): void {
  const deps = makeDeps((name) => Deno.env.get(name), fetch);
  Deno.serve(handler(deps));
}
