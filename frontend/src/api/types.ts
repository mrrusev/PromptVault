// Mirrors the backend DTO contract verbatim (camelCase field names).
// All IDs are numbers (Java Long); all timestamps are ISO-8601 strings.

export interface UserDto {
  id: number;
  username: string;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

export interface Collection {
  id: number;
  name: string;
  ownerId: number;
  createdAt: string;
}

export interface Prompt {
  id: number;
  title: string;
  content: string;
  collectionId: number;
  ownerId: number;
  createdAt: string;
}

export interface PromptVersion {
  id: number;
  promptId: number;
  versionNumber: number;
  content: string;
  createdAt: string;
}

export interface DashboardResponse {
  totalCollections: number;
  totalPrompts: number;
  totalVersions: number;
  latestPrompt: Prompt | null;
}

// Request payloads
export interface AuthRequest {
  username: string;
  password: string;
}

export interface CreateCollectionRequest {
  name: string;
}

export interface CreatePromptRequest {
  collectionId: number;
  title: string;
  content: string;
}

export interface UpdatePromptRequest {
  title?: string;
  content?: string;
}

// Error shape returned by the backend exception handler.
export interface ApiError {
  error: string;
  fields?: Record<string, string>;
}
