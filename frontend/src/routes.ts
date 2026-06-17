// Named route constants — avoid magic strings scattered across components.
export const ROUTES = {
  root: '/',
  login: '/login',
  dashboard: '/dashboard',
  collections: '/collections',
  prompt: (id: string | number) => `/prompts/${id}`,
  promptPattern: '/prompts/:id',
} as const;
