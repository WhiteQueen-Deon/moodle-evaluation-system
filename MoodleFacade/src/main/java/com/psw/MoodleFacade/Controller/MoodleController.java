package com.psw.MoodleFacade.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.psw.MoodleFacade.domain.Submission;
import com.psw.MoodleFacade.domain.SubmissionFile;
import com.psw.MoodleFacade.domain.SubmissionStatus;
import com.psw.MoodleFacade.dto.EvaluationResultDTO;
import com.psw.MoodleFacade.dto.SubmissionDTO;
import com.psw.MoodleFacade.repository.SubmissionRepository;
import com.psw.MoodleFacade.service.MoodlePullService;
import com.psw.MoodleFacade.service.SubmissionService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.stream.Collectors.toList;


@RestController
@RequestMapping("/api")
@Slf4j
public class MoodleController {
    
    // prevents multiple concurrent moodle syncs
    private final ReentrantLock syncLock = new ReentrantLock();
    
    @Autowired
    private MoodlePullService moodlePullService;
    
    @Autowired
    private SubmissionService submissionService;
    
    @Autowired
    private SubmissionRepository submissionRepository;
    
    @Value("${moodle.base-url}")
    private String moodleBaseUrl;
    @Value("${moodle.assignment-id}")
    private Long moodleAssignmentId;
    
    // GET /api/submissions/pending
    @GetMapping(value = "/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> pending(
            @RequestParam(defaultValue = "1") int limit,
            @RequestParam(required = false) String workerId
    ) {
        if (limit <= 0) limit = 1;
        
        log.info("/pending reached step 1");

        List<Submission> list = submissionRepository
                .findAllPendingForUpdate()
                .stream()
                .limit(limit)
                .toList();
        log.info("/pending reached step 2");

        if (list.isEmpty()) {
            log.info("no pending submissions found. Pulling new submissions from Moodle.");
            syncSubmissions(moodleAssignmentId);
            list = submissionRepository
                    .findAllPendingForUpdate()
                    .stream()
                    .limit(limit)
                    .toList();
        }

        if (list.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        log.info("/pending reached step 3");

        LocalDateTime now = LocalDateTime.now();
        
        if (limit == 1) {
            Submission s = list.get(0);
            s.setClaimedAt(now);
            s.setStatus(SubmissionStatus.PROCESSING);
            submissionRepository.save(s);

            // Build files array from submissionFiles
            List<Map<String, Object>> filesArray = new ArrayList<>();
            if (s.getSubmissionFiles() != null && !s.getSubmissionFiles().isEmpty()) {
                for (SubmissionFile sf : s.getSubmissionFiles()) {
                    byte[] content = sf.getContent();
                    filesArray.add(Map.of(
                        "fileName", sf.getFilename(),
                        "content", content
                    ));
                }
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", s.getId().toString());
            payload.put("submissionId", s.getSubmissionId() != null ? s.getSubmissionId().toString() : s.getId().toString());
            payload.put("language", detectLanguageFromFilename(s));
            payload.put("files", filesArray);
            
            return ResponseEntity.ok(List.of(payload));
        }

        else {
            for (Submission s : list) {
                s.setClaimedAt(now);
                s.setStatus(SubmissionStatus.PROCESSING);
            }
            submissionRepository.saveAll(list);

            List<SubmissionDTO> resp = list.stream()
                    .map(this::toDTO)
                    .collect(toList());
            log.info("/pending reached step 5");
            return ResponseEntity.ok(resp);
        }
    }

    @PostMapping("/{submissionId}/result")
    public ResponseEntity<Void> complete(
            @PathVariable Long submissionId,
            @Valid @RequestBody EvaluationResultDTO result) {
        
        log.info("Receiving completion for submission: {}", submissionId);
        
        try {
            submissionService.completeSubmission(submissionId, result);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            log.error("Invalid state for completion: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (JsonProcessingException e) {
            log.error("Error serializing result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    public void syncSubmissions(Long assignmentId) {
        // if another thread is already syncing, skip
        if (syncLock.tryLock()) {
            try {
                log.info("Syncing submissions");
                moodlePullService.syncSubmissionsFromMoodle(assignmentId, moodleBaseUrl);
            } finally {
                syncLock.unlock();
            }
        } else {
            log.debug("Sync already in progress, skipping");
        }
    }
    
    private SubmissionDTO toDTO(Submission submission) {
        SubmissionDTO dto = new SubmissionDTO();
        dto.setId(submission.getId());
        dto.setSubmissionId(submission.getSubmissionId());
        dto.setAssignmentId(submission.getAssignmentId());
        dto.setUserId(submission.getUserId());
        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setFiles(submission.getFiles());
        dto.setStatus(submission.getStatus());
        dto.setCreatedAt(submission.getCreatedAt());
        dto.setClaimedAt(submission.getClaimedAt());
        return dto;
    }

    private String detectLanguageFromFilename(Submission submission) {
    String files = submission.getFiles();
    if (files != null && !files.isEmpty()) {
        String lower = files.toLowerCase();
        if (lower.contains(".py")) return "python";
        if (lower.contains(".cpp") || lower.contains(".cc")) return "cpp";
        if (lower.contains(".c\"") || lower.contains(".c,")) return "c";
        if (lower.contains(".java")) return "java";
    }
    
    if (submission.getSubmissionFiles() != null) {
        for (var file : submission.getSubmissionFiles()) {
            String filename = file.getFilename();
            if (filename != null) {
                String lower = filename.toLowerCase();
                if (lower.endsWith(".py")) return "python";
                if (lower.endsWith(".cpp") || lower.endsWith(".cc")) return "cpp";
                if (lower.endsWith(".c")) return "c";
                if (lower.endsWith(".java")) return "java";
            }
        }
    }
    
    return "python"; // default
}
}