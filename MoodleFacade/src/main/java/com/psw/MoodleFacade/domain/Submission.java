package com.psw.MoodleFacade.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions", indexes = {
    @Index(name = "idx_status_created", columnList = "status, created_at"),
    @Index(name = "idx_submission_assignment", columnList = "submission_id, assignment_id", unique = true)
})
@Data
public class Submission {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "submission_id", nullable = false)
    private Long submissionId;
    
    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "submitted_at")
    private String submittedAt; 
    
    @Lob
    @Column(name = "files", columnDefinition = "TEXT")
    private String files; 
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;
    
    @Lob
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SubmissionFile> submissionFiles = new ArrayList<>();
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}