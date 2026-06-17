import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  getToken,
  notifyUnauthorized,
  registerUnauthorizedHandler,
  setToken,
} from './tokenStore';

afterEach(() => {
  // Reset module-level state so tests don't leak into one another.
  setToken(null);
  registerUnauthorizedHandler(null);
});

describe('tokenStore', () => {
  it('round-trips a token via setToken/getToken', () => {
    expect(getToken()).toBeNull();

    setToken('abc.def.ghi');
    expect(getToken()).toBe('abc.def.ghi');
  });

  it('clears the token when set to null', () => {
    setToken('abc.def.ghi');
    setToken(null);
    expect(getToken()).toBeNull();
  });

  it('invokes the registered unauthorized handler on notify', () => {
    const handler = vi.fn();
    registerUnauthorizedHandler(handler);

    notifyUnauthorized();

    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('invokes the latest registered handler, not an earlier one', () => {
    const first = vi.fn();
    const second = vi.fn();
    registerUnauthorizedHandler(first);
    registerUnauthorizedHandler(second);

    notifyUnauthorized();

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });

  it('is a no-op when no handler is registered', () => {
    registerUnauthorizedHandler(null);
    expect(() => notifyUnauthorized()).not.toThrow();
  });
});
