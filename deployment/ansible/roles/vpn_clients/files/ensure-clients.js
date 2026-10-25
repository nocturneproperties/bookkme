const { execFileSync } = require('node:child_process');

const panelUrl = 'http://127.0.0.1:51821';
const loginRejectedExitCode = 2;

class LoginRejected extends Error {}

const readRequest = async () => {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return JSON.parse(Buffer.concat(chunks).toString());
};

const openSession = async (password) => {
  const response = await fetch(`${panelUrl}/api/session`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  });
  if (response.status === 401) throw new LoginRejected('Panel login rejected');
  if (!response.ok) throw new Error(`Panel login failed with ${response.status}`);
  const cookie = response.headers.getSetCookie().map((header) => header.split(';')[0]).join('; ');
  return async (path, init = {}) => {
    const reply = await fetch(`${panelUrl}${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', Cookie: cookie },
    });
    if (!reply.ok) throw new Error(`${init.method ?? 'GET'} ${path} failed with ${reply.status}`);
    return reply;
  };
};

const listClients = async (call) => (await call('/api/wireguard/client')).json();

const networkPrefix = (address) => address.split('.').slice(0, 3).join('.');

const hostNumber = (address) => address.split('.')[3];

const restartInterface = () => {
  execFileSync('wg-quick', ['down', 'wg0'], { stdio: ['ignore', 'ignore', 'pipe'] });
  execFileSync('wg-quick', ['up', 'wg0'], { stdio: ['ignore', 'ignore', 'pipe'] });
};

const renumber = async (call, addressTemplate) => {
  const prefix = networkPrefix(addressTemplate);
  const config = JSON.parse(await (await call('/api/wireguard/backup')).text());
  if (networkPrefix(config.server.address) === prefix) return false;
  config.server.address = `${prefix}.${hostNumber(config.server.address)}`;
  for (const client of Object.values(config.clients)) {
    client.address = `${prefix}.${hostNumber(client.address)}`;
  }
  await call('/api/wireguard/restore', { method: 'PUT', body: JSON.stringify({ file: JSON.stringify(config) }) });
  restartInterface();
  return true;
};

const createClient = (call, name) => call('/api/wireguard/client', { method: 'POST', body: JSON.stringify({ name }) });

const pinAddresses = async (call, addresses) => {
  const existing = await listClients(call);
  const moved = [];
  for (const [name, address] of Object.entries(addresses)) {
    const holder = existing.find((client) => client.address === address && client.name !== name);
    if (holder) throw new Error(`${address} belongs to client '${holder.name}', move it to another address in the panel so '${name}' can take it`);
    const client = existing.find((candidate) => candidate.name === name);
    if (client.address !== address) {
      await call(`/api/wireguard/client/${client.id}/address`, { method: 'PUT', body: JSON.stringify({ address }) });
      moved.push(name);
    }
  }
  return moved;
};

const main = async () => {
  const { password, clients, addresses, addressTemplate } = await readRequest();
  const call = await openSession(password);
  const renumbered = await renumber(call, addressTemplate);
  const existingNames = new Set((await listClients(call)).map((client) => client.name));
  const created = clients.filter((name) => !existingNames.has(name));
  const isPinned = (name) => Object.hasOwn(addresses, name);
  for (const name of created.filter(isPinned)) await createClient(call, name);
  const moved = await pinAddresses(call, addresses);
  for (const name of created.filter((name) => !isPinned(name))) await createClient(call, name);
  const clientsByName = new Map((await listClients(call)).map((client) => [client.name, client]));
  const configurations = {};
  for (const name of clients) {
    const { id } = clientsByName.get(name);
    configurations[name] = await (await call(`/api/wireguard/client/${id}/configuration`)).text();
  }
  process.stdout.write(JSON.stringify({ renumbered, created, moved, configurations }));
};

main().catch((error) => {
  process.stderr.write(`${error.message}\n`);
  process.exit(error instanceof LoginRejected ? loginRejectedExitCode : 1);
});
