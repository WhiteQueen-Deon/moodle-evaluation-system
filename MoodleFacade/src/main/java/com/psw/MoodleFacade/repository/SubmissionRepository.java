package com.psw.MoodleFacade.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.psw.MoodleFacade.domain.Submission;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Submission s WHERE s.status = 'PENDING' ORDER BY s.createdAt ASC")
    List<Submission> findAllPendingForUpdate();
    
    Optional<Submission> findBySubmissionId(Long submissionId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT * FROM submissions WHERE status = 'PENDING' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Submission> findFirstPendingForUpdate();

    Optional<Submission> findBySubmissionIdAndAssignmentId(Long submissionId, Long assignmentId);

    @Query("SELECT CONCAT(s.submissionId, '-', s.assignmentId) FROM Submission s " +
           "WHERE CONCAT(s.submissionId, '-', s.assignmentId) IN :keys")
    Set<String> findExistingSubmissionKeys(@Param("keys") List<String> keys);

    @Query("SELECT s FROM Submission s WHERE s.status = 'PROCESSING' " +
           "AND s.claimedAt < :timeout")
    List<Submission> findTimedOutSubmissions(@Param("timeout") LocalDateTime timeout);
}
