package org.herb.controller;

import org.herb.pojo.Result;
import org.herb.service.PcmRecommendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/recommend")
public class PcmRecommendController {
    @Autowired
    private PcmRecommendService pcmRecommendService;

    @GetMapping
    public Result<String> recommend(@RequestParam String question) {
        validate(question);
        String result = pcmRecommendService.recommend(question);
        return Result.success(result);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter recommendStream(@RequestParam String question) {
        validate(question);
        return pcmRecommendService.recommendStream(question);
    }

    private void validate(String question) {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "输入长度不合规");
        }
    }
}
