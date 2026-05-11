package com.psw.MoodleFacade.dto;

import com.psw.MoodleFacade.domain.SubmissionStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SubmissionDTO {
    private Long id;
    private Long submissionId;
    private Long assignmentId;
    private Long userId;
    private String submittedAt;
    private String files;
    private SubmissionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime claimedAt;
}