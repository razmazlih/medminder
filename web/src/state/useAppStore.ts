import { create } from 'zustand';

interface AppUiState {
  message: string | null;
  setMessage: (message: string | null) => void;
  isSyncRunning: boolean;
  setSyncRunning: (value: boolean) => void;
}

export const useAppStore = create<AppUiState>((set) => ({
  message: null,
  setMessage: (message) => set({ message }),
  isSyncRunning: false,
  setSyncRunning: (isSyncRunning) => set({ isSyncRunning }),
}));
