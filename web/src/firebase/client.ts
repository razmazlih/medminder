import { initializeApp, getApp, getApps, type FirebaseApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';
import { getFunctions } from 'firebase/functions';

import { assertFirebaseConfig, readFirebaseConfig } from '@/firebase/config';

let cachedApp: FirebaseApp | null = null;

function getOrInitFirebaseApp(): FirebaseApp {
  if (cachedApp) {
    return cachedApp;
  }

  const config = readFirebaseConfig();
  assertFirebaseConfig(config);

  cachedApp = getApps().length > 0
    ? getApp()
    : initializeApp({
      apiKey: config.apiKey,
      authDomain: config.authDomain,
      projectId: config.projectId,
      storageBucket: config.storageBucket,
      messagingSenderId: config.messagingSenderId,
      appId: config.appId,
    });

  return cachedApp;
}

export function getFirebaseServices() {
  const app = getOrInitFirebaseApp();
  const config = readFirebaseConfig();
  return {
    app,
    auth: getAuth(app),
    firestore: getFirestore(app),
    functions: getFunctions(app, config.functionsRegion),
  };
}
