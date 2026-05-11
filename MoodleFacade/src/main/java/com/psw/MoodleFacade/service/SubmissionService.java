package com.psw.MoodleFacade.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psw.MoodleFacade.domain.Submission;
import com.psw.MoodleFacade.domain.SubmissionStatus;
import com.psw.MoodleFacade.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.psw.MoodleFacade.dto.EvaluationResultDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SubmissionService {
    
    @Autowired
    private SubmissionRepository submissionRepository;
    @Autowired
    private MoodlePushService moodlePushService;
    @Autowired
    private ObjectMapper objectMapper;
    
    @Transactional
    public Optional<Submission> claimNextSubmission() {
        log.debug("Attempting to claim next pending submission");
        
        Optional<Submission> submissionOpt = submissionRepository.findFirstPendingForUpdate();
        
        if (submissionOpt.isEmpty()) {
            log.debug("No pending submissions available");
            return Optional.empty();
        }
        
        Submission submission = submissionOpt.get();
        
        submission.setStatus(SubmissionStatus.PROCESSING);
        submission.setClaimedAt(LocalDateTime.now());
        
        Submission saved = submissionRepository.save(submission);
        
        log.info("Claimed submission: id={}, moodleId={}, assignmentId={}", 
                 saved.getId(), saved.getSubmissionId(), saved.getAssignmentId());
        
        return Optional.of(saved);
    }
    
    @Transactional
    public void completeSubmission(Long submissionDbId, Object result) throws JsonProcessingException {
        
        Submission submission = submissionRepository.findById(submissionDbId)
            .orElseThrow(() -> new IllegalStateException("Submission not found: " + submissionDbId));
        
        if (submission.getStatus() != SubmissionStatus.PROCESSING) {
            throw new IllegalStateException("Submission not in processing state");
        }
        
        // Save result to database
        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setResult(objectMapper.writeValueAsString(result));
        submissionRepository.save(submission);
        
        // Push grade to Moodle
        if (result instanceof EvaluationResultDTO evaluationResult) {
            log.info("EvaluationResult received: score={}, feedback='{}', reportContent length={}, resultFiles={}", 
                     evaluationResult.getScore(),
                     evaluationResult.getFeedback(),
                     evaluationResult.getResultFiles() != null ? evaluationResult.getResultFiles().size() : "null");
            
            // Try to get reportContent from resultFiles if not directly provided
            List<EvaluationResultDTO.ResultFile> resultFiles = evaluationResult.getResultFiles();
            try {
                log.info("Calling saveGradeToMoodle: assignmentId={}, userId={}, score={}", 
                         submission.getAssignmentId(), submission.getUserId(), evaluationResult.getScore());
                moodlePushService.saveGradeToMoodle(
                    submission.getAssignmentId(),
                    submission.getUserId(),
                    evaluationResult.getScore(),
                    evaluationResult.getFeedback(),
                    resultFiles
                );
                log.info("Grade submitted to Moodle: submission={}, score={}", 
                         submissionDbId, evaluationResult.getScore());
            } catch (Exception e) {
                log.error("Failed to push grade to Moodle for submission {}", submissionDbId, e);
                // Don't fail the transaction - grade is saved locally
            }
        } else {
            log.warn("Result is not an EvaluationResultDTO: {}", result.getClass().getName());
        }
    }
        
    
    @Transactional(readOnly = true)
    public Optional<Submission> findById(Long id) {
        return submissionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Submission> findAll() {
        return submissionRepository.findAll();
    }
}