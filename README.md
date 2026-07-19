# MedMinder

MedMinder is a Hebrew-first, local-first medication management system with an
Android app and a companion web client. It supports medication schedules,
reminders, intake history, accessibility-focused RTL interfaces, encrypted
Firebase synchronization, and CSV/PDF report exports.

## Applications

- **Android:** Kotlin, Jetpack Compose, Room, Hilt, and AlarmManager.
- **Web:** React, TypeScript, Vite, Dexie, Zustand, and Firebase.
- **Backend:** Firebase Authentication, Firestore, and Cloud Functions.

Medical records are stored locally first. Optional cloud synchronization uses
end-to-end encrypted payloads; the repository intentionally excludes local
environment files, Firebase client configuration, credentials, keys, and build
artifacts.

## Documentation

- [Architecture](docs/architecture.md)
- [Project build process](docs/project-build-process.md)
- [Web client setup](web/README.md)
- [Firebase setup](docs/firebase/README.md)

## Local configuration

Copy `web/.env.example` to `web/.env` and provide your own Firebase values.
For Android, place your own `google-services.json` inside `app/`. These local
configuration files are ignored by Git and must never be committed.
