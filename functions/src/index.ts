import * as admin from "firebase-admin";
import * as functions from "firebase-functions";
import { createHash } from "crypto";

admin.initializeApp();

const DATASETS_COLLECTION = "datasets_v2";
const APP_OWNERS_COLLECTION = "appOwners_v2";
const STATUS_ACTIVE = "ACTIVE";
const STATUS_REVOKED = "REVOKED";

function datasetIdFromSyncCode(syncCode: string): string {
  return createHash("sha256")
    .update(`medminder|${syncCode.trim()}`)
    .digest("hex")
    .slice(0, 40);
}

function requireUid(context: functions.https.CallableContext): string {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Anonymous auth required");
  }
  return uid;
}

function requireSyncCode(data: unknown): string {
  const syncCode = String((data as { syncCode?: unknown } | null)?.syncCode ?? "").trim();
  if (syncCode.length < 8) {
    throw new functions.https.HttpsError("invalid-argument", "syncCode is too short");
  }
  return syncCode;
}

function requireDatasetId(data: unknown): string {
  const datasetId = String((data as { datasetId?: unknown } | null)?.datasetId ?? "").trim();
  if (!datasetId) {
    throw new functions.https.HttpsError("invalid-argument", "datasetId is required");
  }
  return datasetId;
}

export const joinDataset = functions.https.onCall(async (data, context) => {
  const uid = requireUid(context);
  const syncCode = requireSyncCode(data);
  const firestore = admin.firestore();
  const datasetId = datasetIdFromSyncCode(syncCode);
  const datasetRef = firestore.collection(DATASETS_COLLECTION).doc(datasetId);
  const appOwnerRef = firestore.collection(APP_OWNERS_COLLECTION).doc(uid);
  let previousDatasetId: string | null = null;

  await firestore.runTransaction(async tx => {
    const [datasetSnapshot, ownerSnapshot] = await Promise.all([tx.get(datasetRef), tx.get(appOwnerRef)]);

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
        createdAt: datasetSnapshot.exists
          ? datasetSnapshot.get("createdAt") ?? admin.firestore.FieldValue.serverTimestamp()
          : admin.firestore.FieldValue.serverTimestamp(),
        activatedAt: admin.firestore.FieldValue.serverTimestamp(),
        revokedAt: null,
      },
      { merge: true }
    );

    const activeDatasetId = ownerSnapshot.get("activeDatasetId");
    if (typeof activeDatasetId === "string" && activeDatasetId && activeDatasetId !== datasetId) {
      previousDatasetId = activeDatasetId;
    }

    tx.set(
      appOwnerRef,
      {
        activeDatasetId: datasetId,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    if (previousDatasetId) {
      const previousDatasetRef = firestore.collection(DATASETS_COLLECTION).doc(previousDatasetId);
      tx.set(
        previousDatasetRef,
        {
          status: STATUS_REVOKED,
          revokedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  });

  if (previousDatasetId) {
    const previousDatasetRef = firestore.collection(DATASETS_COLLECTION).doc(previousDatasetId);
    try {
      await firestore.recursiveDelete(previousDatasetRef);
    } catch (error) {
      functions.logger.warn("Failed to delete previous dataset", {
        ownerUid: uid,
        previousDatasetId,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  return { datasetId };
});

export const loginWebWithSyncCode = functions.https.onCall(async (data, context) => {
  const uid = requireUid(context);
  const syncCode = requireSyncCode(data);
  const datasetId = datasetIdFromSyncCode(syncCode);
  const datasetRef = admin.firestore().collection(DATASETS_COLLECTION).doc(datasetId);
  const datasetSnapshot = await datasetRef.get();

  if (!datasetSnapshot.exists) {
    throw new functions.https.HttpsError("not-found", "dataset not found");
  }

  const status = datasetSnapshot.get("status");
  if (status !== STATUS_ACTIVE) {
    throw new functions.https.HttpsError("permission-denied", "dataset is not active");
  }

  const ownerUid = datasetSnapshot.get("ownerUid");
  if (typeof ownerUid !== "string" || ownerUid.length === 0) {
    throw new functions.https.HttpsError("failed-precondition", "dataset owner is invalid");
  }

  await datasetRef.collection("webMembers").doc(uid).set(
    {
      joinedAt: admin.firestore.FieldValue.serverTimestamp(),
      lastSeenAt: admin.firestore.FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  return { datasetId };
});

export const logoutWebSession = functions.https.onCall(async (data, context) => {
  const uid = requireUid(context);
  const datasetId = requireDatasetId(data);
  await admin
    .firestore()
    .collection(DATASETS_COLLECTION)
    .doc(datasetId)
    .collection("webMembers")
    .doc(uid)
    .delete();

  return { ok: true };
});

export const revokeDataset = functions.https.onCall(async (data, context) => {
  const uid = requireUid(context);
  const datasetId = requireDatasetId(data);
  const firestore = admin.firestore();
  const datasetRef = firestore.collection(DATASETS_COLLECTION).doc(datasetId);
  const appOwnerRef = firestore.collection(APP_OWNERS_COLLECTION).doc(uid);

  await firestore.runTransaction(async tx => {
    const [datasetSnapshot, ownerSnapshot] = await Promise.all([tx.get(datasetRef), tx.get(appOwnerRef)]);
    if (!datasetSnapshot.exists) {
      throw new functions.https.HttpsError("not-found", "dataset not found");
    }

    const ownerUid = datasetSnapshot.get("ownerUid");
    if (typeof ownerUid !== "string" || ownerUid !== uid) {
      throw new functions.https.HttpsError("permission-denied", "user is not the dataset owner");
    }

    tx.set(
      datasetRef,
      {
        status: STATUS_REVOKED,
        revokedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    const activeDatasetId = ownerSnapshot.get("activeDatasetId");
    if (activeDatasetId === datasetId) {
      tx.set(
        appOwnerRef,
        {
          activeDatasetId: null,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
  });

  try {
    await firestore.recursiveDelete(datasetRef);
  } catch (error) {
    functions.logger.warn("Failed to delete revoked dataset", {
      ownerUid: uid,
      datasetId,
      error: error instanceof Error ? error.message : String(error),
    });
  }

  return { revoked: true };
});
