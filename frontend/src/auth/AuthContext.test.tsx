import { act, render, renderHook, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getToken, setToken } from '../api/tokenStore';
import type { UserDto } from '../api/types';
import { AuthProvider, useAuth } from './AuthContext';

const USER: UserDto = { id: 1, username: 'alice' };

function wrapper({ children }: { children: React.ReactNode }) {
  return (
    <MemoryRouter>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  );
}

afterEach(() => {
  setToken(null);
});

describe('useAuth outside provider', () => {
  it('throws a clear error', () => {
    expect(() => renderHook(() => useAuth())).toThrow(
      'useAuth must be used within an AuthProvider',
    );
  });
});

describe('login', () => {
  it('sets isAuthenticated, exposes the user, and pushes the token into tokenStore', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isAuthenticated).toBe(false);
    expect(getToken()).toBeNull();

    act(() => result.current.login('jwt-abc', USER));

    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user).toEqual(USER);
    expect(result.current.token).toBe('jwt-abc');
    // The plain fetch client reads the token from here on subsequent calls.
    expect(getToken()).toBe('jwt-abc');
  });
});

describe('logout', () => {
  it('clears auth state and the token store', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    act(() => result.current.login('jwt-abc', USER));

    act(() => result.current.logout());

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.token).toBeNull();
    expect(getToken()).toBeNull();
  });
});

describe('in-memory only', () => {
  it('never writes the token to localStorage, sessionStorage, or cookies', async () => {
    function LoginOnce() {
      const { login } = useAuth();
      return (
        <button type="button" onClick={() => login('secret-jwt', USER)}>
          do-login
        </button>
      );
    }

    // Spy on every persistence sink. The token is in-memory by design, so none
    // of these must ever be written. (Spying is robust against jsdom's flaky
    // file-backed Storage under Node; it asserts the real behavior directly.)
    const localSetItem = vi.spyOn(Storage.prototype, 'setItem');
    const cookieSetter = vi.spyOn(document, 'cookie', 'set');

    render(<LoginOnce />, { wrapper });
    await userEvent.click(screen.getByRole('button', { name: 'do-login' }));

    expect(localSetItem).not.toHaveBeenCalled();
    expect(cookieSetter).not.toHaveBeenCalled();
    expect(document.cookie).not.toContain('secret-jwt');

    localSetItem.mockRestore();
    cookieSetter.mockRestore();
  });
});
