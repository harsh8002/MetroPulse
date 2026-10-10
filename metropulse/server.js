'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, 'public');
const DATA_FILE = path.join(ROOT, 'reports.json');

// Tiny .env reader so the demo needs no third-party packages.
try {
  const envText = fs.readFileSync(path.join(ROOT, '.env'), 'utf8');
  for (const line of envText.split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match && process.env[match[1]] === undefined) process.env[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
  }
} catch (_) {}

const PORT = Number(process.env.PORT || 3000);
const ADMIN_PIN = process.env.ADMIN_PIN || 'MetroDemo2026!';
const adminTokens = new Map();
const loginAttempts = new Map();
const MIME = {
  '.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8', '.js':'text/javascript; charset=utf-8',
  '.json':'application/json; charset=utf-8', '.svg':'image/svg+xml', '.png':'image/png', '.ico':'image/x-icon',
  '.webmanifest':'application/manifest+json', '.txt':'text/plain; charset=utf-8'
};

function readReports() {
  try {
    if (!fs.existsSync(DATA_FILE)) return [];
    const parsed = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    return Array.isArray(parsed) ? parsed : [];
  } catch (err) {
    console.error('Could not read reports.json:', err.message);
    return [];
  }
}
function writeReports(reports) {
  const tempPath = `${DATA_FILE}.tmp`;
  fs.writeFileSync(tempPath, JSON.stringify(reports, null, 2));
  fs.renameSync(tempPath, DATA_FILE);
}
function cleanText(value, maxLen = 200) {
  return String(value ?? '').trim().replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '').slice(0, maxLen);
}
function json(res, status, body, extraHeaders = {}) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { 'Content-Type':'application/json; charset=utf-8', 'Cache-Control':'no-store', 'X-Content-Type-Options':'nosniff', ...extraHeaders });
  res.end(payload);
}
function readBody(req, limit = 256 * 1024) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', chunk => {
      data += chunk;
      if (Buffer.byteLength(data) > limit) {
        reject(Object.assign(new Error('Request body too large.'), { statusCode:413 }));
        req.destroy();
      }
    });
    req.on('end', () => {
      if (!data) return resolve({});
      try { resolve(JSON.parse(data)); }
      catch (_) { reject(Object.assign(new Error('Request body must be valid JSON.'), { statusCode:400 })); }
    });
    req.on('error', reject);
  });
}
function tokenFrom(req) {
  const header = req.headers.authorization || '';
  return header.startsWith('Bearer ') ? header.slice(7) : '';
}
function isAdmin(req) {
  const token = tokenFrom(req);
  const expires = adminTokens.get(token);
  if (!token || !expires || expires < Date.now()) {
    if (token) adminTokens.delete(token);
    return false;
  }
  return true;
}
function serveStatic(req, res, pathname) {
  let decoded;
  try { decoded = decodeURIComponent(pathname); }
  catch (_) { res.writeHead(400); return res.end('Bad path'); }
  if (decoded === '/') decoded = '/index.html';
  const target = path.resolve(PUBLIC_DIR, `.${decoded}`);
  if (!target.startsWith(PUBLIC_DIR + path.sep) && target !== path.join(PUBLIC_DIR, 'index.html')) {
    res.writeHead(403); return res.end('Forbidden');
  }
  fs.readFile(target, (err, buffer) => {
    if (err) {
      if (err.code === 'ENOENT' || err.code === 'EISDIR') { res.writeHead(404, { 'Content-Type':'text/plain; charset=utf-8' }); return res.end('Not found'); }
      res.writeHead(500); return res.end('Internal server error');
    }
    const headers = {
      'Content-Type':MIME[path.extname(target).toLowerCase()] || 'application/octet-stream',
      'X-Content-Type-Options':'nosniff',
      'Referrer-Policy':'strict-origin-when-cross-origin',
      'Cache-Control': path.basename(target) === 'index.html' ? 'no-cache' : 'public, max-age=3600'
    };
    if (path.basename(target) === 'sw.js') headers['Service-Worker-Allowed'] = '/';
    if (req.method === 'HEAD') { res.writeHead(200, headers); return res.end(); }
    res.writeHead(200, headers); res.end(buffer);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  try {
    if (pathname === '/api/health' && req.method === 'GET') {
      return json(res, 200, { ok:true, service:'MetroPulse API', reportCount:readReports().length, time:new Date().toISOString() });
    }
    if (pathname === '/api/admin/login' && req.method === 'POST') {
      const ip = req.socket.remoteAddress || 'unknown';
      const attempt = loginAttempts.get(ip) || { count:0, blockedUntil:0 };
      if (attempt.blockedUntil > Date.now()) return json(res, 429, { error:'Too many attempts. Wait a minute and try again.' });
      const body = await readBody(req);
      const supplied = String(body.pin || '');
      const suppliedBuf = Buffer.from(supplied);
      const expectedBuf = Buffer.from(ADMIN_PIN);
      if (suppliedBuf.length !== expectedBuf.length || !crypto.timingSafeEqual(suppliedBuf, expectedBuf)) {
        attempt.count += 1;
        if (attempt.count >= 6) { attempt.count = 0; attempt.blockedUntil = Date.now() + 60_000; }
        loginAttempts.set(ip, attempt);
        return json(res, 401, { error:'That admin PIN is not correct.' });
      }
      loginAttempts.delete(ip);
      const token = crypto.randomBytes(32).toString('hex');
      adminTokens.set(token, Date.now() + 12 * 60 * 60 * 1000);
      return json(res, 200, { token, expiresInHours:12 });
    }
    if (pathname === '/api/admin/logout' && req.method === 'POST') {
      if (!isAdmin(req)) return json(res, 401, { error:'Admin login required.' });
      adminTokens.delete(tokenFrom(req));
      return json(res, 200, { ok:true });
    }
    if (pathname === '/api/reports' && req.method === 'GET') {
      if (!isAdmin(req)) return json(res, 401, { error:'Admin login required.' });
      const reports = readReports().sort((a,b) => new Date(b.createdAt) - new Date(a.createdAt));
      return json(res, 200, { reports, serverTime:new Date().toISOString() });
    }
    if (pathname === '/api/reports' && req.method === 'POST') {
      const body = await readBody(req);
      const allow = {
        roles:['Sweeper / Housekeeping','Security','Technician','Ticketing staff','Station controller','Operations officer','Senior officer','Other'],
        categories:['Cleaning & hygiene','Electrical','Lift / escalator','Ticketing gate','Safety / security','Train / track','Signage / information','Other'],
        severities:['Critical','High','Normal','Low'],
        stations:['Mahatma Mandir','GIFT City','GNLU','Koba Circle','Dholakuva','Infocity','Sector 1','Sector 10A','Sachivalaya','Akshardham','Other']
      };
      const reportInput = {
        clientId:cleanText(body.clientId,80), reporterName:cleanText(body.reporterName,80), role:cleanText(body.role,60),
        station:cleanText(body.station,60), category:cleanText(body.category,80), location:cleanText(body.location,100),
        severity:cleanText(body.severity,20), description:cleanText(body.description,1000)
      };
      if (!reportInput.clientId || !reportInput.role || !reportInput.station || !reportInput.category || !reportInput.location || !reportInput.severity || !reportInput.description) {
        return json(res, 400, { error:'Please complete all required fields.' });
      }
      if (!allow.roles.includes(reportInput.role) || !allow.stations.includes(reportInput.station) || !allow.categories.includes(reportInput.category) || !allow.severities.includes(reportInput.severity)) {
        return json(res, 400, { error:'One or more selected values are invalid.' });
      }
      if (reportInput.description.length < 8) return json(res, 400, { error:'Please add a little more detail (at least 8 characters).' });
      const reports = readReports();
      const existing = reports.find(r => r.clientId === reportInput.clientId);
      if (existing) return json(res, 200, { report:existing, duplicate:true });
      const now = new Date();
      const dateTag = now.toISOString().slice(0,10).replace(/-/g,'');
      const report = {
        id:crypto.randomUUID(), incidentId:`MP-${dateTag}-${crypto.randomBytes(2).toString('hex').toUpperCase()}`,
        ...reportInput, reporterName:reportInput.reporterName || 'Staff member', status:'Open',
        createdAt:now.toISOString(), updatedAt:now.toISOString()
      };
      reports.unshift(report); writeReports(reports);
      return json(res, 201, { report });
    }
    const updateMatch = pathname.match(/^\/api\/reports\/([^/]+)$/);
    if (updateMatch && req.method === 'PATCH') {
      if (!isAdmin(req)) return json(res, 401, { error:'Admin login required.' });
      const body = await readBody(req);
      const status = cleanText(body.status,30);
      if (!['Open','In Progress','Resolved'].includes(status)) return json(res, 400, { error:'Invalid incident status.' });
      const id = decodeURIComponent(updateMatch[1]);
      const reports = readReports();
      const report = reports.find(r => r.id === id || r.incidentId === id);
      if (!report) return json(res, 404, { error:'Incident not found.' });
      report.status = status; report.updatedAt = new Date().toISOString(); writeReports(reports);
      return json(res, 200, { report });
    }
    if (pathname.startsWith('/api/')) return json(res, 404, { error:'API endpoint not found.' });
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405, { Allow:'GET, HEAD' }); return res.end('Method not allowed'); }
    return serveStatic(req, res, pathname);
  } catch (err) {
    if (res.headersSent || res.destroyed) return;
    const status = err.statusCode || 500;
    if (status >= 500) console.error(err);
    return json(res, status, { error:status >= 500 ? 'Internal server error.' : err.message });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`MetroPulse is running at http://localhost:${PORT}`);
  console.log(`Persistent report store: ${DATA_FILE}`);
  console.log(`Reports currently stored: ${readReports().length}`);
  console.log(`Admin PIN is loaded from ${process.env.ADMIN_PIN ? 'environment / .env' : 'the demo default'}. Change it before public deployment.`);
});
