import '@testing-library/jest-dom/vitest';
import 'fake-indexeddb/auto';
import { afterEach, beforeEach } from 'vitest';
import { cleanup } from '@testing-library/react';

import { clearAllDataForTests } from '@/data/localStore';

const viteEnv = import.meta.env as unknown as Record<string, string>;
viteEnv.VITE_USE_FAKE_CLOUD = '1';
viteEnv.VITE_FIREBASE_PROJECT_ID = 'medical-fe79c';
viteEnv.VITE_FIREBASE_API_KEY = 'demo-key';
viteEnv.VITE_FIREBASE_APP_ID = 'demo-app-id';
viteEnv.VITE_FIREBASE_AUTH_DOMAIN = 'medical-fe79c.firebaseapp.com';
viteEnv.VITE_FIREBASE_STORAGE_BUCKET = 'medical-fe79c.firebasestorage.app';
viteEnv.VITE_FIREBASE_MESSAGING_SENDER_ID = '282849586912';
viteEnv.VITE_FIREBASE_FUNCTIONS_REGION = 'us-central1';

beforeEach(async () => {
  await clearAllDataForTests();
  window.__fakeCloudDebug?.reset();
});

afterEach(() => {
  cleanup();
});
