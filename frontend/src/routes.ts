// Named route constants — avoid magic strings scattered across components.
export const ROUTES = {
  root: '/',
  login: '/login',
  dashboard: '/dashboard',
  collections: '/collections',
  collectionSelected: (id: string | number) => `/collections?selected=${id}`,
  prompt: (id: string | number) => `/prompts/${id}`,
  promptPattern: '/prompts/:id',
} as const;
