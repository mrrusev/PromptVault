import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './server';

// MSW lifecycle. `onUnhandledRequest: 'error'` makes any un-mocked /api call fail
// loudly instead of hitting a real (possibly running) backend on :8080.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
