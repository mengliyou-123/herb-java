package org.herb.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AiService {
    String herbQA(Integer userId, String herbName, String question);
    String prescriptionAnalysis(Integer userId, String prescriptionData);
    String diagnosis(Integer userId, String symptoms);
    String herbRecognition(Integer userId, String imageUrl);
    
    SseEmitter herbQAStream(Integer userId, String herbName, String question);
    SseEmitter prescriptionAnalysisStream(Integer userId, String prescriptionData);
    SseEmitter diagnosisStream(Integer userId, String symptoms);
    SseEmitter herbRecognitionStream(Integer userId, String imageUrl);
    SseEmitter tongueDiagnosisStream(Integer userId, String imageUrl);
}
