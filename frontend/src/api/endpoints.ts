import { request } from './client';
import type {
  AuthRequest,
  AuthResponse,
  Collection,
  CreateCollectionRequest,
  CreatePromptRequest,
  DashboardResponse,
  Prompt,
  PromptVersion,
  UpdatePromptRequest,
  UserDto,
} from './types';

// --- Auth ---
// Auth calls opt out of the global 401 handler: a 401 here means "invalid
// credentials" and must surface in the form, not trigger logout + redirect.
export function register(payload: AuthRequest): Promise<AuthResponse> {
  return request<AuthResponse>(
    '/auth/register',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    { skipAuthRedirect: true },
  );
}

export function login(payload: AuthRequest): Promise<AuthResponse> {
  return request<AuthResponse>(
    '/auth/login',
    {
      method: 'POST',
      body: JSON.stringify(payload),
    },
    { skipAuthRedirect: true },
  );
}

export function me(): Promise<UserDto> {
  return request<UserDto>('/auth/me', { method: 'GET' });
}

// --- Collections ---
export function getCollections(): Promise<Collection[]> {
  return request<Collection[]>('/collections', { method: 'GET' });
}

export function createCollection(payload: CreateCollectionRequest): Promise<Collection> {
  return request<Collection>('/collections', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function deleteCollection(id: number): Promise<void> {
  return request<void>(`/collections/${id}`, { method: 'DELETE' });
}

// --- Prompts ---
export function getPrompts(collectionId?: number): Promise<Prompt[]> {
  const query = collectionId !== undefined ? `?collectionId=${collectionId}` : '';
  return request<Prompt[]>(`/prompts${query}`, { method: 'GET' });
}

export function getPrompt(id: number): Promise<Prompt> {
  return request<Prompt>(`/prompts/${id}`, { method: 'GET' });
}

export function createPrompt(payload: CreatePromptRequest): Promise<Prompt> {
  return request<Prompt>('/prompts', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updatePrompt(id: number, patch: UpdatePromptRequest): Promise<Prompt> {
  return request<Prompt>(`/prompts/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(patch),
  });
}

export function deletePrompt(id: number): Promise<void> {
  return request<void>(`/prompts/${id}`, { method: 'DELETE' });
}

// --- Version history ---
export function getVersions(id: number): Promise<PromptVersion[]> {
  return request<PromptVersion[]>(`/prompts/${id}/versions`, { method: 'GET' });
}

export function saveVersion(id: number): Promise<PromptVersion> {
  return request<PromptVersion>(`/prompts/${id}/versions`, { method: 'POST' });
}

export function restoreVersion(id: number, versionNumber: number): Promise<Prompt> {
  return request<Prompt>(`/prompts/${id}/versions/${versionNumber}/restore`, {
    method: 'POST',
  });
}

// --- Dashboard ---
export function getDashboard(): Promise<DashboardResponse> {
  return request<DashboardResponse>('/dashboard', { method: 'GET' });
}
