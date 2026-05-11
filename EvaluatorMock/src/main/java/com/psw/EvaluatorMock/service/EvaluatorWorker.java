package com.psw.EvaluatorMock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@EnableScheduling
@Component
public class EvaluatorWorker {
    private static final Logger log = LoggerFactory.getLogger(EvaluatorWorker.class);

    private final RestClient http = RestClient.create();
    private final String pullUrl;
    private final String resultUrl;

    public EvaluatorWorker(
            @Value("${facade.pull.url}") String pullUrl,
            @Value("${facade.result.url}") String resultUrl
    ) {
        this.pullUrl = pullUrl;
        this.resultUrl = resultUrl;
    }

    /** Poll facade; get single submission, post a fixed score=60 back. */
    @Scheduled(fixedDelayString = "${poll.intervalMs:5000}")
    public void runOnce() {
        try {
            Map<String, Object> item = http.get()
                    .uri(pullUrl)
                    .retrieve()
                    .body(Map.class);

            if (item == null || item.isEmpty()) {
                log.info("No pending submissions (empty response).");
                return;
            }

            long submissionId = asLong(item.get("submissionId"));
            long assignmentId = asLong(item.get("assignmentId"));
            long userId       = asLong(item.get("userId"));

            int score = 60;
            String feedback = "Mock evaluator: fixed score 60";
            
            http.post()
                    .uri(resultUrl+"/"+submissionId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "score", 60.0, 
                            "feedback", feedback
                    ))
                    .retrieve()
                    .toBodilessEntity();

            log.info("✅ Posted result: submission={}, assignment={}, user={}, score={}",
                    submissionId, assignmentId, userId, score);

        } catch (org.springframework.web.client.RestClientException e) {
            if (e.getMessage() != null && e.getMessage().contains("204")) {
                log.info("No pending submissions (204).");
            } else {
                log.error("EvaluatorMock run failed: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("EvaluatorMock run failed: {}", e.getMessage(), e);
        }
    }

    private static long asLong(Object n) {
        return (n instanceof Number num) ? num.longValue() : Long.parseLong(String.valueOf(n));
    }
}