import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { createApi } from './api.mjs';

const food = () => ({ id: randomUUID(), name: 'Yaourts', date: '2026-11-12', createdAt: 1, photoData: null, deleted: false });
const request = (mutation, token = 'a'.repeat(64)) => new Request('https://test/api/foods', { method: mutation ? 'POST' : 'GET', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(mutation ? { body: JSON.stringify(mutation) } : {}) });
async function fixture(t) {
  const path = await mkdtemp(join(tmpdir(), 'vide-frigo-test-'));
  t.after(() => rm(path, { recursive: true, force: true }));
  return { api: createApi(path), path };
}

test('les écritures persistent après redémarrage et restent isolées par installation', async t => {
  const { api, path } = await fixture(t);
  const item = food();
  assert.equal((await api(request({ operationId: randomUUID(), baseVersion: 0, food: item }))).status, 200);
  const restarted = createApi(path);
  assert.equal((await (await restarted(request())).json())[0].name, 'Yaourts');
  assert.deepEqual(await (await restarted(request(null, 'b'.repeat(64)))).json(), []);
});

test('un rejeu après timeout ne crée pas de doublon, même après une écriture suivante', async t => {
  const { api } = await fixture(t);
  const item = food();
  const first = { operationId: randomUUID(), baseVersion: 0, food: item };
  await api(request(first));
  await api(request({ operationId: randomUUID(), baseVersion: 1, food: { ...item, name: 'Lait' } }));
  assert.deepEqual(await (await api(request(first))).json(), { ok: true, version: 1 });
  const result = await (await api(request())).json();
  assert.equal(result.length, 1);
  assert.equal(result[0].name, 'Lait');
  assert.equal(result[0].version, 2);
});

test('deux modifications concurrentes : une seule gagne et le conflit contient la version serveur', async t => {
  const { api } = await fixture(t);
  const item = food();
  await api(request({ operationId: randomUUID(), baseVersion: 0, food: item }));
  const responses = await Promise.all(['Pommes', 'Poires'].map(name => api(request({ operationId: randomUUID(), baseVersion: 1, food: { ...item, name } }))));
  assert.deepEqual(responses.map(r => r.status), [200, 409]);
  assert.equal((await responses[1].json()).current.name, 'Pommes');
});

test('une suppression est conservée pour les clients qui se reconnectent', async t => {
  const { api } = await fixture(t);
  const item = food();
  await api(request({ operationId: randomUUID(), baseVersion: 0, food: item }));
  await api(request({ operationId: randomUUID(), baseVersion: 1, food: { ...item, deleted: true } }));
  assert.equal((await (await api(request())).json())[0].deleted, true);
});

test('dates impossibles, photos invalides et absence de jeton sont refusées', async t => {
  const { api } = await fixture(t);
  assert.equal((await api(request(null, 'incorrect'))).status, 401);
  for (const change of [{ date: '2026-02-31' }, { photoData: 'file:///private/photo.jpg' }, { name: ' ' }]) {
    assert.equal((await api(request({ operationId: randomUUID(), baseVersion: 0, food: { ...food(), ...change } }))).status, 400);
  }
});
