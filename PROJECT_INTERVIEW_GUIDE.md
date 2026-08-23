# DevPilot: Project and RAG Interview Guide

This document explains the DevPilot project from beginner to advanced level. It is based on the current implementation in this repository and is intended to help explain the project confidently in an interview.

## 1. One-Line Explanation

DevPilot is a GitHub codebase question-answering application that uses Retrieval-Augmented Generation (RAG): it downloads repository source files, splits them into chunks, converts those chunks into Gemini embeddings, stores them in PostgreSQL with pgvector, retrieves the most relevant chunks for a user question, and gives those chunks to Gemini to generate a grounded answer with file and line citations.

## 2. The Problem It Solves

When a developer asks a general AI model, "Where is authentication handled in my project?", the model does not automatically know the private repository's code. Sending an entire repository in every prompt is expensive, slow, and usually too large for the model context window.

DevPilot solves this by preparing the repository once and retrieving only the most relevant code for each question.

The application provides:

- GitHub repository import by URL
- GitHub repository synchronization
- Source-code indexing with progress states
- Semantic code search
- Chat sessions associated with repositories
- Streaming Gemini responses through Server-Sent Events (SSE)
- Markdown, code highlighting, and source citations in the UI

## 3. Technology Stack

### Frontend

- React 19
- Vite development server
- React Router for page navigation
- `react-markdown` and `remark-gfm` for Markdown answers
- `react-syntax-highlighter` for code blocks

Important frontend pages are in `frontend/src/pages/`:

- `LandingPage.jsx`: initial product page
- `Dashboard.jsx`: repository statistics and recent repositories
- `Repositories.jsx`: repository list, search, filtering, syncing, and indexing
- `Chat.jsx`: chat UI and SSE response parsing

### Backend

- Java 17 target with Spring Boot 3.4.1
- Spring Web and WebFlux
- Spring Data JPA and Hibernate
- Spring Security
- Spring AI 1.0.0
- PostgreSQL JDBC driver
- Lombok

Important backend packages are under `backend/src/main/java/com/devpilot/`:

- `controller`: REST endpoints
- `service`: business logic and RAG pipeline
- `model`: JPA entities
- `repository`: database access interfaces
- `dto`: API request and response objects
- `config`: security, CORS, and asynchronous execution setup

### Storage and AI

- PostgreSQL stores application data such as users, repositories, chat sessions, and messages.
- The `pgvector` PostgreSQL extension stores and searches embedding vectors.
- Google Gemini is used through its OpenAI-compatible API:
  - Chat: `gemini-2.5-flash`
  - Embeddings: `gemini-embedding-001`
  - Configured vector dimension: `768`

## 4. What Is RAG?

RAG means **Retrieval-Augmented Generation**.

It combines two operations:

1. **Retrieval:** find relevant information from an external knowledge source.
2. **Generation:** ask an LLM to produce an answer using the retrieved information.

In this project:

- The external knowledge source is the indexed GitHub repository.
- Retrieval is semantic similarity search in pgvector.
- Generation is performed by Gemini.

Without RAG, Gemini would answer from its general training knowledge and the current prompt only. With RAG, the prompt contains relevant, current repository code.

### RAG versus model training

RAG does not train or fine-tune Gemini. It does not change Gemini's weights. It dynamically supplies context at question time.

This is useful because repository code can change frequently and private code should not be used to train a general model. The repository can be re-indexed when it changes.

## 5. Key RAG Terms

### Document

A document is a unit stored in the vector store. In DevPilot, a document represents one code chunk plus metadata.

### Chunk

A chunk is a smaller part of a source file. Large files are divided because an entire repository or file is too large and because smaller units produce more precise search results.

In this project, `CodeChunker` uses:

- Maximum chunk size: `1500` characters
- Overlap: `200` characters
- Metadata: file path, start line, and end line

For example, a Java file may become:

```text
Chunk 1: characters 1-1500, lines 1-70
Chunk 2: approximately the final 200 characters of chunk 1 plus new content
Chunk 3: approximately the final 200 characters of chunk 2 plus new content
```

### Chunk overlap

Overlap prevents an important method or explanation from being split into two completely disconnected chunks. It preserves some context at boundaries.

The tradeoff is duplication: overlapping text can create more chunks and therefore more embedding/storage cost.

### Embedding

An embedding is a numerical vector representing the semantic meaning of text. Similar meanings tend to be close together in vector space even when they do not use identical words.

For example, these questions may be semantically close:

- "Where is the login logic?"
- "Which class authenticates a user?"

Keyword search may treat them as different. Embedding search can connect them because both relate to authentication.

DevPilot sends each code chunk to `gemini-embedding-001` and stores the resulting 768-number vector.

### Vector store

A vector store saves embeddings and supports similarity search. DevPilot uses Spring AI's `VectorStore` abstraction backed by PostgreSQL and pgvector.

The vector records also contain metadata such as:

- `repoId`: identifies the repository
- `repoName`: owner and repository name
- `filePath`: source file path
- `startLine` and `endLine`: citation range
- `language`: repository language

### Similarity search

At question time, the user question is embedded and compared with stored code vectors. The closest vectors are returned.

DevPilot retrieves the top `8` documents and applies this filter:

```text
repoId == '<current repository id>'
```

The filter is important because a user asking about repository A should not receive code from repository B.

### Generation

The retrieved chunks are inserted into a system prompt. Gemini then generates an answer using those chunks and the conversation history.

### Grounding

Grounding means constraining an answer with retrieved evidence. DevPilot instructs Gemini to reference file paths and line numbers and to say honestly when the retrieved context is insufficient.

RAG improves grounding, but it does not guarantee correctness. The model can still misunderstand code or make an unsupported inference.

## 6. DevPilot Architecture

```text
React + Vite browser
        |
        | REST and SSE through /api
        v
Spring Boot backend
        |
        +--> GitHub API
        |
        +--> PostgreSQL/JPA
        |       users, repositories, sessions, messages
        |
        +--> Spring AI VectorStore
        |       PostgreSQL + pgvector embeddings
        |
        +--> Gemini API
                embeddings and chat generation
```

The Vite proxy forwards `/api` calls from port `5173` to Spring Boot on port `8080`. PostgreSQL runs through Docker on host port `5433`.

## 7. Repository Ingestion and Indexing Flow

Indexing is the offline or preparation phase of the RAG system.

### Step 1: Add or synchronize a repository

The frontend calls one of these endpoints:

- `POST /api/repos/url`: add a public repository by URL
- `POST /api/repos/sync`: synchronize repositories for the current GitHub user
- `GET /api/repos`: load repositories for the UI

`RepoService` validates the GitHub URL, extracts the owner and repository name, calls GitHub, and saves repository metadata.

### Step 2: Start indexing

When the user clicks **Index**, the frontend calls:

```text
POST /api/repos/{id}/index
```

`RepoController` delegates to `IndexingService.startIndexing(id)`.

The repository is reset to:

- `indexStatus = INDEXING`
- `filesTotal = 0`
- `filesProcessed = 0`
- `chunkCount = 0`
- `errorMessage = null`
- `indexedAt = null`

The actual work is then started asynchronously through the `indexingExecutor`.

### Step 3: Fetch the repository tree

`GitHubApiClient.getRepoTree(...)` requests the Git tree for the repository's default branch. The tree contains paths, types, and sizes.

### Step 4: Filter files

`CodeFileFilter` keeps file-tree entries of type `blob` and excludes files that are not useful for source-code search.

Current exclusions include:

- Generated/build directories: `node_modules`, `dist`, `build`, `target`, `.git`, and similar directories
- Binary, media, archive, document, and font extensions
- Lock files such as `package-lock.json` and `yarn.lock`
- Files such as licenses and operating-system metadata
- Files larger than `100 KB`

This saves API calls, embedding requests, storage, and token usage.

### Step 5: Fetch file content

For every indexable path, `GitHubApiClient.getFileContent(...)` downloads the file content from GitHub.

If the file is blank, it is skipped. An error in one file is logged and processing continues with the next file.

### Step 6: Chunk the file

`CodeChunker.chunkFile(filePath, content)` works as follows:

1. Return no chunks for blank content.
2. Keep a small file as one chunk.
3. Split a large file by lines while keeping each chunk near 1500 characters.
4. Copy approximately 200 characters from the previous chunk into the next chunk.
5. Store the calculated line range.

Each chunk's embedding text begins with context like:

```text
File: src/example/Service.java (lines 20-55)

<source code>
```

Including the path and line range helps both semantic retrieval and answer citations.

### Step 7: Create and store embeddings

For each chunk, `IndexingService` creates a Spring AI `Document` containing:

- The formatted chunk text
- Repository and source metadata

`vectorStore.add(batch)` sends documents to the embedding model and stores their vectors in pgvector. Documents are batched when the batch reaches `32` items, reducing request overhead.

### Step 8: Update progress

The service updates progress after every five files and adds a small delay after every ten files. This helps the UI show progress and reduces pressure on the GitHub API.

### Step 9: Mark the repository ready

After all files are processed, the repository is marked:

```text
indexStatus = READY
filesProcessed = total processed files
chunkCount = total chunks
indexedAt = current timestamp
```

The frontend polls `GET /api/repos` every three seconds while a repository is in `INDEXING` state.

### Failure behavior

If the overall indexing operation fails, the repository becomes `FAILED` and receives an error message. Individual file failures are logged and skipped so one bad file does not necessarily stop the entire repository.

Possible indexing failure causes include invalid GitHub access, rate limits, unavailable embedding models, quota errors, network failures, and vector dimension mismatches.

## 8. What Does "Index" Mean Here?

Indexing has two related meanings in this project:

### Application indexing

The complete preparation pipeline:

```text
GitHub files -> filtering -> chunking -> embeddings -> vector storage
```

### Database/vector indexing

pgvector can use an approximate nearest-neighbor structure such as HNSW to speed up vector similarity search. The configuration selects:

- Index type: `HNSW`
- Distance: cosine distance
- Dimension: `768`

The user-facing **Index** button starts the first meaning. The database internally uses the second meaning to make retrieval efficient.

## 9. Chat and Retrieval Flow

When a user submits a question, the request travels through:

```text
Chat.jsx
  -> POST /api/chat/sessions/{sessionId}/messages
  -> ChatController
  -> ChatService.sendMessageStreaming(...)
  -> VectorSearchService.searchRepoContext(...)
  -> pgvector similarity search
  -> Gemini chat model
  -> SSE token stream
  -> React message renderer
```

### Step 1: Save the user message

`ChatService` loads the chat session and saves the new message with role `USER`.

### Step 2: Load conversation history

The service loads previous messages and uses the last ten messages as conversational history. This allows follow-up questions such as "How is that method called?" to retain context without sending the entire conversation forever.

### Step 3: Retrieve code context

The question is passed to `VectorSearchService`, which performs a similarity search restricted to the current repository and requests the top eight documents.

### Step 4: Build citations

The metadata of retrieved documents is converted into JSON containing file paths and line ranges. This JSON is sent to the frontend after the answer stream and is also stored with the assistant message.

### Step 5: Build the system prompt

The system prompt tells Gemini:

- Which repository is being discussed
- That retrieved code snippets are available
- To reference files and line numbers
- To admit when context is insufficient
- To provide useful Markdown and code examples
- To be concise but thorough

The retrieved snippets are formatted like:

```text
--- File: backend/src/.../Example.java (lines 10-40) ---
<retrieved code>
```

### Step 6: Stream the answer

Spring AI streams Gemini output. The backend maps generated text into events such as:

```json
{"type":"token","content":"..."}
{"type":"citation","content":"[...]"}
{"type":"done","content":""}
```

The React chat page reads the response body, parses the SSE `data:` lines, progressively renders tokens, and displays citations.

### Step 7: Save the assistant answer

When streaming completes, the full assistant response and citation JSON are saved in `chat_messages`.

## 10. Database Model

The application uses JPA entities for normal relational data:

### User

Stores GitHub identity information and an encrypted GitHub access token.

### Repository

Stores:

- GitHub URL, owner, name, branch, language, description
- Whether the repository is private
- Source type: `GITHUB_SYNC` or `URL_ADDED`
- Index status and progress fields
- Error message and indexed timestamp

### ChatSession

Connects a conversation to a repository and optionally a user. It has a title, timestamps, and a collection of messages.

### ChatMessage

Stores the role (`USER` or `ASSISTANT`), message content, citations, session, and creation time.

### Vector documents

Spring AI manages vector documents in the pgvector-backed `vector_store` table. These are separate from JPA chat and repository records but are connected logically through the `repoId` metadata field.

## 11. Why PostgreSQL and pgvector?

PostgreSQL is already useful for users, repositories, sessions, and messages. pgvector allows the same database to store embeddings and perform similarity search.

Advantages:

- One database to operate locally
- SQL support plus vector search
- Transactional relational data alongside vector data
- Good development simplicity
- Metadata filtering by repository ID

For a very large production system, a dedicated vector database could be considered, but pgvector is a practical choice for this project.

## 12. Why Gemini Through an OpenAI-Compatible API?

Spring AI provides an OpenAI-style client abstraction. Google Gemini exposes an OpenAI-compatible endpoint, so the project can use Spring AI's chat and embedding interfaces while pointing the base URL to Google.

The important configuration is:

```yaml
spring:
  ai:
    openai:
      base-url: https://generativelanguage.googleapis.com/v1beta/openai
      chat:
        completions-path: /chat/completions
        options:
          model: gemini-2.5-flash
      embedding:
        options:
          model: gemini-embedding-001
          dimensions: 768
```

The base URL and path must combine to exactly:

```text
https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
```

Using an outdated model or accidentally adding an extra `/v1` produces provider errors such as HTTP 404.

## 13. Important RAG Design Decisions

### Character-based chunks instead of syntax-aware chunks

The current implementation uses a simple line-based character limit. It is easy to understand and supports any programming language, but it can split a method, class, or block in the middle.

A more advanced implementation could use:

- Language-aware parsers
- AST boundaries
- Method/class-level chunks
- Markdown heading boundaries
- Import and dependency summaries

### Fixed top-k retrieval

The service always retrieves eight documents. This is predictable and simple, but not every question needs the same number of chunks.

Possible improvements:

- Similarity threshold tuning
- Dynamic top-k
- MMR or diversity-aware retrieval
- Reranking retrieved chunks
- Query expansion for vague questions

### Repository metadata filter

Filtering by `repoId` is essential for multi-repository isolation. Without it, semantically similar code from another repository could be included in the answer.

### Overlap

The 200-character overlap protects context at chunk boundaries, but increases duplicate content and embedding cost.

### Batch size

Adding documents in batches of 32 reduces the number of vector-store calls. A larger batch may improve throughput, while a smaller batch may reduce memory use and request size.

### Asynchronous indexing

Indexing can involve many GitHub requests and embedding requests, so it runs asynchronously. The API immediately returns after changing the status to `INDEXING`, and the frontend polls for updates.

## 14. Common Interview Questions and Answers

### Q1. What is the main purpose of this project?

**Answer:** DevPilot lets a developer connect a GitHub repository and ask natural-language questions about its code. It uses RAG to retrieve relevant code chunks from a vector database and Gemini to produce grounded answers with citations.

### Q2. Explain the complete RAG pipeline.

**Answer:** During ingestion, the system fetches the GitHub tree, filters irrelevant files, downloads file content, splits files into overlapping chunks, creates embeddings for every chunk, and stores vectors with metadata in pgvector. During a query, it embeds the user's question, retrieves the closest chunks for that repository, puts them into a system prompt, and sends the prompt to Gemini. The answer is streamed back and the citations are saved.

### Q3. Why do we split files into chunks?

**Answer:** Entire repositories are too large and contain too much irrelevant information. Chunks make embedding and retrieval manageable and allow the system to send only relevant sections to Gemini. Overlap preserves context when a logical code block crosses a chunk boundary.

### Q4. What is the difference between a chunk and an embedding?

**Answer:** A chunk is the actual text segment, such as part of a source file. An embedding is the numerical vector generated from that text to represent its meaning. The chunk is shown to Gemini; the embedding is used to find the chunk during similarity search.

### Q5. Why use semantic search instead of keyword search?

**Answer:** Semantic search can match meaning rather than exact words. A question about user login can find code named `authenticate`, `authorize`, or `securityFilterChain` even when the word "login" is absent.

### Q6. What metadata is stored and why?

**Answer:** The system stores repository ID, repository name, file path, line range, and language. `repoId` isolates searches to the correct repository, while path and line metadata provide useful citations in the answer.

### Q7. What happens when a repository is indexed?

**Answer:** Its state changes from `NEW` to `INDEXING`. The service fetches and filters the Git tree, processes each file, creates chunks and embeddings, stores them in pgvector, updates progress, and finally marks the repository `READY`. If the overall process fails, it marks it `FAILED` with an error message.

### Q8. Why is indexing asynchronous?

**Answer:** A repository may contain many files, and each file may require a GitHub request and embedding operation. Running synchronously would block the HTTP request for a long time. The async executor lets the API respond immediately while the frontend polls status.

### Q9. How does the system prevent cross-repository results?

**Answer:** Every vector document contains a `repoId` metadata field. Retrieval applies a pgvector filter for the current repository ID, so similar chunks from another repository are excluded.

### Q10. How does streaming work?

**Answer:** The backend returns a reactive `Flux` as a `TEXT_EVENT_STREAM`. Spring AI emits Gemini output incrementally, and the backend converts it into token, citation, and done events. The React frontend reads the stream and updates the message while tokens arrive.

### Q11. Is this fine-tuning?

**Answer:** No. This is RAG, not fine-tuning. The Gemini model is unchanged. Repository information is retrieved and inserted into the prompt at query time.

### Q12. What are the main limitations?

**Answer:** The chunker is not syntax-aware, retrieval uses a fixed top eight, there is no reranking, repository updates require re-indexing, and the model can still hallucinate if retrieval is incomplete. Production deployment would also need stronger authentication, rate limiting, secret management, and observability.

### Q13. How would you improve retrieval quality?

**Answer:** I would use language-aware or AST-based chunking, add a similarity threshold, rerank candidate chunks, use hybrid keyword plus vector search, add MMR for diversity, include repository summaries and symbol metadata, and evaluate retrieval with a labeled question set.

### Q14. How would you scale indexing?

**Answer:** I would use a job queue, persist resumable indexing jobs, process files with controlled concurrency, cache unchanged file hashes, use incremental indexing based on Git commit or path changes, apply provider rate limits, and monitor embedding latency, failures, and token cost.

### Q15. How would you evaluate the RAG system?

**Answer:** I would separately measure retrieval recall and answer quality. Retrieval evaluation checks whether the correct file/chunk appears in top-k. Generation evaluation checks citation correctness, factuality, completeness, relevance, and refusal when context is insufficient. I would create representative questions for each repository and compare results after every retrieval change.

## 15. Strong 60-Second Interview Answer

> I built DevPilot, a GitHub repository chat application using Spring Boot, React, PostgreSQL, pgvector, and Gemini. A user can add or synchronize a repository and start indexing it. The backend fetches the repository tree from GitHub, filters out generated, binary, and oversized files, splits the remaining source files into overlapping chunks of about 1500 characters, and generates 768-dimensional Gemini embeddings. Those embeddings are stored in PostgreSQL using pgvector along with metadata such as repository ID, file path, and line range.
>
> When the user asks a question, the application embeds the question and performs a repository-filtered similarity search for the top eight relevant chunks. It then builds a prompt containing the conversation history and retrieved code context and sends it to Gemini. The answer is streamed to the React frontend through Server-Sent Events, and the retrieved file paths and line ranges are shown as citations. Indexing is asynchronous so long-running GitHub and embedding operations do not block the API request.

## 16. Strong 2-Minute Technical Explanation

> The project has an ingestion phase and a query phase. In ingestion, `RepoController` receives an indexing request and delegates to `IndexingService`. The service sets the repository state to `INDEXING`, gets the GitHub tree, and passes it to `CodeFileFilter`. That filter removes build directories, dependencies, binaries, lock files, and files larger than 100 KB. The remaining files are downloaded through `GitHubApiClient`.
>
> Each file goes through `CodeChunker`. Small files remain as one chunk; large files are split by lines at approximately 1500 characters with 200 characters of overlap. Every chunk includes file path and line metadata. `IndexingService` creates Spring AI documents, attaches `repoId`, path, line, and language metadata, and adds them to the pgvector store in batches of 32. Gemini creates the embeddings, and PostgreSQL stores the vectors. Once complete, the repository is marked `READY`.
>
> In the query phase, `ChatService` saves the user message, takes the latest ten conversation messages, and calls `VectorSearchService`. That service performs semantic similarity search with a repository ID filter and returns eight documents. The chat service formats those documents into a system prompt, creates citation JSON from their metadata, and streams the Gemini answer. The frontend parses token, citation, and done SSE events. After the stream finishes, the assistant response and citation data are persisted.

## 17. Security and Production Concerns

These are important to mention honestly if asked about production readiness:

- API keys and GitHub tokens should be supplied through environment variables or a secret manager, not committed to `application.yml`.
- The Gemini key and GitHub token previously visible in configuration should be revoked and replaced.
- The default encryption key must be replaced with a strong environment-provided key.
- Repository and chat endpoints should enforce ownership checks; currently several endpoints are publicly permitted in the development security configuration.
- Add rate limiting for GitHub and Gemini calls.
- Validate repository URLs and access permissions carefully.
- Do not log access tokens or full sensitive prompts.
- Add request timeouts, retries with backoff, quotas, and circuit breakers.
- Add structured logs, metrics, tracing, and indexing job observability.
- Add automated tests for chunk boundaries, metadata filters, failed files, provider errors, and SSE parsing.

## 18. Practical Improvement Roadmap

### Beginner improvements

1. Move all secrets to environment variables.
2. Add unit tests for `CodeChunker` and `CodeFileFilter`.
3. Show `errorMessage` and indexing progress clearly in the UI.
4. Prevent indexing when a repository is already `INDEXING`.

### Intermediate improvements

1. Add incremental indexing using the latest Git commit SHA.
2. Add similarity thresholds and configurable top-k.
3. Add retry/backoff for GitHub and Gemini failures.
4. Add ownership checks for every repository and chat endpoint.
5. Add integration tests using a test PostgreSQL/pgvector instance.

### Advanced improvements

1. Use AST-aware chunking and symbol-level metadata.
2. Add hybrid BM25/keyword plus vector retrieval.
3. Add a reranker after initial vector retrieval.
4. Build an evaluation dataset and track retrieval recall and citation accuracy.
5. Use a durable job queue for large repositories.
6. Support branch and commit selection.
7. Detect deleted or modified files and update vectors incrementally.
8. Add prompt-injection defenses for repository content.

## 19. Final Summary

The most important concept is this:

```text
Repository code is not sent blindly to Gemini.
It is filtered, chunked, embedded, stored, retrieved by meaning,
and then supplied as focused context for generation.
```

That is why DevPilot is a RAG application. GitHub provides the data, the chunker creates searchable units, Gemini embeddings enable semantic retrieval, pgvector stores and searches those vectors, and Gemini chat generates the final answer.