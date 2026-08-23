import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { repoApi } from '../lib/api';

export default function Dashboard() {
  const [repos, setRepos] = useState([]);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    repoApi.getAll()
      .then(setRepos)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const readyCount = repos.filter(r => r.indexStatus === 'READY').length;
  const indexingCount = repos.filter(r => r.indexStatus === 'INDEXING').length;
  const totalChunks = repos.reduce((sum, r) => sum + (r.chunkCount || 0), 0);

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="page-header-left">
          <h2>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
            </svg>
            Overview
          </h2>
          <p>Your DevPilot workspace at a glance</p>
        </div>
      </div>

      {loading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}>
          <div className="spinner" />
        </div>
      ) : (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-value">{repos.length}</div>
              <div className="stat-label">Total Repositories</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{readyCount}</div>
              <div className="stat-label">Indexed & Ready</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{indexingCount}</div>
              <div className="stat-label">Currently Indexing</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{totalChunks}</div>
              <div className="stat-label">Code Chunks Stored</div>
            </div>
          </div>

          {repos.length === 0 ? (
            <div className="empty-state">
              <div className="empty-state-icon">📦</div>
              <h3>No repositories yet</h3>
              <p>Add a GitHub repository by URL or login with GitHub to sync your repos.</p>
              <button className="btn btn-primary" onClick={() => navigate('/dashboard/repositories')} id="overview-go-repos">
                Go to Repositories
              </button>
            </div>
          ) : (
            <div style={{ marginTop: 8 }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: 12 }}>Recent Repositories</h3>
              <div className="repo-grid">
                {repos.slice(0, 6).map(repo => (
                  <div key={repo.id} className="repo-card" onClick={() => navigate('/dashboard/repositories')} style={{ cursor: 'pointer' }}>
                    <div className="repo-card-header">
                      <div>
                        <div className="repo-card-owner">
                          <span>{repo.owner}</span>
                        </div>
                        <div className="repo-card-name">{repo.name}</div>
                      </div>
                      <span className={`status-badge ${repo.indexStatus?.toLowerCase()}`}>
                        <span className="status-dot" />
                        {repo.indexStatus === 'NEW' ? 'Not Indexed' : repo.indexStatus}
                      </span>
                    </div>
                    {repo.language && (
                      <span className="language-badge">
                        <span className="language-dot" style={{ background: '#7c3aed' }} />
                        {repo.language}
                      </span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
