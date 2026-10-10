# MetroPulse — Hackathon MVP

An offline-first, mobile-friendly reporting app for metro employees, paired with a web admin dashboard.

## What is included

- **Employee phone view** (`/?view=employee`): staff role, station, issue category, exact location, severity and description.
- **Local-first queue:** report is saved in browser storage before a network attempt; queued reports retry when connection returns.
- **Shared server:** incidents from employee devices are stored in `reports.json` and returned to the admin console.
- **Admin web view** (`/?view=admin`): PIN login, auto-refreshing incident list, priority/status filters, status updates, summary stats and CSV export.
- **Installable PWA shell:** a service worker caches the app shell after first visit so the reporting screen can reopen offline.
- **Demo-safe distinction:** app-shell and local-queue offline support are real in this prototype; dashboard sync needs a reachable server. No real GMRC systems or live operational feeds are connected.

## Run locally

Requires Node.js 18 or newer. No npm packages are required.

1. Extract this folder.
2. In a terminal, move into the `metropulse` directory.
3. Copy `.env.example` to `.env` and set a private admin PIN. Example:

   ```env
   ADMIN_PIN=choose-a-private-demo-pin
   PORT=3000
   ```

4. Start the server:

   ```bash
   npm start
   ```

5. Open `http://localhost:3000/?view=employee` for the staff app and `http://localhost:3000/?view=admin` for the admin console. The admin view asks for the PIN from `.env`.

If you skip `.env`, the demo PIN defaults to `MetroDemo2026!`. Change it before presenting the URL publicly.

## Demo on a phone on the same Wi-Fi / hotspot

1. Connect the laptop and phone to the same Wi-Fi or laptop hotspot.
2. Find the laptop's LAN IP (Windows: `ipconfig`, Linux/macOS: `ip addr` or `ifconfig`).
3. On the phone, open `http://LAPTOP-LAN-IP:3000/?view=employee`.
4. On the laptop, open `http://localhost:3000/?view=admin`, sign in, and submit a report from the phone. The report should appear in the dashboard within about 3.5 seconds.
5. If the phone browser offers it, use **Add to Home Screen** after the first successful load. The service worker caches the app shell for later offline reopening.

Both devices must be able to reach the laptop server for reports to sync. Local offline submissions stay queued on the phone; they are uploaded automatically when it can reach the server again. Note: browser service workers require HTTPS (or localhost). When the phone opens the laptop over plain `http://LAN-IP`, the queue still works while the page is open, but reopening the app fully offline requires serving it over HTTPS.

## Deploying so different networks can connect

Host the Node app on a public Node hosting service with HTTPS and persistent disk/storage. Set `ADMIN_PIN` as a host environment variable. The included JSON file is suitable for a hackathon demo, not a production multi-instance deployment: ephemeral hosting storage may reset and simultaneous writes are not database-grade. Use PostgreSQL or another managed database, proper employee/admin authentication, audit logs, backups, rate limiting and HTTPS for any real rollout.

## Demo checklist

- [ ] Open admin console and sign in.
- [ ] Open employee app on a phone.
- [ ] Submit an escalator / electrical / cleanliness report.
- [ ] Observe the report arrive on admin console.
- [ ] Change status from Open to In Progress to Resolved.
- [ ] Export filtered reports as CSV.
- [ ] Optional offline test: load the app once, switch phone to airplane mode, submit one report, confirm it appears as waiting to sync, restore connectivity to the same server, and confirm it synchronizes.

## Important safety note

This is a prototype, not an official metro system. Do not use it as emergency dispatch or for safety-critical decisions. Never enter passenger personal information or credentials.
