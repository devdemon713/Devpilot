import { useState, useEffect, useCallback } from 'react';
import { useNavigate, useOutletContext } from 'react-router-dom';
import { repoApi, getLanguageColor, getAvatarColor } from '../lib/api';
import AddRepoModal from '../components/AddRepoModal';

export default function Repositories() {
  const [repos, setRepos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [search, setSearch] = useState('');
  const [visibilityFilter, setVisibilityFilter] = useState('All');
  const [statusFilter, setStatusFilter] = useState('All');
  const { user } = useOutletContext();
  const navigate = useNavigate();

  const loadRepos = useCallback(async () => {
    try {
      const data = await repoApi.getAll();
      setRepos(data);
    } catch (err) {
      console.error('Failed to load repos:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRepos();
  }, [loadRepos]);

  // Poll for indexing updates
  useEffect(() => {
    const hasIndexing = repos.some(r => r.indexStatus === 'INDEXING');
    if (!hasIndexing) return;

    const interval = setInterval(loadRepos, 3000);
    return () => clearInterval(interval);
  }, [repos, loadRepos]);

  const handleSync = async () => {
    if (!user) {
      window.location.href = '/api/auth/github';
      return;
    }
    setSyncing(true);
    try {
      await repoApi.sync();
      await loadRepos();
    } catch (err) {
      alert('Sync failed: ' + err.message);
    } finally {
      setSyncing(false);
    }
  };

  const handleIndex = async (repoId) => {
    try {
      await repoApi.index(repoId);
      await loadRepos();
    } catch (err) {
      alert('Index failed: ' + err.message);
    }
  };

  const handleChat = async (repo) => {
    try {
      const session = await (await import('../lib/api')).chatApi.createSession(repo.id, 'Chat about ' + repo.name);
      navigate(`/dashboard/chat/${session.id}`);
    } catch (err) {
      alert('Failed to create chat session: ' + err.message);
    }
  };

  const handleRepoAdded = () => {
    setShowAddModal(false);
    loadRepos();
  };

  // Filters
  const filtered = repos.filter(repo => {
    if (search && !repo.name.toLowerCase().includes(search.toLowerCase()) &&
        !repo.owner.toLowerCase().includes(search.toLowerCase())) return false;
    if (visibilityFilter === 'Public' && repo.private) return false;
    if (visibilityFilter === 'Private' && !repo.private) return false;
    if (statusFilter !== 'All' && repo.indexStatus !== statusFilter.toUpperCase()) return false;
    return true;
  });

  const readyCount = repos.filter(r => r.indexStatus === 'READY').length;

  return (
    <div className="page-container">
      {/* Header */}
      <div className="page-header">
        <div className="page-header-left">
          <h2>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
            Repositories
          </h2>
          <p>{repos.length} connected · {readyCount} ready</p>
        </div>
        <div className="page-header-right">
          <div className="search-wrapper">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              type="text"
              className="search-input"
              placeholder="Search repositories..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              id="repo-search-input"
            />
          </div>
          <button className="btn btn-primary" onClick={handleSync} disabled={syncing} id="repo-sync-btn">
            {syncing ? (
              <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Syncing...</>
            ) : (
              <>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
                </svg>
                Sync
              </>
            )}
          </button>
          <button className="btn btn-secondary" onClick={() => setShowAddModal(true)} id="repo-add-url-btn">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            Add URL
          </button>
          <button className="btn-icon" onClick={loadRepos} title="Refresh" id="repo-refresh-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="filter-bar">
        <div className="filter-group">
          <span className="filter-label">Visibility</span>
          {['All', 'Public', 'Private'].map(f => (
            <button
              key={f}
              className={`filter-tab ${visibilityFilter === f ? 'active' : ''}`}
              onClick={() => setVisibilityFilter(f)}
            >
              {f}
            </button>
          ))}
        </div>
        <div style={{ flex: 1 }} />
        <div className="filter-group">
          <span className="filter-label">Status</span>
          {['All', 'Ready', 'Indexing', 'New', 'Failed'].map(f => (
            <button
              key={f}
              className={`filter-tab ${statusFilter === f ? 'active' : ''}`}
              onClick={() => setStatusFilter(f)}
            >
              {f}
            </button>
          ))}
        </div>
      </div>

      {/* Repo Grid */}
      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}>
          <div className="spinner" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="empty-state">
          <div className="empty-state-icon">📦</div>
          <h3>{repos.length === 0 ? 'No repositories yet' : 'No matching repositories'}</h3>
          <p>{repos.length === 0 ? 'Sync your GitHub repos or add a public repo by URL.' : 'Try adjusting your search or filters.'}</p>
          {repos.length === 0 && (
            <div style={{ display: 'flex', gap: 10 }}>
              <button className="btn btn-primary" onClick={handleSync} id="empty-sync-btn">Sync GitHub</button>
              <button className="btn btn-secondary" onClick={() => setShowAddModal(true)} id="empty-add-url-btn">Add by URL</button>
            </div>
          )}
        </div>
      ) : (
        <div className="repo-grid">
          {filtered.map(repo => (
            <div key={repo.id} className="repo-card">
              <div className="repo-card-header">
                <div>
                  <div className="repo-card-owner">
                    <div
                      className="repo-avatar"
                      style={{ background: getAvatarColor(repo.owner) }}
                    >
                      {repo.owner.charAt(0).toUpperCase()}
                    </div>
                    <span>{repo.owner}</span>
                  </div>
                  <div className="repo-card-name">{repo.name}</div>
                </div>
                <span className={`status-badge ${repo.indexStatus?.toLowerCase()}`}>
                  <span className="status-dot" />
                  {repo.indexStatus === 'NEW' ? 'Not Indexed' : repo.indexStatus}
                </span>
              </div>

              <div className="repo-card-description">
                {repo.description || 'No description provided.'}
              </div>

              {/* Index progress */}
              {repo.indexStatus === 'INDEXING' && (
                <div>
                  <div className="progress-bar">
                    <div
                      className="progress-bar-fill"
                      style={{ width: `${repo.filesTotal > 0 ? (repo.filesProcessed / repo.filesTotal * 100) : 0}%` }}
                    />
                  </div>
                  <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)', marginTop: 4 }}>
                    {repo.filesProcessed} / {repo.filesTotal} files · {repo.chunkCount} chunks
                  </div>
                </div>
              )}

              <div className="repo-card-meta">
                <span className="repo-meta-item">
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="6" y1="3" x2="6" y2="15"/><circle cx="18" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="M18 9a9 9 0 0 1-9 9"/>
                  </svg>
                  {repo.defaultBranch || 'main'}
                </span>

                {repo.language && (
                  <span className="language-badge">
                    <span className="language-dot" style={{ background: getLanguageColor(repo.language) }} />
                    {repo.language}
                  </span>
                )}

                {repo.indexStatus === 'READY' && repo.chunkCount > 0 && (
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                    {repo.chunkCount} chunks
                  </span>
                )}
              </div>

              <div className="repo-card-actions">
                <a
                  href={repo.githubUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn btn-ghost"
                  style={{ fontSize: '0.78rem' }}
                >
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
                  </svg>
                  GitHub
                </a>

                {repo.indexStatus === 'READY' && (
                  <button
                    className="btn btn-ghost"
                    onClick={() => handleChat(repo)}
                    style={{ fontSize: '0.78rem' }}
                  >
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                    Chat
                  </button>
                )}

                <div style={{ flex: 1 }} />

                {repo.indexStatus === 'READY' ? (
                  <button
                    className="btn btn-primary"
                    onClick={() => handleChat(repo)}
                    style={{ fontSize: '0.78rem' }}
                  >
                    Open →
                  </button>
                ) : repo.indexStatus === 'INDEXING' ? (
                  <button className="btn btn-secondary" disabled style={{ fontSize: '0.78rem' }}>
                    <span className="spinner" style={{ width: 12, height: 12, borderWidth: 2 }} />
                    Indexing...
                  </button>
                ) : (
                  <button
                    className="btn btn-primary"
                    onClick={() => handleIndex(repo.id)}
                    style={{ fontSize: '0.78rem' }}
                  >
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
                    </svg>
                    Index
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add Repo Modal */}
      {showAddModal && (
        <AddRepoModal
          onClose={() => setShowAddModal(false)}
          onAdded={handleRepoAdded}
        />
      )}
    </div>
  );
}
