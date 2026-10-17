package com.careerai.interview.rag;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rag_chunks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RagChunk {

    @Id
    @Column(name = "chunk_id", length = 64)
    private String chunkId;

    @Column(name = "doc_id", nullable = false, length = 100)
    private String docId;

    @Column(name = "citation_id", nullable = false)
    private String citationId;

    @Column(nullable = false, length = 40)
    private String track;

    @Column(name = "doc_type", nullable = false, length = 30)
    private String docType;

    private String niveau;

    private String section;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    @Column(name = "index_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String indexText;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "embed_model")
    private String embedModel;

    @Column(columnDefinition = "MEDIUMBLOB")
    private byte[] embedding;
}
