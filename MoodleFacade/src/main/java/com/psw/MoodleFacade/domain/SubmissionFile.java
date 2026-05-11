package com.psw.MoodleFacade.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "submission_files")
@Data
public class SubmissionFile {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;
    
    @Column(name = "filename", nullable = false)
    private String filename;
    
    @Lob
    @Column(name = "content", nullable = false)
    private byte[] content;
    
    @Column(name = "mime_type")
    private String mimeType;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}