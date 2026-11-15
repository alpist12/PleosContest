const MAX_BYTES = 65536;
const response = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });

export function validDocument(d) {
  const text = (v, max) => typeof v === 'string' && v.length <= max;
  return d && !Array.isArray(d) && text(d.id, 100) && text(d.title, 80) && d.title.trim() &&
    text(d.type, 80) && text(d.destination, 120) && text(d.departAt, 80) &&
    ['DRAFT','PREPARING','DEPARTURE_CHECK','TRAVELING','ARRIVED','RETURN_CHECK','CLOSED'].includes(d.status) &&
    ['windmill','tower'].includes(d.landmark) &&
    Number.isSafeInteger(d.departureEpochMillis) && d.departureEpochMillis >= 0 && d.departureEpochMillis <= 8640000000000000 &&
    [0,30,60,120].includes(d.reminderMinutes) && typeof d.reminderEnabled === 'boolean' &&
    Array.isArray(d.items) && d.items.length <= 250 && new Set(d.items.map(i => i?.id)).size === d.items.length &&
    d.items.every(i => i && text(i.id, 100) && i.id && text(i.name, 80) && i.name.trim() && text(i.owner, 40) && text(i.place, 80) &&
      ['NOT_CHECKED','USER_CONFIRMED','EXCLUDED','RETURN_PENDING','RETURN_CONFIRMED'].includes(i.state) &&
      ['NONE','MOBILE','VEHICLE'].includes(i.confirmedIn) &&
      ['prepared','loaded','recoverable','dropOff'].every(k => typeof i[k] === 'boolean') && Number.isSafeInteger(i.revision) && i.revision >= 0) &&
    Array.isArray(d.observations) && d.observations.length <= 20 && new Set(d.observations.map(o => o?.id)).size === d.observations.length &&
    d.observations.every(o => o && text(o.id, 100) && text(o.title, 200) && text(o.note, 500) && typeof o.done === 'boolean');
}

async function readBounded(request) {
  if (Number(request.headers.get('Content-Length')) > MAX_BYTES) throw new Error('too-large');
  const reader = request.body?.getReader();
  if (!reader) throw new Error('invalid');
  let length = 0;
  const chunks = [];
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > MAX_BYTES) { await reader.cancel(); throw new Error('too-large'); }
      chunks.push(value);
    }
    const bytes = new Uint8Array(length);
    let offset = 0;
    for (const c of chunks) { bytes.set(c, offset); offset += c.length; }
    return JSON.parse(new TextDecoder().decode(bytes));
  } finally { reader.releaseLock(); }
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === '/health' && request.method === 'GET') return response({ ok: true });
    if (path !== '/trip') return response({ error: 'not-found' }, 404);
    if (!/^[a-f0-9]{64}$/.test(env.AUTH_SHA256 || '')) return response({ error: 'not-configured' }, 503);
    const token = request.headers.get('Authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1] || '';
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(token));
    const expected = Uint8Array.from(env.AUTH_SHA256.match(/../g), x => parseInt(x, 16));
    if (!crypto.subtle.timingSafeEqual(digest, expected)) return response({ error: 'unauthorized' }, 401);
    try {
      if (request.method === 'GET') {
        const row = await env.DB.prepare('SELECT version, document FROM trip WHERE slot = 1').first();
        return response({ version: row.version, document: row.document ? JSON.parse(row.document) : null });
      }
      if (request.method !== 'PUT') return response({ error: 'method' }, 405);
      let body;
      try { body = await readBounded(request); }
      catch (e) { return response({ error: e.message === 'too-large' ? 'too-large' : 'invalid-json' }, e.message === 'too-large' ? 413 : 400); }
      if (!Number.isSafeInteger(body?.version) || body.version < 0 || !validDocument(body.document)) return response({ error: 'invalid-document' }, 400);
      const result = await env.DB.prepare('UPDATE trip SET version = version + 1, document = ? WHERE slot = 1 AND version = ?')
        .bind(JSON.stringify(body.document), body.version).run();
      if (result.meta.changes !== 1) return response({ error: 'conflict' }, 409);
      return response({ version: body.version + 1, document: body.document });
    } catch {
      return response({ error: 'temporarily-unavailable' }, 503);
    }
  }
};
