import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

interface WorkspaceContextValue {
  // Bumped whenever workspace data changes (collections created/deleted, and
  // later prompts/versions), so aggregate views like the dashboard can refetch
  // without prop drilling or a shared data store.
  revision: number;
  notifyChanged: () => void;
}

const WorkspaceContext = createContext<WorkspaceContextValue | null>(null);

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [revision, setRevision] = useState(0);
  const notifyChanged = useCallback(() => setRevision((current) => current + 1), []);

  const value = useMemo<WorkspaceContextValue>(
    () => ({ revision, notifyChanged }),
    [revision, notifyChanged],
  );

  return <WorkspaceContext value={value}>{children}</WorkspaceContext>;
}

// Co-located with its provider (standard Context pattern); the react-refresh
// rule about mixed exports doesn't apply to this shared module.
// eslint-disable-next-line react-refresh/only-export-components
export function useWorkspace(): WorkspaceContextValue {
  const ctx = useContext(WorkspaceContext);
  if (ctx === null) {
    throw new Error('useWorkspace must be used within a WorkspaceProvider');
  }
  return ctx;
}
