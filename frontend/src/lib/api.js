const API_BASE = '/api';

export async function fetchApi(url, options = {}) {
  const response = await fetch(`${API_BASE}${url}`, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ error: response.statusText }));
    throw new Error(error.error || 'Request failed');
  }

  return response.json();
}

// Auth
export const authApi = {
  getMe: () => fetchApi('/auth/me').catch(() => null),
  loginUrl: () => `${API_BASE}/auth/github`,
  logout: () => fetchApi('/auth/logout', { method: 'POST' }),
};

// Repos
export const repoApi = {
  getAll: () => fetchApi('/repos'),
  getById: (id) => fetchApi(`/repos/${id}`),
  addByUrl: (githubUrl) => fetchApi('/repos/url', {
    method: 'POST',
    body: JSON.stringify({ githubUrl }),
  }),
  sync: () => fetchApi('/repos/sync', { method: 'POST' }),
  index: (id) => fetchApi(`/repos/${id}/index`, { method: 'POST' }),
  delete: (id) => fetchApi(`/repos/${id}`, { method: 'DELETE' }),
};

// Chat
export const chatApi = {
  createSession: (repoId, title) => fetchApi('/chat/sessions', {
    method: 'POST',
    body: JSON.stringify({ repoId, title }),
  }),
  getSessions: (repoId) => fetchApi(`/chat/sessions?repoId=${repoId}`),
  getMessages: (sessionId) => fetchApi(`/chat/sessions/${sessionId}/messages`),
  sendMessageStream: (sessionId, content) => {
    return fetch(`${API_BASE}/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content }),
    });
  },
};

// Language colors for badges
export const LANGUAGE_COLORS = {
  JavaScript: '#f1e05a',
  TypeScript: '#3178c6',
  Python: '#3572A5',
  Java: '#b07219',
  'C++': '#f34b7d',
  'C#': '#178600',
  Go: '#00ADD8',
  Rust: '#dea584',
  Ruby: '#701516',
  PHP: '#4F5D95',
  Swift: '#F05138',
  Kotlin: '#A97BFF',
  Dart: '#00B4AB',
  HTML: '#e34c26',
  CSS: '#563d7c',
  Shell: '#89e051',
  'Jupyter Notebook': '#DA5B0B',
  Vue: '#41b883',
  Scala: '#c22d40',
  R: '#198CE7',
};

export function getLanguageColor(language) {
  return LANGUAGE_COLORS[language] || '#8b8b8b';
}

// Avatar color from string
export function getAvatarColor(str) {
  const colors = [
    '#ef4444', '#f97316', '#f59e0b', '#84cc16',
    '#10b981', '#06b6d4', '#3b82f6', '#8b5cf6',
    '#a855f7', '#ec4899', '#f43f5e', '#6366f1',
  ];
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }
  return colors[Math.abs(hash) % colors.length];
}
