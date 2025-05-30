package com.cv_jd_matching.HR.service;

import com.cv_jd_matching.HR.config.WebClientConfig;
import com.cv_jd_matching.HR.dto.*;
import com.cv_jd_matching.HR.entity.Cv;
import com.cv_jd_matching.HR.entity.JobDescription;
import com.cv_jd_matching.HR.error.InputException;
import com.cv_jd_matching.HR.error.WrongWeightsException;
import com.cv_jd_matching.HR.mapper.CvMapper;
import com.cv_jd_matching.HR.mapper.JobDescriptionMapper;
import com.cv_jd_matching.HR.mapper.MatchResponseMapper;
import com.cv_jd_matching.HR.repository.ICvRepository;
import com.cv_jd_matching.HR.repository.IJobDescriptionRepository;
import com.cv_jd_matching.HR.util.validator.AdditionalSkillsValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class MatchingClientImpl implements MatchingClient{
    private final WebClient webClient;
    private final MatchResponseMapper matchResponseMapper;
    private final ICvRepository cvRepository;
    private final IJobDescriptionRepository jobDescriptionRepository;

    public Mono<MatchResponseDTO> match(Integer cvId, Integer jobDescriptionId){
        Optional<Cv> cv = cvRepository.findById(cvId);
        Optional<JobDescription> jobDescription = jobDescriptionRepository.findById(jobDescriptionId);
        if(cv.isEmpty() || jobDescription.isEmpty()){
            throw new RuntimeException("BAD MATCHING INPUT!");
        }
        CvDTO cvDTO = CvMapper.mapEntityToDTO(cv.get());
        JobDescriptionDTO jobDescriptionDTO = JobDescriptionMapper.mapEntityToDTO(jobDescription.get());

        Map<String, Integer> jobSkills = new HashMap<>();
        jobSkills.put("Python", 30);
        jobSkills.put("TensorFlow", 20);
        jobSkills.put("PyTorch", 20);
        jobSkills.put("Scikit-learn", 10);
        jobSkills.put("AWS", 10);
        jobSkills.put("Git", 10);

        MatchRequestDTO requestDTO = new MatchRequestDTO(cvId, jobDescriptionDTO, jobSkills);

        return webClient.post()
                .uri("/match/aux")
                .bodyValue(requestDTO)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToMono(MatchResponseDTO.class);
    }

    public Mono<String> embedJobDescription(JobDescriptionDTO jobDescriptionDTO){
        return webClient.post()
                .uri("/embed/jd")
                .bodyValue(jobDescriptionDTO)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }

    public Mono<String> embedCv(CvDTO cvDTO){
        return webClient.post()
                .uri("/embed/cv")
                .bodyValue(cvDTO)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }

    public List<JobMatchResponseDTO> matchCv(Integer cvId) {
        List<JobDescription> jobDescriptions = StreamSupport
                .stream(jobDescriptionRepository.findAll().spliterator(), false)
                .toList();
        CvMatchRequestDTO cvMatchRequestDTO = new CvMatchRequestDTO(cvId, jobDescriptions.stream().map(JobDescriptionMapper::mapEntityToDTO).toList());

        List<MatchResponseDTO> result =  webClient.post()
                .uri("/match/cv")
                .bodyValue(cvMatchRequestDTO)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToFlux(MatchResponseDTO.class)
                .collectList()
                .block();
        return result.stream()
                .map(matchResponseDTO -> {
                    JobDescription jobDescription = jobDescriptionRepository.findById(matchResponseDTO.getId())
                            .orElseThrow(() -> new RuntimeException(new InputException("The Job Description with id " + matchResponseDTO.getId() + " was not found.")));
                    JobDescriptionViewDTO jobDescriptionViewDTO = JobDescriptionMapper.mapEntityToViewDTO(jobDescription);
                    return MatchResponseMapper.mapMatchToJobDTO(matchResponseDTO, jobDescriptionViewDTO);
                })
                .collect(Collectors.toList());
    }

    public List<CvMatchResponseDTO> matchJobDescription(Integer jdId, Map<String, Integer> additionalSkills) throws WrongWeightsException, InputException {
        String error = AdditionalSkillsValidator.validateWeights(additionalSkills);
        if(error != null){
            throw new WrongWeightsException(error);
        }
        Optional<JobDescription> jobDescription = jobDescriptionRepository.findById(jdId);
        if(jobDescription.isEmpty()){
            throw new InputException("BAD MATCHING INPUT!");
        }
        JobDescriptionDTO jobDescriptionDTO = JobDescriptionMapper.mapEntityToDTO(jobDescription.get());
        JobMatchRequestDTO jobMatchRequestDTO = new JobMatchRequestDTO(jobDescriptionDTO, additionalSkills);
        List<MatchResponseDTO> result = webClient.post()
                .uri("/match/jd")
                .bodyValue(jobMatchRequestDTO)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToFlux(MatchResponseDTO.class)
                .collectList()
                .block();
        return result.stream()
                .map(matchResponseDTO -> {
                    Cv cv = cvRepository.findById(matchResponseDTO.getId())
                            .orElseThrow(() -> new RuntimeException(new InputException("The CV with id " + matchResponseDTO.getId() + " was not found.")));
                    CvViewDTO cvViewDTO = CvMapper.mapEntityToViewDTO(cv);
                    return MatchResponseMapper.mapMatchToCVDTO(matchResponseDTO, cvViewDTO);
                })
                .collect(Collectors.toList());
    }

    public Mono<String> deleteCv(Integer cvId) {
        return webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/delete/cv/{item_id}")
                        .build(cvId))
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }

    public Mono<String> deleteJobDescription(Integer jdId) {
        return webClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/delete/jd/{item_id}")
                        .build(jdId))
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Backend error: " + errorBody)))
                )
                .bodyToMono(String.class)
                .onErrorResume(e -> Mono.just("Error: " + e.getMessage()));
    }
}