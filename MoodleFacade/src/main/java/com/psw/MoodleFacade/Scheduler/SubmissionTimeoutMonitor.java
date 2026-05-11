package com.psw.MoodleFacade.Scheduler;

import com.psw.MoodleFacade.domain.Submission;
import com.psw.MoodleFacade.domain.SubmissionStatus;
import com.psw.MoodleFacade.repository.SubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class SubmissionTimeoutMonitor {
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Value("${app.submission.timeout-minutes:1}")
    private int timeoutMinutes;
    
    @Scheduled(fixedDelayString = "${app.submission.cleanup-interval-ms:60000}")
    @Transactional
    public void checkTimeouts() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Submission> timedOut = submissionRepository.findTimedOutSubmissions(timeoutThreshold);
        
        if (!timedOut.isEmpty()) {
            log.warn("Found {} timed out submissions", timedOut.size());
            
            for (Submission submission : timedOut) {
                // Reset to PENDING for retry
                submission.setStatus(SubmissionStatus.PENDING);
                submission.setClaimedAt(null);
                submission.setErrorMessage("Timed out - reset for retry");
                submissionRepository.save(submission);
                
                log.info("Reset timed out submission: {}", submission.getId());
            }
        }
    }
}