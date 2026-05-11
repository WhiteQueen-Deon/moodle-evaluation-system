package com.psw.MoodleFacade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.psw.MoodleFacade.dto.EvaluationResultDTO;

import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.MultipartBodyBuilder;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.List;

@Service
@Slf4j
public class MoodlePushService {

    private final WebClient web;
    private final WebClient uploadClient;
    private final String token;

    public MoodlePushService(WebClient moodleClient,
                             @Qualifier("moodleUploadClient") WebClient uploadClient,
                             @Value("${moodle.token}") String token) {
        this.web = moodleClient;
        this.uploadClient = uploadClient;
        this.token = token;
    }

    public void saveGradeToMoodle(long assignmentId, long userId, double score, String feedback, List<EvaluationResultDTO.ResultFile> resultFiles) {
        Long draftItemId = null;
        
        if (resultFiles != null && !resultFiles.isEmpty()) {
            try {
                draftItemId = uploadReportFiles(resultFiles);
                log.info("Uploaded report file, draftItemId={}", draftItemId);
                log.info("Uploaded report file to draft area: itemId={}", draftItemId);
            } catch (Exception e) {
                log.error("Failed to upload report file, continuing without attachment", e);
            }
        }
        
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("wstoken", token);
        form.add("wsfunction", "mod_assign_save_grade");
        form.add("moodlewsrestformat", "json");

        form.add("assignmentid", String.valueOf(assignmentId));
        form.add("userid", String.valueOf(userId));
        form.add("grade", String.valueOf(score));
        form.add("attemptnumber", "0");
        form.add("addattempt", "0");
        form.add("workflowstate", "");
        form.add("applytoall", "0");;

        if (feedback != null && !feedback.isBlank()) {
            form.add("plugindata[assignfeedbackcomments_editor][text]", feedback);
            form.add("plugindata[assignfeedbackcomments_editor][format]", "1"); // 1 = HTML
        }
        
        if (draftItemId != null) {
            form.add("plugindata[files_filemanager]", String.valueOf(draftItemId));
            log.info("Adding feedback file with draftItemId={} for user={}", draftItemId, userId);
        } else {
            // Must be zero when not used
            form.add("plugindata[files_filemanager]", "0");
}

        log.info("Sending grade to Moodle: assignmentId={}, userId={}, score={}, feedback={}", 
                 assignmentId, userId, score, feedback);
        log.info("Form data keys: {}", form.keySet());
        
        try {
            String response = web.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.info("Moodle save_grades response: '{}'", response);
            // A successful response is "null"
            if (response != null && response.contains("exception")) {
                log.error("Moodle returned an error: {}", response);
                throw new RuntimeException("Moodle API error: " + response);
            }
            
            if (response == null || response.equals("null") || response.trim().isEmpty()) {
                log.info("Grade saved successfully to Moodle (null response indicates success)");
            }
        } catch (WebClientResponseException e) {
            log.error("Moodle API HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Moodle API HTTP error: " + e.getMessage(), e);
        }
    }
    private Long uploadReportFiles(List<EvaluationResultDTO.ResultFile> resultFiles) {
        Long draftItemId = null;
        for (int i = 0; i < resultFiles.size(); i++) {
            String filename = resultFiles.get(i).getFileName();
            byte[] content = resultFiles.get(i).getContent().getBytes(StandardCharsets.UTF_8);
            draftItemId = uploadSingleFile(filename, content, draftItemId);
        }
        return draftItemId;
    }

    private Long uploadSingleFile(String filename, byte[] content, Long existingItemId) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("token", token);
        builder.part("filearea", "draft");
        
        // If we have an existing itemId, add to that draft area; otherwise create new (itemid=0)
        builder.part("itemid", existingItemId != null ? String.valueOf(existingItemId) : "0");
        
        builder.part("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }).filename(filename);
        
        log.debug("Uploading file '{}' ({} bytes) to draft area, existingItemId={}", 
                  filename, content.length, existingItemId);
        
        String response = uploadClient
                .post()
                .uri("/")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        
        log.debug("Upload response for '{}': {}", filename, response);
        
        if (response != null && response.contains("exception")) {
            throw new RuntimeException("Moodle upload error: " + response);
        }
        
        // Parse response to get itemid
        if (response != null && response.contains("itemid")) {
            int itemIdStart = response.indexOf("\"itemid\":") + 9;
            int itemIdEnd = response.indexOf(",", itemIdStart);
            if (itemIdEnd == -1) {
                itemIdEnd = response.indexOf("}", itemIdStart);
            }
            String itemIdStr = response.substring(itemIdStart, itemIdEnd).trim();
            return Long.parseLong(itemIdStr);
        }
        
        throw new RuntimeException("Failed to parse itemid from upload response: " + response);
    }
}