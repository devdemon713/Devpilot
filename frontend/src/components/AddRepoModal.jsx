import { useState } from 'react';
import { repoApi } from '../lib/api';

export default function AddRepoModal({ onClose, onAdded }) {
  const [url, setUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!url.trim()) return;

    setLoading(true);
    setError('');

    try {
      await repoApi.addByUrl(url.trim());
      onAdded();
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3>Add Repository by URL</h3>
        <p>Paste a public GitHub repository URL to add it to your workspace.</p>

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            className="modal-input"
            placeholder="https://github.com/owner/repository"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            autoFocus
            id="add-repo-url-input"
          />

          {error && (
            <p style={{ color: 'var(--accent-red)', fontSize: '0.82rem', marginBottom: 14 }}>
              ⚠️ {error}
            </p>
          )}

          <div className="modal-actions">
            <button type="button" className="btn btn-secondary" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary" disabled={loading || !url.trim()} id="add-repo-submit-btn">
              {loading ? (
                <><span className="spinner" style={{ width: 14, height: 14, borderWidth: 2 }} /> Adding...</>
              ) : (
                'Add Repository'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
