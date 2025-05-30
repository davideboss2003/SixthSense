package com.cv_jd_matching.HR.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvMatchResponseDTO {
    private float score;
    private Map<String, String> explanation;
    private CvViewDTO cvViewDTO;
}
