import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { ConnectPage } from '@/pages/ConnectPage';

const navigateMock = vi.fn();
const connectMutationState = vi.hoisted(() => ({
  isPending: false,
  error: null as unknown,
  mutateAsync: vi.fn(),
}));

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

vi.mock('@/hooks/useSyncMutations', () => ({
  useConnectMutation: () => connectMutationState,
}));

describe('ConnectPage', () => {
  beforeEach(() => {
    connectMutationState.isPending = false;
    connectMutationState.error = null;
    connectMutationState.mutateAsync.mockReset().mockResolvedValue('dataset-1');
    navigateMock.mockReset();
  });

  it('validates sync code before submit', async () => {
    const user = userEvent.setup();
    render(<ConnectPage />);

    await user.type(screen.getByLabelText('Sync Code'), 'short');
    await user.click(screen.getByRole('button', { name: '🔗 התחברות' }));

    expect(connectMutationState.mutateAsync).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('⚠️ יש להזין Sync Code תקין (לפחות 8 תווים).');
  });

  it('submits valid sync code and navigates to intakes page', async () => {
    const user = userEvent.setup();
    render(<ConnectPage />);

    await user.type(screen.getByLabelText('Sync Code'), 'VALID-SYNC-CODE-123');
    await user.click(screen.getByRole('button', { name: '🔗 התחברות' }));

    expect(connectMutationState.mutateAsync).toHaveBeenCalledWith({ syncCode: 'VALID-SYNC-CODE-123' });
    expect(navigateMock).toHaveBeenCalledWith('/intakes');
  });

  it('shows mutation error text and loading state from hook', () => {
    connectMutationState.isPending = true;
    connectMutationState.error = new Error('network failure');

    render(<ConnectPage />);

    expect(screen.getByRole('alert')).toHaveTextContent('❌ network failure');
    expect(screen.getByRole('button', { name: '⏳ מתחבר...' })).toBeDisabled();
  });
});
