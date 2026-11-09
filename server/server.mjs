import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';
import { createApi } from './api.mjs';

const api = createApi(process.env.DATA_DIR ?? fileURLToPath(new URL('.data/', import.meta.url)));
const server = createServer(async (req, res) => {
  try {
    if (req.url?.split('?')[0] !== '/api/foods') { res.writeHead(404); res.end(); return; }
    let size = 0;
    const chunks = [];
    for await (const chunk of req) {
      size += chunk.length;
      if (size > 420_000) { res.writeHead(413); res.end(); return; }
      chunks.push(chunk);
    }
    const response = await api(new Request(`http://localhost${req.url}`, {
      method: req.method, headers: req.headers,
      ...(['GET', 'HEAD'].includes(req.method) ? {} : { body: Buffer.concat(chunks) }),
    }));
    res.writeHead(response.status, { ...Object.fromEntries(response.headers), 'X-Content-Type-Options': 'nosniff' });
    res.end(Buffer.from(await response.arrayBuffer()));
  } catch (error) {
    console.error('API indisponible :', error.message);
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Stockage temporairement indisponible.' }));
  }
});
server.listen(Number(process.env.PORT ?? 8787), process.env.HOST ?? '127.0.0.1', () => console.log('Vide-frigo API : http://127.0.0.1:8787'));
