# PhoneBeacon

PhoneBeacon is a private, consent-based phone-finder project with two separate Android apps:
- **PhoneBeacon Controller** — generates a one-time pairing code and sends find-phone commands.
- **PhoneBeacon Companion** — accepts the code on the phone owner's device and plays approved alerts.

## Included in this code version
- Separate Controller and Companion Android app modules.
- Six-digit one-time pairing code with a **10-minute expiration**; the code is consumed after a successful claim.
- Random per-install secrets; the backend stores SHA-256 hashes rather than raw secrets.
- Controller commands: chime, flashlight pulse, white screen, and stop.
- Companion can choose an audio file on the phone for its chime.
- Companion has a local Stop Alert control.
- Flashlight use requires the Companion owner to grant Android camera permission.
- HTTPS-only backend calls.
- **No location feature, GPS permission, location sharing, or location history.**

## Backend setup (required before the apps can connect)
1. Create a Supabase project.
2. Open SQL Editor and run `supabase/phonebeacon.sql`.
3. Copy the Project URL and anon/public key from the project's API settings.
4. Add these properties to your local Gradle user properties file (`~/.gradle/gradle.properties` on macOS/Linux, or `%USERPROFILE%\.gradle\gradle.properties` on Windows). Do not commit them:

```properties
PHONEBEACON_SUPABASE_URL=https://YOUR-PROJECT.supabase.co
PHONEBEACON_SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_PUBLIC_KEY
```

5. Open the repository in Android Studio, sync Gradle, and build both app modules.

**Never use a Supabase service-role key in either Android app.** The anon key is intended to be public; the per-install random secrets authorize each paired phone. Keep the repository private while developing and do not upload personal audio files or credentials.

## Pairing and use
1. Build and install Controller on your phone.
2. Build and install Companion on the phone owner's device.
3. Open Controller and generate a code.
4. Enter that code in Companion within 10 minutes.
5. After it is claimed, the apps reuse their saved installation secrets; a new code is needed after reinstalling or creating a replacement pairing.
6. Use Controller's chime, flashlight, white-screen, and stop controls.

## Current limitations
- This is an initial implementation, not a tested release APK. Build and test both modules before relying on it.
- Companion currently polls for commands while its screen is open. Android may pause network work when the app is backgrounded or the phone is in Doze; dependable background alerts need a properly configured push/foreground-service design.
- Remote commands require both phones to have internet access and the Supabase backend to be configured.
- The Companion owner's local Stop Alert control remains available while the app is open.
- DND override is not included; Android requires separate user-granted notification-policy access and behavior varies by device.
- Before broad distribution, add server-side attempt throttling and abuse monitoring to the pairing endpoint.

## Modules
| Module | Application ID |
|---|---|
| Controller | `com.phonebeacon.controller` |
| Companion | `com.phonebeacon.companion` |

Open the project in Android Studio with Android SDK 35 installed.