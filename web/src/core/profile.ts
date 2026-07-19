import { OWNER_PROFILE_ID } from '@/domain/models';

export async function getActiveProfileId(): Promise<string> {
  return OWNER_PROFILE_ID;
}
