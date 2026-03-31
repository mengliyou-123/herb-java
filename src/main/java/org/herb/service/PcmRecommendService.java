package org.herb.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface PcmRecommendService {
    String recommend(String question);
    SseEmitter recommendStream(String question);
}
