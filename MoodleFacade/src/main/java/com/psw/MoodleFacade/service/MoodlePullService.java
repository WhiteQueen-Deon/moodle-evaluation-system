package com.psw.MoodleFacade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psw.MoodleFacade.domain.Submission;
import com.psw.MoodleFacade.domain.SubmissionFile;
import com.psw.MoodleFacade.domain.SubmissionStatus;
import com.psw.MoodleFacade.repository.SubmissionRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.springframework.dao.DataIntegrityViolationException;

@Service
@Slf4j
public class MoodlePullService {

    @Autowired
    private SubmissionRepository submissionRepository;

    private final WebClient webClient;
    private final String token;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    public static record FileRef(String filename, String url) {}

    public MoodlePullService(WebClient moodleClient,
                             @Value("${moodle.token}") String token) {
        this.webClient = moodleClient;
        this.token = token;
    }

    @Transactional
    public int syncSubmissionsFromMoodle(long assignmentId, String facadeBaseUrl) {
        List<Map<String, Object>> moodleSubmissions = listSubmissionsForSolution(assignmentId, facadeBaseUrl);
        if (moodleSubmissions.isEmpty()) return 0;
        
        List<String> keys = moodleSubmissions.stream()
            .map(ms -> ms.get("submissionId") + "-" + ms.get("assignmentId"))
            .toList();
        
        Set<String> existingKeys = submissionRepository.findExistingSubmissionKeys(keys);
        
        List<Submission> newSubmissions = moodleSubmissions.stream()
            .filter(ms -> !existingKeys.contains(ms.get("submissionId") + "-" + ms.get("assignmentId")))
            .map(ms -> {
                try {
                    Submission sub = new Submission();
                    sub.setSubmissionId(((Number) ms.get("submissionId")).longValue());
                    sub.setAssignmentId(((Number) ms.get("assignmentId")).longValue());
                    sub.setUserId(((Number) ms.get("userId")).longValue());
                    sub.setSubmittedAt((String) ms.get("submittedAt"));
                    sub.setStatus(SubmissionStatus.PENDING);
                    
                    // Download files and store content directly
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> fileDtos = (List<Map<String, Object>>) ms.get("files");
                    String mainFileContent = "";
                    
                    if (fileDtos != null && !fileDtos.isEmpty()) {
                        for (Map<String, Object> fileDto : fileDtos) {
                            try {
                                String fetchUrl = (String) fileDto.get("fetchUrl");
                                String filename = (String) fileDto.get("filename");
                                
                                if (fetchUrl != null && filename != null) {
                                    long submissionId = sub.getSubmissionId();
                                    int index = extractIndexFromFetchUrl(fetchUrl);
                                    
                                    FileRef fileRef = findFileByIndex(ms.get("assignmentId") instanceof Number 
                                            ? ((Number) ms.get("assignmentId")).longValue() 
                                            : Long.parseLong(ms.get("assignmentId").toString()), 
                                            submissionId, index);
                                    if (fileRef != null) {
                                        byte[] fileContent = downloadBytes(fileRef.url());
                                        String contentStr = Base64.getEncoder().encodeToString(fileContent);
                                        
                                        // Store the first file's content as the main code
                                        if (mainFileContent.isEmpty()) {
                                            mainFileContent = contentStr;
                                        }
                                        
                                        // Also save to SubmissionFile entity for backup
                                        SubmissionFile submissionFile = new SubmissionFile();
                                        submissionFile.setSubmission(sub);
                                        submissionFile.setFilename(filename);
                                        submissionFile.setContent(fileContent);
                                        submissionFile.setFileSize((long) fileContent.length);
                                        submissionFile.setMimeType(detectMimeType(filename));
                                        sub.getSubmissionFiles().add(submissionFile);
                                        
                                        log.info("Downloaded file: {} ({} bytes)", filename, fileContent.length);
                                    }
                                }
                            } catch (Exception e) {
                                log.error("Failed to download file for submission {}: {}", sub.getSubmissionId(), e.getMessage());
                            }
                        }
                    }
                    
                    sub.setFiles(mainFileContent);
                    log.info("Submission {} files field set with {} characters of code", 
                             sub.getSubmissionId(), mainFileContent.length());
                    
                    return sub;
                } catch (Exception e) {
                    log.error("Failed to process submission: {}", e.getMessage());
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
        
        if (!newSubmissions.isEmpty()) {
            int saved = 0;
            for (Submission sub : newSubmissions) {
                try {
                    submissionRepository.save(sub);
                    saved++;
                } catch (DataIntegrityViolationException e) {
                    // Another thread already inserted this submission - this is OK
                    log.debug("Submission already exists (race condition): submissionId={}, assignmentId={}", 
                             sub.getSubmissionId(), sub.getAssignmentId());
                }
            }
            if (saved > 0) {
                log.info("Saved {} new submissions with files to database", saved);
            }
        }
        
        return newSubmissions.size();
    }
    
    private int extractIndexFromFetchUrl(String fetchUrl) {
        try {
            // Extract index parameter from URL like: ".../file?assignmentId=123&index=0"
            String[] parts = fetchUrl.split("index=");
            if (parts.length > 1) {
                String indexPart = parts[1].split("&")[0];
                return Integer.parseInt(indexPart);
            }
        } catch (Exception e) {
            log.warn("Failed to extract index from fetchUrl: {}", fetchUrl);
        }
        return 0;
    }
    
    private String detectMimeType(String filename) {
        if (filename == null) return "application/octet-stream";
        
        String lower = filename.toLowerCase();
        if (lower.endsWith(".java")) return "text/x-java-source";
        if (lower.endsWith(".py")) return "text/x-python";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc")) return "text/x-c++src";
        if (lower.endsWith(".c")) return "text/x-csrc";
        if (lower.endsWith(".js")) return "text/javascript";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        
        return "application/octet-stream";
    }

    public List<Map<String, Object>> listSubmissionsForSolution(long assignmentId, String facadeBaseUrl) {
        String raw = callGetSubmissionsRaw(assignmentId);
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode subs = mapper.readTree(raw).path("assignments").path(0).path("submissions");
            if (!subs.isArray()) return out;

            for (JsonNode sub : subs) {
                String status = sub.path("status").asText("");
                String gradingstatus = sub.path("gradingstatus").asText("");
                
                if ("graded".equals(gradingstatus)) continue;
                if (!"submitted".equals(status)) continue;

                long submissionId = sub.path("id").asLong();
                long userId = sub.path("userid").asLong();
                String submittedAt = asIsoUtc(sub);

                List<FileRef> files = allFiles(sub);
                List<Map<String, Object>> fileDtos = new ArrayList<>();
                for (int i = 0; i < files.size(); i++) {
                    FileRef fr = files.get(i);
                    String fetchUrl = String.format(
                            "%s/moodle/submissions/%d/file?assignmentId=%d&index=%d",
                            facadeBaseUrl, submissionId, assignmentId, i
                    );
                    fileDtos.add(Map.of("filename", fr.filename(), "fetchUrl", fetchUrl));
                }

                out.add(Map.of(
                    "submissionId", submissionId,
                    "assignmentId", assignmentId,
                    "userId", userId,
                    "submittedAt", submittedAt,
                    "files", fileDtos
                ));
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse submissions", e);
        }
    }

    private List<FileRef> allFiles(JsonNode submission) {
        List<FileRef> list = new ArrayList<>();
        JsonNode plugins = submission.path("plugins");
        if (!plugins.isArray()) return list;
        
        for (JsonNode p : plugins) {
            JsonNode fileareas = p.path("fileareas");
            if (!fileareas.isArray()) continue;
            
            for (JsonNode fa : fileareas) {
                JsonNode files = fa.path("files");
                if (!files.isArray() || files.isEmpty()) continue;
                
                for (JsonNode f : files) {
                    String filename = f.path("filename").asText(null);
                    String fileurl  = f.path("fileurl").asText(null);
                    if (fileurl == null) continue;
                    
                    String full = fileurl + (fileurl.contains("?") ? "&" : "?") + "token=" + token;
                    list.add(new FileRef(filename, full));
                }
            }
        }
        
        return list;
    }

    public FileRef findFileByIndex(long assignmentId, long submissionId, int index) {
        String raw = callGetSubmissionsRaw(assignmentId);
        try {
            JsonNode subs = mapper.readTree(raw).path("assignments").path(0).path("submissions");
            if (!subs.isArray()) return null;
            
            for (JsonNode sub : subs) {
                if (sub.path("id").asLong() != submissionId) continue;
                
                List<FileRef> all = allFiles(sub);
                if (index < 0 || index >= all.size()) return null;
                
                return all.get(index);
            }
            
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to locate file by index", e);
        }
    }
    public byte[] downloadBytes(String fullUrlWithToken) {
        try {
            return webClient.get()
                    .uri(java.net.URI.create(fullUrlWithToken))
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to download file from Moodle", e);
            throw new RuntimeException("Failed to download file", e);
        }
    }

    private String callGetSubmissionsRaw(long assignmentId) {
        try {
            return webClient.get()
                    .uri(uri -> uri
                            .queryParam("wstoken", token)
                            .queryParam("wsfunction", "mod_assign_get_submissions")
                            .queryParam("moodlewsrestformat", "json")
                            .queryParam("assignmentids[0]", assignmentId)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to call Moodle API", e);
            throw new RuntimeException("Failed to call Moodle API", e);
        }
    }

    private String asIsoUtc(JsonNode submission) {
        long t = submission.path("timecreated").asLong(0L);
        if (t == 0L) t = submission.path("timemodified").asLong(0L);
        if (t <= 0L) return null;
        return ISO.format(Instant.ofEpochSecond(t).atOffset(ZoneOffset.UTC));
    }
}