import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { setLocalOnlyMode } from '@/data/actions';
import { useAppSettings, useCloudDeletionNotices } from '@/hooks/useLocalData';
import {
  useConnectMutation,
  useLogoutMutation,
  useSyncNowMutation,
  useSyncStatusQuery,
} from '@/hooks/useSyncMutations';
import { useAppStore } from '@/state/useAppStore';

function syncStateLabel(state: string): { label: string; dotClass: string; description: string } {
  switch (state) {
    case 'LOCAL_ONLY':
      return { label: 'Local-only', dotClass: 'local', description: 'הנתונים מאוחסנים רק על מכשיר זה' };
    case 'SYNCING':
      return { label: 'מסנכרן...', dotClass: 'syncing', description: 'מעלה ומוריד נתונים מהענן' };
    case 'ERROR':
      return { label: 'שגיאה', dotClass: 'error', description: 'הסנכרון נכשל, נסו שנית' };
    case 'IDLE':
    default:
      return { label: 'מוכן', dotClass: 'synced', description: 'הנתונים מסונכרנים עם הענן' };
  }
}

export function SettingsPage() {
  const settings = useAppSettings();
  const syncNowMutation = useSyncNowMutation();
  const connectMutation = useConnectMutation();
  const logoutMutation = useLogoutMutation();
  const syncStatus = useSyncStatusQuery();
  const cloudDeletionNotices = useCloudDeletionNotices();
  const { setMessage } = useAppStore();
  const navigate = useNavigate();

  const [reconnectCode, setReconnectCode] = useState('');

  async function onToggleLocalOnly(enabled: boolean) {
    await setLocalOnlyMode(enabled);
    setMessage(enabled ? '📱 מצב Local-only הופעל' : '☁️ גיבוי לענן הופעל');
  }

  async function onReconnect() {
    if (!reconnectCode.trim()) {
      setMessage('⚠️ יש להזין Sync Code');
      return;
    }

    try {
      await connectMutation.mutateAsync({
        syncCode: reconnectCode.trim(),
      });
      setReconnectCode('');
      setMessage('✅ התחברתם מחדש בהצלחה');
    } catch {
      setMessage('❌ ההתחברות נכשלה, בדקו את הקוד');
    }
  }

  async function onLogoutFromWeb() {
    await logoutMutation.mutateAsync();
    navigate('/connect');
  }

  const snapshot = syncStatus.data;
  const syncConfigured = Boolean(settings.datasetId && settings.wrappedSyncSecret && settings.localWrapKeyBase64);
  const canSyncNow = !settings.localOnlyMode && syncConfigured;
  const stateInfo = syncStateLabel(snapshot?.settings.syncState ?? settings.syncState);
  const outboxCount = snapshot?.outboxCount ?? 0;

  return (
    <div className="stack-lg">
      <section className="panel">
        <h1 className="page-title">
          <span aria-hidden="true">🔄</span> מצב סנכרון
        </h1>

        <div className="sync-status-card">
          <div className="sync-status-row">
            <span className={`status-dot ${stateInfo.dotClass}`} />
            <div>
              <span className="status-label">{stateInfo.label}</span>
              <p className="field-help">{stateInfo.description}</p>
            </div>
          </div>

          <div className="sync-info-grid">
            <div className="sync-info-item">
              <span className="sync-info-value">{outboxCount}</span>
              <span className="sync-info-label">בהמתנה</span>
            </div>
            <div className="sync-info-item">
              <span className="sync-info-value">
                {snapshot?.settings.lastSyncAtEpochMs
                  ? new Date(snapshot.settings.lastSyncAtEpochMs).toLocaleTimeString('he-IL', { hour: '2-digit', minute: '2-digit' })
                  : '—'}
              </span>
              <span className="sync-info-label">סנכרון אחרון</span>
            </div>
          </div>
        </div>

        <button
          type="button"
          onClick={() => syncNowMutation.mutate()}
          disabled={syncNowMutation.isPending || !canSyncNow}
          data-testid="sync-now-button"
          className="btn-block mt-sm"
        >
          {syncNowMutation.isPending ? '⏳ מסנכרן...' : '🔄 סנכרון עכשיו'}
        </button>

        {!canSyncNow ? (
          <p className="field-help">
            💡 כדי לסנכרן, בטלו Local-only והתחברו עם Sync Code מהאפליקציה.
          </p>
        ) : null}
      </section>

      <section className="panel">
        <h2 className="section-title">☁️ אחסון נתונים</h2>

        <div className="toggle-card">
          <div className="toggle-card-content">
            <strong className="toggle-card-title">{settings.localOnlyMode ? '📱 מצב Local-only' : '☁️ גיבוי לענן'}</strong>
            <p className="field-help">
              {settings.localOnlyMode
                ? 'הנתונים נשמרים רק על המכשיר – לא נשלח שום דבר לענן'
                : 'הנתונים מגובים לענן באופן אוטומטי ומסונכרנים בין מכשירים'}
            </p>
          </div>
          <button
            type="button"
            className={`switch-toggle ${settings.localOnlyMode ? 'is-on' : ''}`}
            role="switch"
            aria-checked={settings.localOnlyMode}
            onClick={() => void onToggleLocalOnly(!settings.localOnlyMode)}
            aria-label="הפעלת מצב Local-only"
          >
            <span className="switch-thumb" aria-hidden="true" />
          </button>
        </div>
      </section>

      <section className="panel">
        <h2 className="section-title">🔑 התחברות עם קוד מהאפליקציה</h2>

        <div className="stack">
          <div className="form-group">
            <label htmlFor="reconnectCode">Sync Code מהאפליקציה</label>
            <input
              id="reconnectCode"
              value={reconnectCode}
              onChange={(event) => setReconnectCode(event.target.value)}
              placeholder="הדביקו Sync Code כאן..."
              autoComplete="off"
            />
          </div>
          <button
            type="button"
            onClick={onReconnect}
            disabled={connectMutation.isPending || !reconnectCode.trim()}
            className="secondary btn-block"
          >
            {connectMutation.isPending ? '⏳ מתחבר...' : '🔗 התחברות מחדש'}
          </button>

          <p className="field-help">
            Sync Code מונפק מתוך האפליקציה בלבד.
          </p>

          <hr className="separator" />

          <button
            type="button"
            onClick={onLogoutFromWeb}
            disabled={logoutMutation.isPending}
            className="btn-block"
          >
            {logoutMutation.isPending ? '⏳ מתנתק...' : '🚪 התנתקות מהאתר'}
          </button>
        </div>
      </section>

      <section className="panel">
        <h2 className="section-title">🧰 כלים מתקדמים</h2>
        <p className="section-subtitle">גישה לרשומות פעילות פנימיות ולכלי בדיקה מתקדמים.</p>
        <button type="button" className="secondary btn-block" onClick={() => navigate('/hidden-log')}>
          🔍 יומן פנימי
        </button>
      </section>

      {cloudDeletionNotices.length > 0 ? (
        <section className="panel warning-panel" data-testid="cloud-deletion-settings">
          <h2 className="section-title">⚠️ התראות מחיקה ({cloudDeletionNotices.length})</h2>
          <ul className="plain-list">
            {cloudDeletionNotices.slice(0, 5).map((notice) => (
              <li key={notice.id}>נמחק: {notice.recordTypeLabel} / {notice.medicationName}</li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
