# Firebase Sync Setup

## Android app
1. Add `google-services.json` to `/app/google-services.json`.
2. Enable Firebase Authentication with **Anonymous** provider.
3. Enable Firestore in production mode.

## Cloud Functions
Deploy callable functions from `functions/src/index.ts`:
- `joinDataset(syncCode)` for Android app owners only.
- `loginWebWithSyncCode(syncCode)` for Web sign-in by app-issued code.
- `logoutWebSession(datasetId)` to remove only the current Web session.
- `revokeDataset(datasetId)` for owner-initiated revocation.

Data model (v2):
- Dataset metadata: `datasets_v2/{datasetId}`
- Web sessions per dataset: `datasets_v2/{datasetId}/webMembers/{webUid}`
- Active dataset pointer per app-owner: `appOwners_v2/{ownerUid}.activeDatasetId`

## Firestore Rules
1. Apply [`firestore.rules`](./firestore.rules).
2. Rules allow read/write on `datasets_v2/{datasetId}/records/*` only if:
   - `request.auth.uid` is dataset owner (`ownerUid`), or
   - user has Web session doc in `webMembers/{uid}`.
3. Rules block direct client writes to dataset metadata, `webMembers`, and `appOwners_v2`.

## Data model in Firestore
- Path: `datasets_v2/{datasetId}/records/{recordType_recordId}`
- Fields:
  - `recordType`
  - `recordId`
  - `updatedAt`
  - `deletedAt` (tombstone soft-delete)
  - `source`
  - `schemaVersion`
  - `iv`
  - `ciphertext`

## One-command deploy (after login)
From project root:

```bash
firebase login
./scripts/deploy_firebase.sh
```
