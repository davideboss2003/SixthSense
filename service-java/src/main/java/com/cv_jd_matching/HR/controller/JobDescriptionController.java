package com.cv_jd_matching.HR.controller;

import com.cv_jd_matching.HR.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping(value = "/api/jobDescription")
@CrossOrigin(origins = "http://localhost:4200")
public class JobDescriptionController {


    @Autowired
    private BlobServiceClient blobServiceClient;

    @Autowired
    private JobDescriptionService jobDescriptionService;

    @Autowired
    private JobDescriptionServiceImpl jobDescriptionServiceImpl;

    @Autowired
    private MatchingClient matchingClient;

    @Autowired
    private CvService cvService;

    @CrossOrigin(origins = "http://localhost:4200") // Allow requests from http://localhost:4200
    @RequestMapping(method = RequestMethod.GET, value="/all")
    public ResponseEntity<?> displayAllJobDescriptions(){
        return new ResponseEntity<>(jobDescriptionService.getJobDescriptions(), HttpStatus.OK);
    }


  @CrossOrigin(origins = "http://localhost:4200") // Allow requests from http://localhost:4200
    @RequestMapping(method = RequestMethod.DELETE, value = "/delete")
    public ResponseEntity<?> deleteSelectedJobDescriptions(@RequestBody List<Integer> ids){
        jobDescriptionService.deleteJobDescription(ids);
        return new ResponseEntity<>("Successfully deleted files", HttpStatus.OK);
    }


}
