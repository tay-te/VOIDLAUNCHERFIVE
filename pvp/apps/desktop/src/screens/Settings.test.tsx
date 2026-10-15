// @vitest-environment jsdom
/**
 * The Settings panel, rendered against the mock backend: the fields this release added
 * exist, commit when they should, and the developer-only client JAR is not a field.
 */

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { __resetMock, __setSpeed } from '../mocks/tauri';
import { useLoadouts } from '../stores/loadouts';
import { useSession } from '../stores/session';
import { useUi } from '../stores/ui';
import { SettingsPanel } from './Settings';

beforeEach(async () => {
  __resetMock();
  __setSpeed(0);
  useSession.setState({ account: null, accounts: [], deviceCode: null, error: null });
  await useSession.getState().hydrate();
  await useLoadouts.getState().hydrate();
  useUi.setState({ settingsOpen: true });
});

afterEach(() => cleanup());

describe('Settings', () => {
  it('shows the new launch fields and no user-facing client JAR field', () => {
    render(<SettingsPanel />);
    expect(screen.getByLabelText('JVM arguments')).toBeTruthy();
    expect(screen.getByLabelText('Window width')).toBeTruthy();
    expect(screen.getByLabelText('Window height')).toBeTruthy();
    expect(screen.getByLabelText('Start fullscreen')).toBeTruthy();
    expect(screen.getByLabelText('Game directory')).toBeTruthy();
    expect(screen.getByLabelText('Discord status')).toBeTruthy();
    expect(screen.getByLabelText('Update channel')).toBeTruthy();
    expect(screen.queryByLabelText('Client JAR')).toBeNull();
  });

  it('commits JVM arguments on blur, not on every keystroke', async () => {
    render(<SettingsPanel />);
    const field = screen.getByLabelText('JVM arguments') as HTMLInputElement;
    fireEvent.change(field, { target: { value: '-XX:+UseG1GC' } });
    expect(useLoadouts.getState().settings?.jvm_args).toBe('');
    fireEvent.blur(field);
    await waitFor(() => expect(useLoadouts.getState().settings?.jvm_args).toBe('-XX:+UseG1GC'));
  });

  it('lists every account with a way to switch and to sign out', async () => {
    await useSession.getState().loginOffline('Alpha');
    await useSession.getState().loginOffline('Bravo');
    render(<SettingsPanel />);
    expect(screen.getByText('Alpha')).toBeTruthy();
    expect(screen.getByText('Bravo')).toBeTruthy();
    // Bravo is active; Alpha's row offers "Use" (the last "Use" is the offline form's).
    const uses = screen.getAllByRole('button', { name: 'Use' });
    expect(uses).toHaveLength(2);
    fireEvent.click(uses[0]!);
    await waitFor(() => expect(useSession.getState().account?.name).toBe('Alpha'));
    expect(screen.getAllByRole('button', { name: 'Sign out' })).toHaveLength(2);
  });
});
