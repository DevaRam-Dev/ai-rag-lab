CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS doc_chunks (
  id        BIGSERIAL PRIMARY KEY,
  filename  VARCHAR(255),
  chunk_index INT,
  content   TEXT,
  embedding TEXT
);
