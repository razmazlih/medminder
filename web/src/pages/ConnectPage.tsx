import { useEffect, useMemo, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';

import { syncCodeSchema } from '@/domain/schemas';
import { useConnectMutation } from '@/hooks/useSyncMutations';

export function ConnectPage() {
  const [syncCode, setSyncCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const connectMutation = useConnectMutation();
  const inputRef = useRef<HTMLInputElement>(null);

  const loading = connectMutation.isPending;
  const mutationError = useMemo(() => {
    if (!connectMutation.error) {
      return null;
    }
    return connectMutation.error instanceof Error ? connectMutation.error.message : 'Connection failed';
  }, [connectMutation.error]);
  const errorText = error ?? (mutationError ? `❌ ${mutationError}` : null);
  const describedBy = errorText ? 'syncCodeHelp syncCodeError' : 'syncCodeHelp';

  useEffect(() => {
    setTimeout(() => inputRef.current?.focus(), 300);
  }, []);

  async function onConnect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const parsed = syncCodeSchema.safeParse(syncCode);
    if (!parsed.success) {
      setError('⚠️ יש להזין Sync Code תקין (לפחות 8 תווים).');
      return;
    }

    setError(null);
    await connectMutation.mutateAsync({
      syncCode: parsed.data,
    });
    navigate('/intakes');
  }

  return (
    <div className="connect-page">
      <div className="connect-logo" aria-hidden="true">💊</div>

      <section className="panel connect-panel">
        <h1 className="page-title">ברוכים הבאים ל-MedMinder</h1>
        <p className="section-subtitle">
          כדי להתחבר לאתר, יש להנפיק Sync Code מתוך האפליקציה בטלפון ולהדביק אותו כאן.
        </p>

        <form onSubmit={onConnect} className="stack">
          <div className="form-group">
            <label htmlFor="syncCode">Sync Code</label>
            <input
              ref={inputRef}
              id="syncCode"
              name="syncCode"
              autoComplete="off"
              value={syncCode}
              onChange={(event) => {
                setSyncCode(event.target.value);
                setError(null);
              }}
              placeholder="הדביקו את הקוד מהאפליקציה..."
              aria-describedby={describedBy}
              aria-invalid={errorText ? true : undefined}
              required
            />
            <p id="syncCodeHelp" className="field-help">
              הקוד מונפק מתוך האפליקציה בלבד.
            </p>
          </div>

          {errorText ? (
            <p id="syncCodeError" className="error-text" role="alert" aria-live="assertive">
              {errorText}
            </p>
          ) : null}

          <button type="submit" disabled={loading || !syncCode.trim()} data-testid="connect-button" className="btn-block">
            {loading ? '⏳ מתחבר...' : '🔗 התחברות'}
          </button>
        </form>
      </section>
    </div>
  );
}
