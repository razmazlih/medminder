# MedMinder Web

Web client (`React + TypeScript + Vite`) for the existing MedMinder Firebase sync backend.

## Stack
- React + TypeScript + Vite
- Zustand (UI state)
- Dexie (IndexedDB)
- Firebase Web SDK (modular)
- TanStack Query (sync/connect mutations + status query)
- Zod validation
- Vitest + Testing Library
- Playwright E2E

## Key behaviors
- Local-first: all writes land in IndexedDB first.
- Cloud is optional backup/sync.
- Anonymous Firebase Auth only.
- Web login via callable function `loginWebWithSyncCode(syncCode)`.
- Web logout via callable function `logoutWebSession(datasetId)`.
- E2E encryption of medical payloads with AES-GCM.
- Data key derivation with HKDF SHA-256:
  - salt: `medminder-sync-salt-v1`
  - info: `medminder-sync-data-key`
  - key length: 32 bytes
- AAD format: `datasetId|recordType|recordId|updatedAtEpochMs`
- Firestore documents:
  - path: `datasets_v2/{datasetId}/records/{recordType_recordId}`
  - encrypted fields: `iv`, `ciphertext`
  - tombstones do not contain `iv/ciphertext`
- Sync strategy: Pull -> Merge -> Push
  - conflict rule: latest `updatedAt` wins
  - soft delete with `deletedAt`
  - cloud delete notice logged as `cloud_deleted:*` and included in report exports

## Supported cloud record types
- `MEDICATION`
- `DOSE_EVENT`
- `HIDDEN_INTAKE_LOG`

(`user_profile` is not synced to cloud.)

## Environment setup
1. Copy `.env.example` to `.env`.
2. Fill Firebase web values from the same project used by Android.
3. Ensure Firebase has:
   - Authentication -> Anonymous enabled
   - Firestore rules deployed
   - Callable functions `joinDataset`, `loginWebWithSyncCode`, `logoutWebSession`

## Install
```bash
npm install
```

## Run locally
```bash
npm run dev
```

## Build
```bash
npm run build
```

## Type check
```bash
npm run typecheck
```

## Lint
```bash
npm run lint
```

## Tests
Unit + integration:
```bash
npm test
```

E2E:
```bash
npm run test:e2e
```

## Deploy
Any static hosting is fine (Firebase Hosting, Vercel, Netlify, Nginx).
Build output is in `dist/`.

Example Firebase Hosting flow:
```bash
npm run build
firebase deploy --only hosting
```

## Project structure
- `src/sync/` - sync engine + cloud adapters
- `src/data/` - local mutations and outbox helpers
- `src/db/` - Dexie schema
- `src/pages/` - app screens
- `src/core/` - crypto, exports, shared utilities
- `e2e/` - Playwright tests

## Current screens
- Connect (Sync Code from mobile app + Anonymous auth + `loginWebWithSyncCode`)
- Medications (create/edit/disable/restore)
- History (dose events, edit time, logical delete)
- Hidden log (`hidden_intake_log` list + search/filter)
- Settings (local-only toggle, sync status, sync now, reconnect, logout)

## Notes
- Full RTL layout is enabled.
- Reports export to CSV/PDF and include cloud deletion notices.
- For deterministic local/e2e runs use `VITE_USE_FAKE_CLOUD=1`.
