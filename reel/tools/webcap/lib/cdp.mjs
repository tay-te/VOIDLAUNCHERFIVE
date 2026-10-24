// A minimal Chrome DevTools Protocol client over Node's built-in WebSocket.
// One connection to a page target; commands are promises, events are callbacks.

export class CDP {
  #ws;
  #id = 0;
  #pending = new Map();
  #listeners = new Map();
  closed = false;

  static connect(url) {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(url);
      ws.binaryType = 'arraybuffer';
      const c = new CDP(ws);
      ws.addEventListener('open', () => resolve(c), { once: true });
      ws.addEventListener('error', (e) => reject(new Error(`CDP connect failed: ${e.message ?? e.type}`)), { once: true });
    });
  }

  constructor(ws) {
    this.#ws = ws;
    ws.addEventListener('message', (ev) => {
      const text = typeof ev.data === 'string' ? ev.data : Buffer.from(ev.data).toString('utf8');
      const msg = JSON.parse(text);
      if (msg.id !== undefined) {
        const p = this.#pending.get(msg.id);
        if (!p) return;
        this.#pending.delete(msg.id);
        if (msg.error) p.reject(new Error(`${p.method}: ${msg.error.message}${msg.error.data ? ` (${msg.error.data})` : ''}`));
        else p.resolve(msg.result);
      } else if (msg.method) {
        for (const fn of this.#listeners.get(msg.method) ?? []) fn(msg.params);
        for (const fn of this.#listeners.get('*') ?? []) fn(msg.method, msg.params);
      }
    });
    ws.addEventListener('close', () => {
      this.closed = true;
      for (const p of this.#pending.values()) p.reject(new Error(`CDP closed during ${p.method}`));
      this.#pending.clear();
    });
  }

  send(method, params = {}) {
    if (this.closed) return Promise.reject(new Error(`CDP closed; cannot send ${method}`));
    const id = ++this.#id;
    return new Promise((resolve, reject) => {
      this.#pending.set(id, { resolve, reject, method });
      this.#ws.send(JSON.stringify({ id, method, params }));
    });
  }

  on(method, fn) {
    const set = this.#listeners.get(method) ?? new Set();
    set.add(fn);
    this.#listeners.set(method, set);
    return () => set.delete(fn);
  }

  /** Evaluate an expression in the page and return its value (promises awaited). */
  async eval(expression, { timeout = 60_000 } = {}) {
    const r = await this.send('Runtime.evaluate', {
      expression,
      awaitPromise: true,
      returnByValue: true,
      timeout,
    });
    if (r.exceptionDetails) {
      const d = r.exceptionDetails;
      const msg = d.exception?.description ?? d.exception?.value ?? d.text;
      throw new Error(`page eval failed: ${msg}\n  in: ${expression.slice(0, 200)}`);
    }
    return r.result.value;
  }

  close() {
    try {
      this.#ws.close();
    } catch {
      /* already gone */
    }
  }
}
