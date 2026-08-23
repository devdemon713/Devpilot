import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { chatApi, repoApi } from '../lib/api';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';

export default function Chat() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  const [session, setSession] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [streamingContent, setStreamingContent] = useState('');
  const [citations, setCitations] = useState([]);
  const [repoInfo, setRepoInfo] = useState(null);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);

  // Load session and messages
  useEffect(() => {
    if (!sessionId) return;

    chatApi.getMessages(parseInt(sessionId))
      .then(setMessages)
      .catch(console.error);
  }, [sessionId]);

  // Load session details from messages context
  useEffect(() => {
    // Fetch all sessions to show in sidebar — we need a repoId first
    // For now, we rely on session data being available
  }, []);

  // Scroll to bottom on new messages
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent]);

  const handleSend = async () => {
    if (!input.trim() || streaming) return;

    const userMessage = input.trim();
    setInput('');
    setStreaming(true);
    setStreamingContent('');
    setCitations([]);

    // Add user message to UI immediately
    const userMsg = {
      id: Date.now(),
      role: 'USER',
      content: userMessage,
      createdAt: new Date().toISOString(),
    };
    setMessages(prev => [...prev, userMsg]);

    try {
      const response = await chatApi.sendMessageStream(parseInt(sessionId), userMessage);
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let fullContent = '';
      let citationsData = [];
      let sseBuffer = '';
      let pendingContent = '';
      let renderFrame = null;

      const flushStreamingContent = () => {
        renderFrame = null;
        setStreamingContent(pendingContent);
      };

      const updateStreamingContent = (content) => {
        pendingContent = content;
        if (renderFrame === null) {
          renderFrame = requestAnimationFrame(flushStreamingContent);
        }
      };

      const processSseEvent = (rawEvent) => {
        const data = rawEvent
          .split('\n')
          .filter(line => line.startsWith('data:'))
          .map(line => line.slice(5).trim())
          .join('\n');

        if (!data) return;

        try {
          const event = JSON.parse(data);

          if (event.type === 'token') {
            fullContent += event.content;
            updateStreamingContent(fullContent);
          } else if (event.type === 'citation') {
            try {
              citationsData = JSON.parse(event.content);
              setCitations(citationsData);
            } catch (e) { /* ignore parse error */ }
          } else if (event.type === 'error') {
            fullContent += '\n\nError: ' + event.content;
            updateStreamingContent(fullContent);
          }
        } catch (e) {
          // Ignore incomplete or non-JSON SSE events instead of dropping the stream.
        }
      };

      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          sseBuffer += decoder.decode();
          if (sseBuffer.trim()) processSseEvent(sseBuffer);
          break;
        }

        sseBuffer += decoder.decode(value, { stream: true });
        const events = sseBuffer.split(/\r?\n\r?\n/);
        sseBuffer = events.pop() || '';

        for (const event of events) {
          processSseEvent(event);
        }
      }

      if (renderFrame !== null) {
        cancelAnimationFrame(renderFrame);
        setStreamingContent(fullContent);
      }

      // Add assistant message
      const assistantMsg = {
        id: Date.now() + 1,
        role: 'ASSISTANT',
        content: fullContent,
        citations: citationsData.length > 0 ? JSON.stringify(citationsData) : null,
        createdAt: new Date().toISOString(),
      };
      setMessages(prev => [...prev, assistantMsg]);
      setStreamingContent('');
    } catch (err) {
      console.error('Stream error:', err);
      const errorMsg = {
        id: Date.now() + 1,
        role: 'ASSISTANT',
        content: '⚠️ Failed to get response: ' + err.message,
        createdAt: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errorMsg]);
    } finally {
      setStreaming(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const renderCitations = (citationStr) => {
    if (!citationStr) return null;
    try {
      const cites = JSON.parse(citationStr);
      if (!cites || cites.length === 0) return null;

      return (
        <div className="citations">
          <div className="citations-title">Referenced Files</div>
          {cites.map((cite, i) => (
            <span key={i} className="citation-item">
              📄 {cite.filePath}:{cite.startLine}-{cite.endLine}
            </span>
          ))}
        </div>
      );
    } catch {
      return null;
    }
  };

  const CodeBlock = ({ children, className, ...props }) => {
    const match = /language-(\w+)/.exec(className || '');
    const language = match ? match[1] : '';

    return match ? (
      <SyntaxHighlighter
        style={oneDark}
        language={language}
        PreTag="div"
        customStyle={{
          margin: '10px 0',
          borderRadius: '8px',
          fontSize: '0.82rem',
        }}
        {...props}
      >
        {String(children).replace(/\n$/, '')}
      </SyntaxHighlighter>
    ) : (
      <code className={className} {...props}>
        {children}
      </code>
    );
  };

  return (
    <div className="chat-layout">
      {/* Chat Main Area */}
      <div className="chat-main">
        <div className="chat-header">
          <div>
            <h3>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginRight: 8, verticalAlign: 'middle' }}>
                <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
              </svg>
              Chat Session
            </h3>
          </div>
          <button className="btn btn-secondary" onClick={() => navigate('/dashboard/repositories')} style={{ fontSize: '0.8rem' }}>
            ← Back to Repos
          </button>
        </div>

        {/* Messages */}
        <div className="chat-messages">
          {messages.length === 0 && !streaming && (
            <div className="empty-state">
              <div className="empty-state-icon">💬</div>
              <h3>Start a conversation</h3>
              <p>Ask a question about the codebase. The AI will use the indexed code as context to provide accurate answers.</p>
            </div>
          )}

          {messages.map(msg => (
            <div key={msg.id} className={`message ${msg.role.toLowerCase()}`}>
              <div className="message-avatar">
                {msg.role === 'ASSISTANT' ? '⚡' : '👤'}
              </div>
              <div>
                <div className="message-content">
                  {msg.role === 'ASSISTANT' ? (
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm]}
                      components={{ code: CodeBlock }}
                    >
                      {msg.content}
                    </ReactMarkdown>
                  ) : (
                    msg.content
                  )}
                </div>
                {msg.role === 'ASSISTANT' && renderCitations(msg.citations)}
              </div>
            </div>
          ))}

          {/* Streaming message */}
          {streaming && streamingContent && (
            <div className="message assistant">
              <div className="message-avatar">⚡</div>
              <div>
                <div className="message-content">
                  <ReactMarkdown
                    remarkPlugins={[remarkGfm]}
                    components={{ code: CodeBlock }}
                  >
                    {streamingContent}
                  </ReactMarkdown>
                </div>
              </div>
            </div>
          )}

          {/* Typing indicator */}
          {streaming && !streamingContent && (
            <div className="message assistant">
              <div className="message-avatar">⚡</div>
              <div className="message-content">
                <div className="typing-indicator">
                  <div className="typing-dot" />
                  <div className="typing-dot" />
                  <div className="typing-dot" />
                </div>
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        <div className="chat-input-area">
          <div className="chat-input-wrapper">
            <textarea
              ref={inputRef}
              className="chat-input"
              placeholder="Ask a question about the code..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              rows={1}
              disabled={streaming}
              id="chat-message-input"
            />
            <button
              className="chat-send-btn"
              onClick={handleSend}
              disabled={!input.trim() || streaming}
              id="chat-send-btn"
            >
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
              </svg>
            </button>
          </div>
          <div style={{ textAlign: 'center', fontSize: '0.7rem', color: 'var(--text-muted)', marginTop: 8 }}>
            DevPilot uses RAG to search your codebase for relevant context before answering.
          </div>
        </div>
      </div>
    </div>
  );
}
