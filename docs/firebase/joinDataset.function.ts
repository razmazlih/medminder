import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { createHash } from "crypto";

admin.initializeApp();

const DATASETS_COLLECTION = "datasets_v2";
const APP_OWNERS_COLLECTION = "appOwners_v2";
const STATUS_ACTIVE = "ACTIVE";

function datasetIdFromSyncCode(syncCode: string): string {
  return createHash("sha256")
    .update(`medminder|${syncCode.trim()}`)
    .digest("hex")
    .slice(0, 40);
}

export const joinDataset = functions.https.onCall(async (data, context) => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Anonymous auth required");
  }

  const syncCode = String(data?.syncCode ?? "").trim();
  if (syncCode.length < 8) {
    throw new functions.https.HttpsError("invalid-argument", "syncCode is too short");
  }

  const datasetId = datasetIdFromSyncCode(syncCode);
  const firestore = admin.firestore();
  const datasetRef = firestore.collection(DATASETS_COLLECTION).doc(datasetId);
  const ownerRef = firestore.collection(APP_OWNERS_COLLECTION).doc(uid);

  await firestore.runTransaction(async tx => {
    const [datasetSnapshot, ownerSnapshot] = await Promise.all([tx.get(datasetRef), tx.get(ownerRef)]);
    if (datasetSnapshot.exists) {
      const ownerUid = datasetSnapshot.get("ownerUid");
      if (typeof ownerUid !== "string" || ownerUid !== uid) {
        throw new functions.https.HttpsError("permission-denied", "dataset belongs to a different owner");
      }
    }

    tx.set(
      datasetRef,
      {
        ownerUid: uid,
        status: STATUS_ACTIVE,
        schemaVersion: 2,
        createdAt: datasetSnapshot.get("createdAt") ?? admin.firestore.FieldValue.serverTimestamp(),
        activatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );
    tx.set(ownerRef, { activeDatasetId: datasetId }, { merge: true });

    const previousDatasetId = ownerSnapshot.get("activeDatasetId");
    if (typeof previousDatasetId === "string" && previousDatasetId && previousDatasetId !== datasetId) {
      tx.delete(firestore.collection(DATASETS_COLLECTION).doc(previousDatasetId));
    }
  });

  return { datasetId };
});
