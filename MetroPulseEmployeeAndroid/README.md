# MetroPulse Employee Android App

A native Android client for the MetroPulse hackathon prototype. It uses the existing Node.js backend in `../metropulse`.

## What's improved in v1.1

- Redesigned home screen with a MetroPulse brand header, connection status, activity summary, quick-report shortcuts and recent activity.
- Four simple bottom tabs: Home, Report, My Reports and Settings.
- Server URL is stored under **Settings → Operations connection** instead of appearing on the report screen.
- Staff name, role and default station are remembered locally and can be changed in Settings.
- Quick-report shortcuts preselect common issue types and a suggested priority.
- Local report queue, pending/synced status, retry sync and local report history.
- Native interface uses Android widgets only; no third-party UI dependencies.

## Open in Android Studio

1. Extract the complete project ZIP.
2. In Android Studio select **Open** and select the `MetroPulseEmployeeAndroid` folder.
3. Let Gradle sync finish. The project uses Gradle 8.9, Android Gradle Plugin 8.7.3, JDK 17/21, compile SDK 35 and min SDK 26.
4. Select **Build → Generate APKs** or use **Build → Build Bundle(s) / APK(s) → Build APK(s)**, depending on your Android Studio version.
5. The debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

## Configure the server once on each phone

1. Start the Node.js backend from `../metropulse` using `npm.cmd start` on Windows.
2. For a demo over different networks, expose the local server using an HTTPS tunnel or deploy it to a public host. Keep the backend and tunnel running for the demo.
3. Open the Android app → **Settings** → **Operations connection**.
4. Paste the base server URL only, for example `https://your-name.trycloudflare.com` (do not append `/api/health`).
5. Tap **Save address & test connection**. The app checks `/api/health` itself.
6. Go back to Home; the server address is not shown in the normal reporting flow.

A local development URL such as `http://192.168.1.25:3000` also works when phone-to-laptop network access is enabled. The app infers HTTP for localhost/private IPv4 addresses without a scheme and HTTPS for public domain names without a scheme.

## Test the complete flow

1. Keep the Node server running.
2. In the app Settings, test the server URL.
3. Open **Report** (or use one of the Home quick-report cards), complete the required fields and submit.
4. The report is saved locally first. When the server is reachable, it is posted to `/api/reports` and its local entry changes to **Synced to server** with the server incident ID.
5. Open the web admin page at `https://your-name.trycloudflare.com/?view=admin` (or your deployment URL), sign in with the admin PIN configured in the backend `.env`, and view the incident.

## Notes / limitations

- This is a hackathon prototype, not a production metro application. Use synthetic demo reports and never enter passenger-sensitive information.
- A report queued offline is stored in Android SharedPreferences on the device. The app retries on app resume and when connectivity returns; keep the app open long enough for sync to complete.
- Server-side report management and status updates remain in the web admin console.
- Before production use, add staff authentication, role-based authorization, encrypted storage, an audit trail, secure managed hosting, retention rules, backend observability and integration with the operator's approved incident process.
- Do not treat this prototype as an emergency dispatch channel. Immediate safety issues must follow official station procedures.


### Restore phone history after changing server/tunnel
Open **My Reports** and tap **Restore local history to current server**. This retries every report saved on that phone against the configured server. The API deduplicates repeats by each report's `clientId`. Review the connection status in Settings; it now shows how many reports are stored server-side.
