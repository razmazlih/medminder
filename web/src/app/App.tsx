import { useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';

import { AppLayout } from '@/components/AppLayout';
import { defaultSettings, getOrCreateSettings } from '@/data/localStore';
import { db } from '@/db/appDb';
import { ConnectPage } from '@/pages/ConnectPage';
import { HiddenLogPage } from '@/pages/HiddenLogPage';
import { HistoryPage } from '@/pages/HistoryPage';
import { IntakesPage } from '@/pages/IntakesPage';
import { MedicationsPage } from '@/pages/MedicationsPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { useAppStore } from '@/state/useAppStore';
import { syncEngine } from '@/sync/instance';

const FOREGROUND_SYNC_INTERVAL_MS = 20 * 1000;

function RouteGuard() {
  const settings = useLiveQuery(() => db.settings.get('singleton'), []);
  const location = useLocation();

  if (settings === undefined) {
    return null;
  }

  const resolvedSettings = settings ?? defaultSettings;
  const connected = Boolean(resolvedSettings.datasetId && resolvedSettings.wrappedSyncSecret);

  if (!connected && location.pathname !== '/connect') {
    return <Navigate to="/connect" replace />;
  }

  if (connected && location.pathname === '/connect') {
    return <Navigate to="/intakes" replace />;
  }

  return null;
}

function MessageBar() {
  const { message, setMessage } = useAppStore();

  useEffect(() => {
    if (!message) {
      return;
    }

    const timeout = window.setTimeout(() => setMessage(null), 2400);
    return () => window.clearTimeout(timeout);
  }, [message, setMessage]);

  if (!message) {
    return null;
  }

  return (
    <div className="toast" role="status" aria-live="polite">
      {message}
    </div>
  );
}

async function syncIfEnabled(reason: string): Promise<void> {
  const settings = await getOrCreateSettings();
  const canAutoSync =
    settings.cloudBackupEnabled &&
    !settings.localOnlyMode &&
    Boolean(settings.datasetId && settings.wrappedSyncSecret);
  if (!canAutoSync) {
    return;
  }

  await syncEngine.syncNow(reason);
}

export default function App() {
  useEffect(() => {
    void getOrCreateSettings();
  }, []);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== 'visible') {
        return;
      }
      void syncEngine.syncNow('re_entry');
    };

    document.addEventListener('visibilitychange', onVisible);
    void syncEngine.syncNow('app_open');

    const intervalId = window.setInterval(() => {
      if (document.visibilityState !== 'visible') {
        return;
      }

      void (async () => {
        try {
          await syncIfEnabled('foreground_poll');
        } catch {
          // Errors are handled by sync state and route guard side effects.
        }
      })();
    }, FOREGROUND_SYNC_INTERVAL_MS);

    return () => {
      document.removeEventListener('visibilitychange', onVisible);
      window.clearInterval(intervalId);
    };
  }, []);

  return (
    <>
      <RouteGuard />
      <Routes>
        <Route path="/connect" element={<ConnectPage />} />
        <Route path="/*" element={<AppLayout />}>
          <Route index element={<Navigate to="/intakes" replace />} />
          <Route path="medications" element={<MedicationsPage />} />
          <Route path="intakes" element={<IntakesPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route path="hidden-log" element={<HiddenLogPage />} />
          <Route path="settings" element={<SettingsPage />} />
        </Route>
      </Routes>
      <MessageBar />
    </>
  );
}
