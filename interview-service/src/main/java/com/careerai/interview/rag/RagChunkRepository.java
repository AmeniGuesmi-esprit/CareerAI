package com.careerai.interview.rag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RagChunkRepository extends JpaRepository<RagChunk, String> {
}
