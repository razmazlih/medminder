import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 4173',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: true,
    timeout: 120000,
    env: {
      VITE_USE_FAKE_CLOUD: '1',
      VITE_FIREBASE_PROJECT_ID: 'medical-fe79c',
      VITE_FIREBASE_API_KEY: 'demo-key',
      VITE_FIREBASE_APP_ID: 'demo-app-id',
      VITE_FIREBASE_AUTH_DOMAIN: 'medical-fe79c.firebaseapp.com',
      VITE_FIREBASE_STORAGE_BUCKET: 'medical-fe79c.firebasestorage.app',
      VITE_FIREBASE_MESSAGING_SENDER_ID: '282849586912',
      VITE_FIREBASE_FUNCTIONS_REGION: 'us-central1',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
