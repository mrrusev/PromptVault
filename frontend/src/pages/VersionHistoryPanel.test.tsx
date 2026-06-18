import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { PromptVersion } from '../api/types';
import { VersionHistoryPanel } from './VersionHistoryPanel';

function version(id: number, versionNumber: number): PromptVersion {
  return {
    id,
    promptId: 7,
    versionNumber,
    content: `content ${versionNumber}`,
    createdAt: '2026-06-18T10:00:00Z',
  };
}

describe('VersionHistoryPanel', () => {
  it('renders rows newest-first', () => {
    render(
      <VersionHistoryPanel
        versions={[version(1, 1), version(2, 2), version(3, 3)]}
        restoringVersion={null}
        onRestore={() => {}}
      />,
    );

    const rows = screen.getAllByRole('listitem');
    expect(rows).toHaveLength(3);
    expect(within(rows[0]).getByText('Version 3')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Version 2')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Version 1')).toBeInTheDocument();
  });

  it('calls onRestore with the row version number', async () => {
    const onRestore = vi.fn();
    render(
      <VersionHistoryPanel
        versions={[version(1, 1), version(2, 2)]}
        restoringVersion={null}
        onRestore={onRestore}
      />,
    );

    const newestRow = screen.getAllByRole('listitem')[0];
    await userEvent.click(within(newestRow).getByRole('button', { name: /restore/i }));

    expect(onRestore).toHaveBeenCalledExactlyOnceWith(2);
  });

  it('shows an empty state for no versions', () => {
    render(<VersionHistoryPanel versions={[]} restoringVersion={null} onRestore={() => {}} />);

    expect(screen.getByText(/no versions yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
  });

  it('disables only the row currently being restored', () => {
    render(
      <VersionHistoryPanel
        versions={[version(1, 1), version(2, 2)]}
        restoringVersion={2}
        onRestore={() => {}}
      />,
    );

    const rows = screen.getAllByRole('listitem');
    // Newest-first: row 0 is version 2 (restoring), row 1 is version 1.
    expect(within(rows[0]).getByRole('button')).toBeDisabled();
    expect(within(rows[1]).getByRole('button')).toBeEnabled();
  });
});
