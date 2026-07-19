import { getCloudAdapter } from '@/sync/cloudAdapter';
import { SyncEngine } from '@/sync/syncEngine';

export const syncEngine = new SyncEngine(getCloudAdapter());
