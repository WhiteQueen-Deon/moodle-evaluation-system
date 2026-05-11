package com.psw.MoodleFacade.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class EvaluationResultDTO {
    @NotNull
    private Double score;
    
    private String feedback;

    private List<ResultFile> resultFiles;
    
    @Data
    public static class ResultFile {
        private String fileName;
        private String content;
        private Long fileSize;
    }
}