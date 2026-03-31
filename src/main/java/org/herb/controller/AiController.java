package org.herb.controller;

import org.herb.pojo.DiagnosisHistory;
import org.herb.pojo.Result;
import org.herb.service.AiService;
import org.herb.service.DiagnosisHistoryService;
import org.herb.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private DiagnosisHistoryService diagnosisHistoryService;

    @GetMapping("/herb-qa")
    public Result<String> herbQA(
            @RequestParam String herbName,
            @RequestParam String question) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String answer = aiService.herbQA(userId, herbName, question);
        return Result.success(answer);
    }

    @PostMapping("/prescription-analysis")
    public Result<String> prescriptionAnalysis(@RequestBody Map<String, String> params) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String prescriptionData = params.get("prescriptionData");
        String analysis = aiService.prescriptionAnalysis(userId, prescriptionData);
        return Result.success(analysis);
    }

    @PostMapping("/diagnosis")
    public Result<String> diagnosis(@RequestBody Map<String, String> params) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String symptoms = params.get("symptoms");
        String result = aiService.diagnosis(userId, symptoms);
        return Result.success(result);
    }

    @GetMapping(value = "/herb-qa-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter herbQAStream(
            @RequestParam String herbName,
            @RequestParam String question) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        return aiService.herbQAStream(userId, herbName, question);
    }

    @PostMapping(value = "/prescription-analysis-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter prescriptionAnalysisStream(@RequestBody Map<String, String> params) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String prescriptionData = params.get("prescriptionData");
        return aiService.prescriptionAnalysisStream(userId, prescriptionData);
    }

    @PostMapping(value = "/diagnosis-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter diagnosisStream(@RequestBody Map<String, String> params) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String symptoms = params.get("symptoms");
        return aiService.diagnosisStream(userId, symptoms);
    }

    @GetMapping("/history")
    public Result<List<DiagnosisHistory>> getHistory() {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        List<DiagnosisHistory> history = diagnosisHistoryService.getHistoryByUserId(userId);
        return Result.success(history);
    }

    @GetMapping("/history/type")
    public Result<List<DiagnosisHistory>> getHistoryByType(@RequestParam String type) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        List<DiagnosisHistory> history = diagnosisHistoryService.getHistoryByUserIdAndType(userId, type);
        return Result.success(history);
    }

    @DeleteMapping("/history/{id}")
    public Result<Void> deleteHistory(@PathVariable Integer id) {
        diagnosisHistoryService.deleteHistory(id);
        return Result.success();
    }
}
