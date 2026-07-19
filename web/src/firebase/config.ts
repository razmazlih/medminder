export interface FirebaseWebConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
  functionsRegion: string;
}

function env(name: string): string {
  const value = import.meta.env[name];
  return typeof value === 'string' ? value : '';
}

export function isFakeCloudEnabled(): boolean {
  return import.meta.env.VITE_USE_FAKE_CLOUD === '1';
}

export function readFirebaseConfig(): FirebaseWebConfig {
  return {
    apiKey: env('VITE_FIREBASE_API_KEY'),
    authDomain: env('VITE_FIREBASE_AUTH_DOMAIN'),
    projectId: env('VITE_FIREBASE_PROJECT_ID'),
    storageBucket: env('VITE_FIREBASE_STORAGE_BUCKET'),
    messagingSenderId: env('VITE_FIREBASE_MESSAGING_SENDER_ID'),
    appId: env('VITE_FIREBASE_APP_ID'),
    functionsRegion: env('VITE_FIREBASE_FUNCTIONS_REGION') || 'us-central1',
  };
}

export function assertFirebaseConfig(config: FirebaseWebConfig): void {
  if (isFakeCloudEnabled()) {
    return;
  }

  const missing = Object.entries(config)
    .filter(([, value]) => !value)
    .map(([key]) => key);

  if (missing.length > 0) {
    throw new Error(`Missing Firebase env vars: ${missing.join(', ')}`);
  }
}
