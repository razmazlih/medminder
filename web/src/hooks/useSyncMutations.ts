import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getSyncSnapshot } from '@/data/localStore';
import { useAppStore } from '@/state/useAppStore';
import { syncEngine } from '@/sync/instance';

function syncErrorMessage(error: unknown): string {
  const raw = error instanceof Error ? error.message : String(error ?? '');
  const message = raw.toLowerCase();

  if (message.includes('missing firebase env vars')) {
    return 'חסרות הגדרות Firebase בפרויקט. יש להשלים קובץ סביבה.';
  }
  if (message.includes('missing sync credentials')) {
    return 'פרטי הסנכרון חסרים. יש להתחבר מחדש עם Sync Code.';
  }
  if (message.includes('session_revoked')) {
    return 'הקוד הוחלף באפליקציה. נותקת מהאתר אוטומטית.';
  }
  if (message.includes('permission-denied') || message.includes('insufficient permissions')) {
    return 'אין הרשאה לסנכרון. נסה התחברות מחדש עם Sync Code.';
  }
  if (message.includes('not-found') || message.includes('dataset not found')) {
    return 'הקוד לא קיים או בוטל. יש להנפיק קוד חדש מהאפליקציה.';
  }
  if (message.includes('dataset is not active')) {
    return 'הקוד הזה כבר לא פעיל. יש להנפיק קוד חדש מהאפליקציה.';
  }
  if (message.includes('unauthenticated')) {
    return 'האימות פג. נסה שוב; אם הבעיה נמשכת התחבר מחדש.';
  }
  if (message.includes('unavailable') || message.includes('network')) {
    return 'שגיאת רשת בסנכרון. נסה שוב בעוד רגע.';
  }
  if (message.includes('timeout') || message.includes('timed out') || message.includes('deadline-exceeded')) {
    return 'בקשת הסנכרון חרגה מזמן ההמתנה. נסה שוב.';
  }

  return `שגיאת סנכרון: ${raw || 'Unknown error'}`;
}

export function useSyncStatusQuery() {
  return useQuery({
    queryKey: ['sync-status'],
    queryFn: getSyncSnapshot,
    refetchInterval: 2000,
  });
}

export function useConnectMutation() {
  const queryClient = useQueryClient();
  const setMessage = useAppStore((state) => state.setMessage);

  return useMutation({
    mutationFn: async (input: { syncCode: string }) => {
      const datasetId = await syncEngine.connectWithSyncCode(input.syncCode);
      await syncEngine.syncNow('connect');
      return datasetId;
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sync-status'] });
      setMessage('החיבור הושלם בהצלחה');
    },
    onError: (error) => {
      setMessage(syncErrorMessage(error));
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();
  const setMessage = useAppStore((state) => state.setMessage);

  return useMutation({
    mutationFn: async () => {
      await syncEngine.logoutFromWeb();
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sync-status'] });
      setMessage('התנתקת מהאתר בהצלחה');
    },
    onError: (error) => {
      setMessage(syncErrorMessage(error));
    },
  });
}

export function useSyncNowMutation() {
  const queryClient = useQueryClient();
  const setMessage = useAppStore((state) => state.setMessage);

  return useMutation({
    mutationFn: async () => {
      await syncEngine.syncNow('manual');
    },
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ['sync-status'] });
    },
    onError: (error) => {
      setMessage(syncErrorMessage(error));
    },
  });
}
