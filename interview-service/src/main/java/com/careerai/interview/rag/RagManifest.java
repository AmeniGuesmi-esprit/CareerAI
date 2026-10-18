package com.careerai.interview.rag;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "rag_index_manifest")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RagManifest {

    @Id
    private Integer id;

    @Column(name = "corpus_hash", nullable = false, length = 64)
    private String corpusHash;

    @Column(name = "chat_model", nullable = false)
    private String chatModel;

    @Column(name = "embed_model")
    private String embedModel;

    @Column(name = "embed_digest")
    private String embedDigest;

    private Integer dims;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "built_at", nullable = false)
    private LocalDateTime builtAt;
}
