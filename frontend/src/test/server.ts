import { setupServer } from 'msw/node';

// A single shared MSW server. Tests register per-case handlers with
// `server.use(...)`; `resetHandlers` after each test wipes those overrides.
export const server = setupServer();
