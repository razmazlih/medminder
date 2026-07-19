import '@fontsource/heebo/400.css';
import '@fontsource/heebo/700.css';
import '@fontsource/rubik/500.css';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import App from '@/app/App';
import { clearAllDataForTests } from '@/data/localStore';
import { db } from '@/db/appDb';
import '@/styles.css';

const queryClient = new QueryClient();

declare global {
  interface Window {
    __appDebug?: {
      cloudDeletionNoticeCount: () => Promise<number>;
      hiddenLogDetails: () => Promise<string[]>;
      medicationNameById: (id: string) => Promise<string | null>;
      clearAllData: () => Promise<void>;
    };
  }
}

if (import.meta.env.VITE_USE_FAKE_CLOUD === '1') {
  window.__appDebug = {
    cloudDeletionNoticeCount: async () => {
      const logs = await db.hiddenIntakeLogs.toArray();
      return logs.filter((log) => (log.operationDetails ?? '').startsWith('cloud_deleted:')).length;
    },
    hiddenLogDetails: async () => {
      const logs = await db.hiddenIntakeLogs.orderBy('createdAtEpochMs').reverse().toArray();
      return logs.map((log) => log.operationDetails ?? '');
    },
    medicationNameById: async (id: string) => {
      const medication = await db.medications.get(id);
      return medication?.name ?? null;
    },
    clearAllData: async () => {
      await clearAllDataForTests();
    },
  };
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>
);
