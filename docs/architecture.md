# MedMinder Architecture (MVP)

## Stack
- Kotlin + Jetpack Compose
- Hilt dependency injection
- Room (offline-first local storage)
- MVVM + UseCases + Repository
- AlarmManager-based exact reminder scheduling

## Layers
- `feature/*`: UI screens + ViewModels
- `domain/*`: models, repository contracts, use cases
- `data/*`: Room entities/DAO and repository implementation
- `reminder/*`: AlarmManager scheduler, receivers, notifications
- `core/*`: time providers, security hash, theme, defaults

## Reminder Reliability
- Dose events are persisted in `dose_event`.
- On app init and BOOT/TIMEZONE/TIME changes:
  1. generate future dose events
  2. mark old pending events as missed
  3. reschedule pending alarms

## Accessibility
- Large touch targets (`>=48dp`) for action buttons.
- RTL forced at app root for Hebrew MVP.
- High-contrast theme flag from settings.
- Semantics descriptions on key controls.

## Security and Privacy
- `allowBackup=false` to avoid cloud backup leakage of medical data.
- Optional app lock setting with PBKDF2 PIN hashing.
- No deprecated `security-crypto` dependency.

## Data Schema
- `user_profile`, `medication`, `medication_weekday`, `medication_time`, `dose_event`, `app_settings`, `pin_credential`.
- Unique dose constraint: `(medicationId, scheduledEpochMs)`.
