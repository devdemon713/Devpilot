import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { repoApi } from '../lib/api';

export default function LandingPage() {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleAddRepo = async (e) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError('');

    try {
      await repoApi.addByUrl(url.trim());
      navigate('/dashboard/repositories');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="landing-page">
      {/* Background glow effects */}
      <div className="bg-glow bg-glow-1" />
      <div className="bg-glow bg-glow-2" />

      {/* Nav */}
      <nav className="landing-nav">
        <span className="landing-nav-brand">⚡ DevPilot</span>
        <div style={{ display: 'flex', gap: '10px' }}>
          <a href="/api/auth/github" className="btn btn-github btn-lg" id="landing-github-login">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            Login with GitHub
          </a>
          <button className="btn btn-secondary btn-lg" onClick={() => navigate('/dashboard/repositories')} id="landing-dashboard-btn">
            Dashboard →
          </button>
        </div>
      </nav>

      {/* Hero */}
      <section className="landing-hero">
        <h1>
          Chat with your <span>codebase</span> using AI
        </h1>
        <p>
          Connect your GitHub repositories, index your code with vector embeddings, and get instant AI-powered answers about your codebase.
        </p>

        <form onSubmit={handleAddRepo} className="landing-actions">
          <input
            type="text"
            className="landing-url-input"
            placeholder="Paste a GitHub repo URL... (e.g. github.com/user/repo)"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            id="landing-url-input"
          />
          <button type="submit" className="btn btn-primary btn-lg" disabled={loading} id="landing-add-repo-btn">
            {loading ? (
              <><span className="spinner" style={{ width: 18, height: 18, borderWidth: 2 }} /> Adding...</>
            ) : (
              <>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
                </svg>
                Index & Chat
              </>
            )}
          </button>
        </form>

        {error && <p style={{ color: 'var(--accent-red)', marginTop: 12, fontSize: '0.85rem' }}>{error}</p>}
      </section>

      {/* Features */}
      <section className="landing-features">
        <div className="feature-card">
          <div className="feature-icon" style={{ background: 'var(--accent-purple-light)', color: 'var(--accent-purple)' }}>🔗</div>
          <h3>Connect Repos</h3>
          <p>Login with GitHub to sync your repos, or simply paste any public repository URL to get started instantly.</p>
        </div>
        <div className="feature-card">
          <div className="feature-icon" style={{ background: 'var(--accent-green-light)', color: 'var(--accent-green)' }}>🧠</div>
          <h3>Smart Indexing</h3>
          <p>Your code is chunked, embedded, and stored in a vector database for lightning-fast semantic search and context retrieval.</p>
        </div>
        <div className="feature-card">
          <div className="feature-icon" style={{ background: 'var(--accent-pink-light)', color: 'var(--accent-pink)' }}>💬</div>
          <h3>AI Chat</h3>
          <p>Ask questions about your code and get accurate, context-aware answers with file citations and streaming responses.</p>
        </div>
      </section>
    </div>
  );
}
