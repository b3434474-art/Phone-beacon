# PhoneBeacon

PhoneBeacon is a consent-based family phone-finder project made of two independent Android apps:

- **PhoneBeacon Controller** — the family member's controller app.
- **PhoneBeacon Companion** — the phone owner's app that explicitly shares location and receives approved find-phone alerts.

## Project status

This repository contains the initial Android Studio project scaffold. Location sharing, remote commands, authentication, sound upload, and device pairing are not implemented yet.

## Privacy and safety

- Pairing requires approval on the Companion phone.
- Location sharing must be explicitly enabled by its owner and remains visibly indicated.
- The Companion owner can pause sharing and unpair at any time.
- Chimes are user-authorized find-phone alerts, with an immediate local stop control and a bounded alert duration.
- Never use PhoneBeacon to track someone without their informed permission.
- Do not commit credentials, service-role keys, private location data, or personal audio files.

## Apps

| Module | Application ID | Purpose |
|---|---|---|
| Controller | `com.phonebeacon.controller` | View consented location and request a find-phone alert |
| Companion | `com.phonebeacon.companion` | Share location and receive approved alerts |

## Build

Open this repository in Android Studio with Android SDK 35 installed, then build either app module.

The current screens are starter placeholders. Backend credentials and the Supabase schema will be added only after the consent and access model is reviewed.
