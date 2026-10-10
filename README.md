# MetroPulse — Enhanced Hackathon Project

This bundle includes the Node.js backend/web admin dashboard and the redesigned native Android employee app.

## What changed in the Android app (v1.1.0)

- A redesigned native home screen with a branded hero, connection state, reporting stats and recent activity.
- Four bottom tabs: Home, Report, My reports and Settings.
- The server URL is now tucked away in **Settings → Operations connection** instead of sitting on the reporting screen.
- Saved staff profile: name, role and default station.
- Quick-report shortcuts for Cleaning, Equipment and Safety that preselect category and suggested priority.
- My Reports view with local references and queued/synced status; retry synchronization.
- Local queue remains available if the server cannot be reached.

## Build/install the updated Android app

1. Extract this ZIP.
2. Open `MetroPulseEmployeeAndroid` in Android Studio.
3. Let Gradle sync complete.
4. Select **Build → Generate APKs** or **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
5. Install `MetroPulseEmployeeAndroid/app/build/outputs/apk/debug/app-debug.apk` on the phone.

The app's `versionCode` is now 2 and `versionName` is 1.1.0 so it can upgrade the earlier 1.0.0 build if both builds use the same Android Studio debug-signing key. If Android asks to uninstall the previous app, export any local queued reports first; uninstalling clears local app storage.

## Backend and dashboard

The Node.js backend/web dashboard is in `metropulse`. On Windows, open PowerShell in that folder and run:

```powershell
npm.cmd start
```

Admin dashboard: `http://localhost:3000/?view=admin`

Employee app: `http://localhost:3000/?view=employee` (the Android app is now the main staff-reporting interface).

For cross-network demo, expose the backend on a public HTTPS URL with a tunnel or hosting provider and keep both backend and tunnel running. Enter that base URL on the phone under **Settings → Operations connection** and tap **Save address & test connection**. Use the same base URL followed by `/?view=admin` to open the web admin dashboard.

## Demo flow

1. Start the server.
2. In Android app → Settings, set the public server URL and test it.
3. From Home, choose a quick-report shortcut or open Report.
4. Submit an incident; it is saved locally first and synced to `/api/reports` when available.
5. Open the admin dashboard to show the incoming incident.
6. Use the dashboard to update its status.

## Important limitations

This is a hackathon prototype, not an operational metro system. Use synthetic demo reports. It does not authenticate staff, dispatch real crews, or provide emergency handling. Before production, add secure identity/role access, a durable database, encrypted local storage, audit logs, managed HTTPS hosting, retention policies, operational monitoring and integration with approved station procedures. For immediate danger, staff must follow official station emergency procedures.


## v1.2 recovery notes

- The Android My Reports page displays each saved report's description.
- The web admin incident register has a dedicated description column.
- `/api/health` reports the current server-side report count for diagnostics.
- If a tunnel URL or backend folder changes, open the Android app → My Reports → **Restore local history to current server**. The app re-sends locally saved reports using stable `clientId` values; the API deduplicates reports already present on the server.
- The server stores reports in `metropulse/reports.json` beside `server.js`. Starting the server from a different extracted folder means it will use that folder's separate `reports.json`. Keep the same backend directory if you want the same data store.


## Important when installing this update

Use the **backend and Android project from this same ZIP**. The web dashboard is served from `metropulse/public`, so rebuilding only the Android APK will not update the description column on an older web server folder. If you made a custom `.env`/admin PIN in an older copy, copy that `.env` into the `metropulse` folder of this updated project before starting it. The updated server writes its report data to `metropulse/reports.json` and prints the exact file path plus report count at startup.
