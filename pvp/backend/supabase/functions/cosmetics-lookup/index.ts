import { serve } from '../_shared/deno.ts';
import { handler } from './handler.ts';

serve(handler);
