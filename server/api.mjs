import { createHash } from 'node:crypto';
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const reply = (body, status = 200) => Response.json(body, { status, headers: { 'Cache-Control': 'no-store' } });
const validDate = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T12:00:00Z`)) && new Date(`${value}T12:00:00Z`).toISOString().slice(0, 10) === value;

// Chaque écriture passe par une file : lire, comparer la version puis renommer
// le fichier constituent une seule opération côté serveur (un processus Node).
export function createApi(directory) {
  let queue = Promise.resolve();
  return request => {
    const job = queue.then(() => handle(request));
    queue = job.catch(() => {});
    return job;
  };

  async function handle(request) {
    const token = request.headers.get('authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1];
    if (!token) return reply({ error: 'Identifiant d’installation invalide.' }, 401);
    const device = createHash('sha256').update(token).digest('hex');
    await mkdir(directory, { recursive: true });
    const path = join(directory, `${device}.json`);
    let state;
    try { state = JSON.parse(await readFile(path, 'utf8')); }
    catch (error) { if (error.code !== 'ENOENT') throw error; state = { foods: {}, operations: {} }; }

    if (request.method === 'GET') return reply(Object.values(state.foods));
    if (request.method !== 'POST') return reply({ error: 'Méthode refusée.' }, 405);
    if (!request.headers.get('content-type')?.startsWith('application/json')) return reply({ error: 'JSON attendu.' }, 415);
    const text = await request.text();
    if (Buffer.byteLength(text) > 420_000) return reply({ error: 'Requête trop volumineuse.' }, 413);
    let mutation;
    try { mutation = JSON.parse(text); } catch { return reply({ error: 'JSON invalide.' }, 400); }
    const food = mutation?.food;
    if (!uuid.test(mutation?.operationId ?? '') || !Number.isSafeInteger(mutation?.baseVersion) || mutation.baseVersion < 0 ||
        !uuid.test(food?.id ?? '') || typeof food?.name !== 'string' || !food.name.trim() || food.name.length > 80 ||
        !validDate(food.date) || !Number.isSafeInteger(food.createdAt) || food.createdAt < 0 || typeof food.deleted !== 'boolean') {
      return reply({ error: 'Aliment ou opération invalide.' }, 400);
    }
    if (food.photoData != null) {
      if (typeof food.photoData !== 'string' || food.photoData.length > 400_000 || !/^data:image\/jpeg;base64,[A-Za-z0-9+/]+=*$/.test(food.photoData)) return reply({ error: 'Photo invalide.' }, 400);
      const bytes = Buffer.from(food.photoData.substring(food.photoData.indexOf(',') + 1), 'base64');
      if (bytes[0] !== 255 || bytes[1] !== 216 || bytes.at(-2) !== 255 || bytes.at(-1) !== 217) return reply({ error: 'JPEG invalide.' }, 400);
    }
    const acknowledged = state.operations[mutation.operationId];
    // Un timeout peut arriver après l'écriture : rejouer exactement la même
    // opération renvoie sa confirmation sans créer une seconde version.
    if (acknowledged) {
      if (acknowledged.payload !== text) return reply({ error: 'Identifiant d’opération réutilisé avec un autre contenu.' }, 400);
      return reply(acknowledged.result);
    }
    const previous = state.foods[food.id];
    if (mutation.baseVersion !== (previous?.version ?? 0)) return reply({ error: 'Conflit de version.', current: previous ?? { ...food, version: 0, deleted: true } }, 409);
    const version = (previous?.version ?? 0) + 1;
    state.foods[food.id] = { id: food.id, name: food.name.trim(), date: food.date, createdAt: food.createdAt, photoData: food.deleted ? null : food.photoData ?? null, deleted: food.deleted, version };
    const result = { ok: true, version };
    state.operations[mutation.operationId] = { payload: text, result };
    const temporary = `${path}.tmp`;
    await writeFile(temporary, JSON.stringify(state), { mode: 0o600 });
    await rename(temporary, path);
    return reply(result);
  }
}
