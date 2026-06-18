import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { login as loginRequest, register as registerRequest } from '../api/endpoints';
import { RequestError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ROUTES } from '../routes';

const MIN_PASSWORD_LENGTH = 8;

type Mode = 'login' | 'register';

// Combined login/register form. A mode toggle flips the copy and the submit
// behavior; registering issues a JWT immediately, so its success path is
// identical to login (capture token, then navigate to the dashboard).
export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode>('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const isRegister = mode === 'register';

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    setError(null);
    setPending(true);
    try {
      const submit = isRegister ? registerRequest : loginRequest;
      const res = await submit({ username, password });
      login(res.token, res.user);
      navigate(ROUTES.dashboard, { replace: true });
    } catch (err) {
      if (err instanceof RequestError) {
        setError(err.body?.error ?? 'Login failed. Please try again.');
      } else {
        setError('Unable to reach the server. Please try again.');
      }
    } finally {
      setPending(false);
    }
  }

  function toggleMode() {
    setMode((current) => (current === 'login' ? 'register' : 'login'));
    setError(null);
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-gray-50 p-4">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm space-y-4 rounded-lg bg-white p-6 shadow"
      >
        <h1 className="text-xl font-semibold text-gray-900">
          {isRegister ? 'Create your PromptVault account' : 'Sign in to PromptVault'}
        </h1>

        {error && (
          <p role="alert" className="rounded bg-red-50 p-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <div className="space-y-1">
          <label htmlFor="username" className="block text-sm font-medium text-gray-700">
            Username
          </label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="w-full rounded border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
        </div>

        <div className="space-y-1">
          <label htmlFor="password" className="block text-sm font-medium text-gray-700">
            Password
          </label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete={isRegister ? 'new-password' : 'current-password'}
            required
            minLength={isRegister ? MIN_PASSWORD_LENGTH : undefined}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="w-full rounded border border-gray-300 px-3 py-2 text-gray-900 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
          {isRegister && (
            <p className="text-xs text-gray-500">Password must be at least 8 characters.</p>
          )}
        </div>

        <button
          type="submit"
          disabled={pending}
          className="w-full rounded bg-blue-600 px-4 py-2 font-medium text-white hover:bg-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none disabled:opacity-60"
        >
          {isRegister
            ? pending
              ? 'Creating account…'
              : 'Create account'
            : pending
              ? 'Signing in…'
              : 'Sign in'}
        </button>

        <button
          type="button"
          onClick={toggleMode}
          className="w-full rounded px-4 py-2 text-sm font-medium text-blue-600 hover:text-blue-700 focus:ring-2 focus:ring-blue-500 focus:outline-none"
        >
          {isRegister ? 'Already have an account? Sign in' : 'Need an account? Create one'}
        </button>
      </form>
    </main>
  );
}
