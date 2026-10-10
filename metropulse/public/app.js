(() => {
  'use strict';
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const QUEUE_KEY = 'metropulse_pending_v1';
  const MINE_KEY = 'metropulse_mine_v1';
  const TOKEN_KEY = 'metropulse_admin_token_v1';
  let adminToken = sessionStorage.getItem(TOKEN_KEY) || '';
  let adminReports = [];
  let pollTimer = null;
  let syncing = false;
  let toastTimer = null;

  function readStore(key, fallback = []) {
    try { const val = JSON.parse(localStorage.getItem(key) || 'null'); return val ?? fallback; }
    catch (_) { return fallback; }
  }
  function writeStore(key, value) { localStorage.setItem(key, JSON.stringify(value)); }
  function getQueue() { return readStore(QUEUE_KEY, []); }
  function getMine() { return readStore(MINE_KEY, []); }
  function escapeHTML(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  }
  function relativeTime(dateValue) {
    const date = new Date(dateValue);
    if (Number.isNaN(date.getTime())) return 'Just now';
    const diff = Math.max(0, Date.now() - date.getTime());
    if (diff < 60_000) return 'Just now';
    if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}m ago`;
    if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}h ago`;
    return date.toLocaleDateString('en-IN', { day:'2-digit', month:'short' });
  }
  function fullDate(dateValue) {
    const d = new Date(dateValue);
    if (Number.isNaN(d.getTime())) return '';
    return d.toLocaleString('en-IN', { day:'2-digit', month:'short', hour:'2-digit', minute:'2-digit', hour12:true });
  }
  function toast(message, kind = 'success') {
    const region = $('#toast-region');
    const item = document.createElement('div');
    item.className = `toast${kind === 'error' ? ' error' : ''}`;
    item.textContent = message;
    region.appendChild(item);
    setTimeout(() => item.remove(), 4300);
  }
  function getAdminHeaders() { return { 'Content-Type':'application/json', 'Authorization':`Bearer ${adminToken}` }; }
  async function apiFetch(path, options = {}) {
    const response = await fetch(path, { ...options, headers: { ...(options.headers || {}) }, cache:'no-store' });
    let data = {};
    try { data = await response.json(); } catch (_) {}
    if (!response.ok) {
      if (response.status === 401 && path !== '/api/admin/login') handleSessionExpired();
      throw new Error(data.error || `Request failed (${response.status})`);
    }
    return data;
  }
  function handleSessionExpired() {
    adminToken = '';
    sessionStorage.removeItem(TOKEN_KEY);
    stopPolling();
    $('#admin-dashboard').classList.add('hidden');
    $('#admin-login-panel').classList.remove('hidden');
  }
  function updateNetwork() {
    const pill = $('#network-pill');
    const online = navigator.onLine;
    pill.classList.toggle('online', online);
    pill.classList.toggle('offline', !online);
    const label = pill.querySelector('span');
    if (label) label.textContent = online ? 'Connection available' : 'Offline mode';
    $('#queue-summary').textContent = getQueue().length
      ? `${getQueue().length} report${getQueue().length === 1 ? '' : 's'} waiting to sync`
      : (online ? 'Connected · reports syncing normally' : 'Offline · new reports stay on this device');
  }
  function renderMine() {
    const queue = getQueue();
    const mine = getMine().sort((a,b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 5);
    $('#mine-count').textContent = String(getMine().length);
    if (!mine.length && !queue.length) {
      $('#mine-list').innerHTML = '<div class="empty-mini"><span>↗</span><p>Your submitted reports will appear here.</p></div>';
      updateNetwork();
      return;
    }
    const queuedIds = new Set(queue.map(x => x.clientId));
    const items = mine.map(r => ({ ...r, pending: queuedIds.has(r.clientId) }));
    queue.filter(q => !items.some(r => r.clientId === q.clientId)).forEach(q => items.unshift({ ...q, pending:true, status:'Queued offline' }));
    $('#mine-list').innerHTML = items.slice(0,5).map(r => `
      <div class="mine-item">
        <div class="mine-item-top"><strong>${escapeHTML(r.incidentId || 'LOCAL QUEUE')}</strong><small>${escapeHTML(relativeTime(r.createdAt))}</small></div>
        <p>${escapeHTML(r.category)} · ${escapeHTML(r.station)}</p>
        <span class="mini-status ${r.pending ? 'pending' : ''}">${r.pending ? 'Waiting to sync' : escapeHTML(r.status || 'Submitted')}</span>
      </div>`).join('');
    updateNetwork();
  }
  function addMine(report) {
    const mine = getMine();
    const existingIndex = mine.findIndex(r => r.clientId === report.clientId);
    if (existingIndex >= 0) mine[existingIndex] = { ...mine[existingIndex], ...report };
    else mine.unshift(report);
    writeStore(MINE_KEY, mine.slice(0,30));
  }
  async function syncPending() {
    if (syncing || !navigator.onLine) { updateNetwork(); return; }
    const queue = getQueue();
    if (!queue.length) { updateNetwork(); return; }
    syncing = true;
    try {
      for (const item of queue) {
        try {
          const data = await apiFetch('/api/reports', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(item) });
          const remaining = getQueue().filter(q => q.clientId !== item.clientId);
          writeStore(QUEUE_KEY, remaining);
          addMine(data.report);
        } catch (err) {
          // Keep any report that cannot be delivered yet for a later retry.
          console.warn('Report remains queued:', err.message);
          break;
        }
      }
    } finally {
      syncing = false;
      renderMine();
      if (view === 'admin' && adminToken) refreshDashboard(false);
    }
  }

  const view = new URLSearchParams(location.search).get('view') === 'admin' ? 'admin' : 'employee';
  $('#employee-view').classList.toggle('hidden', view !== 'employee');
  $('#admin-view').classList.toggle('hidden', view !== 'admin');
  $('#employee-nav').classList.toggle('active', view === 'employee');
  $('#admin-nav').classList.toggle('active', view === 'admin');
  document.title = view === 'admin' ? 'MetroPulse — Admin Console' : 'MetroPulse — Employee App';
  $('#char-count').textContent = '0 / 1000';

  const desc = $('textarea[name="description"]');
  if (desc) desc.addEventListener('input', () => { $('#char-count').textContent = `${desc.value.length} / 1000`; });

  $('#incident-form').addEventListener('submit', async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const error = $('#form-error');
    error.textContent = '';
    if (!form.reportValidity()) return;
    const fd = new FormData(form);
    const payload = {
      clientId: (crypto.randomUUID ? crypto.randomUUID() : `mp-${Date.now()}-${Math.random().toString(16).slice(2)}`),
      reporterName: String(fd.get('reporterName') || '').trim(),
      role: String(fd.get('role') || ''),
      station: String(fd.get('station') || ''),
      category: String(fd.get('category') || ''),
      location: String(fd.get('location') || '').trim(),
      severity: String(fd.get('severity') || ''),
      description: String(fd.get('description') || '').trim(),
      createdAt: new Date().toISOString(),
      status: 'Open'
    };
    if (payload.description.length < 8) { error.textContent = 'Please add at least 8 characters describing the issue.'; return; }
    const button = $('.submit-button');
    button.disabled = true;
    button.querySelector('span:first-child').textContent = 'Saving report…';
    // Write to the device queue first. If the network is available, sync immediately after.
    const queue = getQueue();
    queue.push(payload);
    writeStore(QUEUE_KEY, queue);
    addMine({ ...payload, incidentId:'LOCAL QUEUE', status:'Queued offline' });
    form.reset();
    $('#char-count').textContent = '0 / 1000';
    await syncPending();
    const stillPending = getQueue().some(r => r.clientId === payload.clientId);
    button.disabled = false;
    button.querySelector('span:first-child').textContent = 'Submit incident report';
    renderMine();
    if (stillPending) toast('Report saved on this device. It will sync when the connection returns.', 'success');
    else {
      toast('Report submitted. It is now available in the admin console.');
      if (view === 'employee') window.scrollTo({ top: Math.max(0, $('#mine-list').getBoundingClientRect().top + window.scrollY - 150), behavior:'smooth' });
    }
  });

  $('#retry-sync').addEventListener('click', () => {
    if (!navigator.onLine) toast('You are offline. The report queue is safe on this device.', 'error');
    else { syncPending(); toast('Checking pending reports…'); }
  });
  window.addEventListener('online', () => { updateNetwork(); syncPending(); if (view === 'admin' && adminToken) refreshDashboard(false); });
  window.addEventListener('offline', updateNetwork);

  // Employee reports can be queued while the browser has no network. The static app shell is cached by sw.js.
  if ('serviceWorker' in navigator && location.protocol !== 'file:') {
    window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(err => console.warn('Service worker registration skipped:', err.message)));
  }

  // Admin view
  const loginForm = $('#admin-login-form');
  loginForm.addEventListener('submit', async event => {
    event.preventDefault();
    $('#login-error').textContent = '';
    const button = $('button[type="submit"]', loginForm);
    button.disabled = true;
    try {
      const data = await apiFetch('/api/admin/login', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({ pin:$('#admin-pin').value }) });
      adminToken = data.token;
      sessionStorage.setItem(TOKEN_KEY, adminToken);
      $('#admin-pin').value = '';
      $('#admin-login-panel').classList.add('hidden');
      $('#admin-dashboard').classList.remove('hidden');
      await refreshDashboard(false);
      startPolling();
      toast('Admin console unlocked.');
    } catch (err) {
      $('#login-error').textContent = err.message;
    } finally { button.disabled = false; }
  });

  function stopPolling() { if (pollTimer) clearInterval(pollTimer); pollTimer = null; }
  function startPolling() { stopPolling(); pollTimer = setInterval(() => refreshDashboard(false), 3500); }
  function priorityClass(s) { return String(s || '').toLowerCase(); }
  function statusClass(s) { return String(s || '').toLowerCase().replace(/\s+/g, '-'); }
  function computeStats(reports) {
    $('#stat-total').textContent = reports.length;
    $('#hero-total').textContent = reports.length;
    $('#stat-open').textContent = reports.filter(r => r.status === 'Open').length;
    $('#stat-high').textContent = reports.filter(r => ['High','Critical'].includes(r.severity)).length;
    $('#stat-resolved').textContent = reports.filter(r => r.status === 'Resolved').length;
  }
  function filteredReports() {
    const term = $('#report-search').value.trim().toLowerCase();
    const status = $('#status-filter').value;
    const severity = $('#severity-filter').value;
    return adminReports.filter(r => {
      if (status !== 'All' && r.status !== status) return false;
      if (severity !== 'All' && r.severity !== severity) return false;
      if (!term) return true;
      return [r.incidentId,r.station,r.location,r.category,r.reporterName,r.role,r.description].some(v => String(v || '').toLowerCase().includes(term));
    });
  }
  function renderReportTable() {
    const reports = filteredReports();
    $('#feed-count').textContent = String(adminReports.length);
    $('#no-results').classList.toggle('hidden', reports.length > 0);
    if (!reports.length) {
      $('#report-table-body').innerHTML = '<tr><td colspan="6" class="table-empty">No incidents to display. Reports submitted from an employee phone will appear here.</td></tr>';
      return;
    }
    $('#report-table-body').innerHTML = reports.map(r => `
      <tr data-id="${escapeHTML(r.id)}">
        <td><div class="incident-id">${escapeHTML(r.incidentId)}</div><div class="incident-type">${escapeHTML(r.category)}</div><div class="report-date">${escapeHTML(fullDate(r.createdAt))}</div></td>
        <td><div class="station-name">${escapeHTML(r.station)}</div><div class="station-location">${escapeHTML(r.location)}</div></td>
        <td class="description-cell"><div class="description-text">${escapeHTML(r.description || 'No description was saved for this report.')}</div></td>
        <td><div class="reporter-name">${escapeHTML(r.reporterName || 'Staff member')}</div><div class="reporter-role">${escapeHTML(r.role)}</div></td>
        <td><span class="priority-pill ${priorityClass(r.severity)}">${escapeHTML(r.severity)}</span></td>
        <td><div class="status-action"><span class="status-badge ${statusClass(r.status)}">${escapeHTML(r.status)}</span><select class="status-select" aria-label="Update status for ${escapeHTML(r.incidentId)}" data-status-id="${escapeHTML(r.id)}"><option${r.status==='Open'?' selected':''}>Open</option><option${r.status==='In Progress'?' selected':''}>In Progress</option><option${r.status==='Resolved'?' selected':''}>Resolved</option></select></div></td>
      </tr>`).join('');
    $$('[data-status-id]').forEach(select => select.addEventListener('change', async () => {
      const id = select.dataset.statusId;
      const nextStatus = select.value;
      select.disabled = true;
      try {
        const result = await apiFetch(`/api/reports/${encodeURIComponent(id)}`, { method:'PATCH', headers:getAdminHeaders(), body:JSON.stringify({ status:nextStatus }) });
        const index = adminReports.findIndex(r => r.id === id);
        if (index >= 0) adminReports[index] = result.report;
        computeStats(adminReports);
        renderReportTable();
        toast(`${result.report.incidentId} marked ${nextStatus.toLowerCase()}.`);
      } catch (err) { toast(err.message, 'error'); refreshDashboard(false); }
      finally { select.disabled = false; }
    }));
  }
  async function refreshDashboard(showToast = false) {
    if (!adminToken || !navigator.onLine) return;
    try {
      const data = await apiFetch('/api/reports', { headers:getAdminHeaders() });
      const oldNewest = adminReports[0]?.id;
      adminReports = data.reports || [];
      computeStats(adminReports);
      renderReportTable();
      $('#last-updated').textContent = `Updated ${new Date().toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit',second:'2-digit'})}`;
      if (showToast) toast('Dashboard refreshed.');
      if (oldNewest && adminReports[0] && oldNewest !== adminReports[0].id) $('#hero-total').classList.add('count-pop');
    } catch (err) {
      if (err.message !== 'Admin login required.') console.warn('Dashboard refresh failed:', err.message);
    }
  }
  $('#refresh-dashboard').addEventListener('click', () => refreshDashboard(true));
  $('#admin-logout').addEventListener('click', async () => {
    try { await apiFetch('/api/admin/logout', { method:'POST', headers:getAdminHeaders() }); } catch (_) {}
    adminToken = '';
    sessionStorage.removeItem(TOKEN_KEY);
    stopPolling();
    $('#admin-dashboard').classList.add('hidden');
    $('#admin-login-panel').classList.remove('hidden');
    toast('You have signed out.');
  });
  ['report-search','status-filter','severity-filter'].forEach(id => $(`#${id}`).addEventListener('input', renderReportTable));
  $('#export-csv').addEventListener('click', () => {
    const data = filteredReports();
    const cols = ['incidentId','createdAt','station','location','role','reporterName','category','severity','status','description'];
    const csvCell = value => `"${String(value ?? '').replace(/"/g,'""')}"`;
    const csv = [cols.join(','), ...data.map(r => cols.map(k => csvCell(r[k])).join(','))].join('\r\n');
    const blob = new Blob([csv], { type:'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `metropulse-incidents-${new Date().toISOString().slice(0,10)}.csv`;
    document.body.appendChild(a); a.click(); a.remove(); URL.revokeObjectURL(url);
  });

  updateNetwork();
  renderMine();
  syncPending();
  if (view === 'admin' && adminToken) {
    $('#admin-login-panel').classList.add('hidden');
    $('#admin-dashboard').classList.remove('hidden');
    refreshDashboard(false).then(startPolling);
  }
})();
