package org.herb.service.impl;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.zhipu.oapi.ClientV4;
import org.herb.utils.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.reactivex.Flowable;
import org.herb.service.AiService;
import org.herb.service.DiagnosisHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.zhipu.oapi.service.v4.api.ChatApiService.defaultObjectMapper;

@Service
public class AiServiceImpl implements AiService {

    @Autowired
    private DiagnosisHistoryService diagnosisHistoryService;

    private static final String API_KEY = "77ac40f6a6004646825d2561dcf9719d.g60df54MeiEE6Nkj";
    private static final ClientV4 client = new ClientV4.Builder(API_KEY).build();
    private static final ObjectMapper mapper = defaultObjectMapper();
    private static final String requestIdTemplate = "mycompany-%d";
    
    private static final ExecutorService executorService = Executors.newCachedThreadPool();

    public static ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        mapper.addMixIn(ChatFunction.class, ChatFunctionMixIn.class);
        mapper.addMixIn(ChatCompletionRequest.class, ChatCompletionRequestMixIn.class);
        mapper.addMixIn(ChatFunctionCall.class, ChatFunctionCallMixIn.class);
        return mapper;
    }

    private String sseInvoke(String prompt) {
        StringBuilder builder = new StringBuilder();
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);
        messages.add(chatMessage);
        String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4Flash)
                .stream(Boolean.TRUE)
                .messages(messages)
                .requestId(requestId)
                .build();
        
        ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
        if (!sseModelApiResp.isSuccess()) {
            return "抱歉，服务暂时不可用，请稍后再试。";
        }
        if (sseModelApiResp.isSuccess()) {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            ChatMessageAccumulator chatMessageAccumulator = mapStreamToAccumulator(sseModelApiResp.getFlowable())
                    .doOnNext(accumulator -> {
                        {
                            if (isFirst.getAndSet(false)) {
                                System.out.print("Response: ");
                            }
                            if (accumulator.getDelta() != null && accumulator.getDelta().getTool_calls() != null) {
                                String jsonString = mapper.writeValueAsString(accumulator.getDelta().getTool_calls());
                                System.out.println("tool_calls: " + jsonString);
                            }
                            if (accumulator.getDelta() != null && accumulator.getDelta().getContent() != null) {
                                System.out.print(accumulator.getDelta().getContent());
                                builder.append(accumulator.getDelta().getContent());
                            }
                        }
                    })
                    .doOnComplete(System.out::println)
                    .lastElement()
                    .blockingGet();

            Choice choice = new Choice(chatMessageAccumulator.getChoice().getFinishReason(), 0L, chatMessageAccumulator.getDelta());
            List<Choice> choices = new ArrayList<>();
            choices.add(choice);
            ModelData data = new ModelData();
            data.setChoices(choices);
            data.setUsage(chatMessageAccumulator.getUsage());
            data.setId(chatMessageAccumulator.getId());
            data.setCreated(chatMessageAccumulator.getCreated());
            data.setRequestId(chatCompletionRequest.getRequestId());
            sseModelApiResp.setFlowable(null);
            sseModelApiResp.setData(data);
        }
        String answer = builder.toString();
        return answer;
    }

    private SseEmitter createSseEmitter(String prompt, Integer userId, String type, String inputForHistory) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        
        executorService.execute(() -> {
            try {
                List<ChatMessage> messages = new ArrayList<>();
                ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), prompt);
                messages.add(chatMessage);
                String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model(Constants.ModelChatGLM4Flash)
                        .stream(Boolean.TRUE)
                        .messages(messages)
                        .requestId(requestId)
                        .build();

                ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
                
                if (!sseModelApiResp.isSuccess()) {
                    emitter.send(SseEmitter.event().data("抱歉，服务暂时不可用，请稍后再试。"));
                    emitter.complete();
                    return;
                }

                Flowable<ChatMessageAccumulator> flowable = mapStreamToAccumulator(sseModelApiResp.getFlowable());
                
                flowable.doOnNext(accumulator -> {
                    if (accumulator.getDelta() != null && accumulator.getDelta().getContent() != null) {
                        String content = accumulator.getDelta().getContent();
                        fullResponse.updateAndGet(v -> v + content);
                        emitter.send(SseEmitter.event().data(content));
                    }
                })
                .doOnComplete(() -> {
                    diagnosisHistoryService.addDiagnosisHistory(userId, type, inputForHistory, fullResponse.get());
                    emitter.send(SseEmitter.event().name("complete").data("[DONE]"));
                    emitter.complete();
                })
                .doOnError(error -> {
                    emitter.send(SseEmitter.event().data("发生错误: " + error.getMessage()));
                    emitter.completeWithError(error);
                })
                .blockingSubscribe();
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("发生错误: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        emitter.onCompletion(() -> System.out.println("SSE completed"));
        emitter.onTimeout(() -> {
            System.out.println("SSE timeout");
            emitter.complete();
        });
        emitter.onError(e -> System.out.println("SSE error: " + e.getMessage()));
        
        return emitter;
    }

    public static Flowable<ChatMessageAccumulator> mapStreamToAccumulator(Flowable<ModelData> flowable) {
        return flowable.map(chunk -> {
            return new ChatMessageAccumulator(chunk.getChoices().get(0).getDelta(), null, chunk.getChoices().get(0), chunk.getUsage(), chunk.getCreated(), chunk.getId());
        });
    }

    @Override
    public String herbQA(Integer userId, String herbName, String question) {
        String prompt = "你是一位经验丰富的中医药专家。关于中药\"" + herbName + "\"，" + question +
                       "请从中医专业角度详细解答，包括其性味归经、功效主治、用法用量、注意事项等方面。";
        String answer = sseInvoke(prompt);
        diagnosisHistoryService.addDiagnosisHistory(userId, "herb_qa", herbName + ": " + question, answer);
        return answer;
    }

    @Override
    public String prescriptionAnalysis(Integer userId, String prescriptionData) {
        String prompt = "你是一位资深的中医方剂学专家。请分析以下方剂信息：\n" + prescriptionData +
                       "\n请从以下几个方面进行分析：\n" +
                       "1. 方剂组成分析\n" +
                       "2. 配伍意义\n" +
                       "3. 功效与主治\n" +
                       "4. 配伍禁忌\n" +
                       "5. 使用注意事项";
        String answer = sseInvoke(prompt);
        diagnosisHistoryService.addDiagnosisHistory(userId, "prescription_analysis", prescriptionData, answer);
        return answer;
    }

    @Override
    public String diagnosis(Integer userId, String symptoms) {
        String prompt = "你是一位经验丰富的中医师。患者描述的症状如下：\n" + symptoms +
                       "\n请基于中医理论进行辨证分析，包括：\n" +
                       "1. 病因分析\n" +
                       "2. 病机分析\n" +
                       "3. 辨证结论\n" +
                       "4. 治疗原则\n" +
                       "5. 推荐方药\n" +
                       "6. 生活调养建议\n\n" +
                       "注意：这只是初步的辨证分析，建议患者到正规中医院就诊。";
        String answer = sseInvoke(prompt);
        diagnosisHistoryService.addDiagnosisHistory(userId, "diagnosis", symptoms, answer);
        return answer;
    }

    @Override
    public SseEmitter herbQAStream(Integer userId, String herbName, String question) {
        String prompt = "你是一位经验丰富的中医药专家。关于中药\"" + herbName + "\"，" + question +
                       "请从中医专业角度详细解答，包括其性味归经、功效主治、用法用量、注意事项等方面。";
        return createSseEmitter(prompt, userId, "herb_qa", herbName + ": " + question);
    }

    @Override
    public SseEmitter prescriptionAnalysisStream(Integer userId, String prescriptionData) {
        String prompt = "你是一位资深的中医方剂学专家。请分析以下方剂信息：\n" + prescriptionData +
                       "\n请从以下几个方面进行分析：\n" +
                       "1. 方剂组成分析\n" +
                       "2. 配伍意义\n" +
                       "3. 功效与主治\n" +
                       "4. 配伍禁忌\n" +
                       "5. 使用注意事项";
        return createSseEmitter(prompt, userId, "prescription_analysis", prescriptionData);
    }

    @Override
    public SseEmitter diagnosisStream(Integer userId, String symptoms) {
        String prompt = "你是一位经验丰富的中医师。患者描述的症状如下：\n" + symptoms +
                       "\n请基于中医理论进行辨证分析，包括：\n" +
                       "1. 病因分析\n" +
                       "2. 病机分析\n" +
                       "3. 辨证结论\n" +
                       "4. 治疗原则\n" +
                       "5. 推荐方药\n" +
                       "6. 生活调养建议\n\n" +
                       "注意：这只是初步的辨证分析，建议患者到正规中医院就诊。";
        return createSseEmitter(prompt, userId, "diagnosis", symptoms);
    }

    @Override
    public String herbRecognition(Integer userId, String imageUrl) {
        StringBuilder builder = new StringBuilder();
        String prompt = "你是一位经验丰富的中草药鉴定专家。请仔细观察这张图片，识别图中的花草或药材，并从以下方面给出专业科普信息：\n\n" +
                       "**1. 名称识别**\n" +
                       "- 中文名称\n" +
                       "- 学名（拉丁名）\n" +
                       "- 别名/俗称\n\n" +
                       "**2. 基本信息**\n" +
                       "- 科属分类\n" +
                       "- 产地分布\n" +
                       "- 生长环境\n\n" +
                       "**3. 药用价值**\n" +
                       "- 性味归经\n" +
                       "- 功效主治\n" +
                       "- 用法用量\n\n" +
                       "**4. 使用禁忌**\n" +
                       "- 禁忌人群\n" +
                       "- 不良反应\n" +
                       "- 配伍禁忌\n\n" +
                       "**5. 科普知识**\n" +
                       "- 民间传说或历史故事\n" +
                       "- 现代研究进展\n\n" +
                       "注意：如果图片不够清晰或无法确定是哪种植物，请说明不确定的原因，并给出可能的候选植物。同时提醒用户，AI识别结果仅供参考，使用中草药前请咨询专业医师。";
        
        List<ChatMessage> messages = new ArrayList<>();
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);
        contentList.add(textContent);
        
        Map<String, Object> imageUrlObj = new HashMap<>();
        imageUrlObj.put("url", imageUrl);
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrlObj);
        contentList.add(imageContent);
        
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), contentList);
        messages.add(chatMessage);
        String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4VFlash)
                .stream(Boolean.FALSE)
                .messages(messages)
                .requestId(requestId)
                .build();
        
        ModelApiResponse modelApiResponse = client.invokeModelApi(chatCompletionRequest);
        if (modelApiResponse.isSuccess() && modelApiResponse.getData() != null) {
            List<Choice> choices = modelApiResponse.getData().getChoices();
            if (choices != null && !choices.isEmpty()) {
                ChatMessage message = choices.get(0).getMessage();
                if (message != null && message.getContent() != null) {
                    builder.append(message.getContent());
                }
            }
        }
        
        if (builder.length() == 0) {
            builder.append("抱歉，图片识别失败，请确保图片清晰且包含完整的花草或药材。");
        }
        
        String answer = builder.toString();
        diagnosisHistoryService.addDiagnosisHistory(userId, "herb_recognition", "中草药图片识别", answer);
        return answer;
    }

    @Override
    public SseEmitter herbRecognitionStream(Integer userId, String imageUrl) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        
        executorService.execute(() -> {
            try {
                String prompt = "你是一位经验丰富的中草药鉴定专家。请仔细观察这张图片，识别图中的花草或药材，并从以下方面给出专业科普信息：\n\n" +
                               "**1. 名称识别**\n" +
                               "- 中文名称\n" +
                               "- 学名（拉丁名）\n" +
                               "- 别名/俗称\n\n" +
                               "**2. 基本信息**\n" +
                               "- 科属分类\n" +
                               "- 产地分布\n" +
                               "- 生长环境\n\n" +
                               "**3. 药用价值**\n" +
                               "- 性味归经\n" +
                               "- 功效主治\n" +
                               "- 用法用量\n\n" +
                               "**4. 使用禁忌**\n" +
                               "- 禁忌人群\n" +
                               "- 不良反应\n" +
                               "- 配伍禁忌\n\n" +
                               "**5. 科普知识**\n" +
                               "- 民间传说或历史故事\n" +
                               "- 现代研究进展\n\n" +
                               "注意：如果图片不够清晰或无法确定是哪种植物，请说明不确定的原因，并给出可能的候选植物。同时提醒用户，AI识别结果仅供参考，使用中草药前请咨询专业医师。";
                
                List<ChatMessage> messages = new ArrayList<>();
                List<Map<String, Object>> contentList = new ArrayList<>();
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", prompt);
                contentList.add(textContent);
                
                Map<String, Object> imageUrlObj = new HashMap<>();
                imageUrlObj.put("url", imageUrl);
                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "image_url");
                imageContent.put("image_url", imageUrlObj);
                contentList.add(imageContent);
                
                ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), contentList);
                messages.add(chatMessage);
                String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model(Constants.ModelChatGLM4VFlash)
                        .stream(Boolean.TRUE)
                        .messages(messages)
                        .requestId(requestId)
                        .build();

                ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
                
                if (!sseModelApiResp.isSuccess()) {
                    emitter.send(SseEmitter.event().data("抱歉，服务暂时不可用，请稍后再试。"));
                    emitter.complete();
                    return;
                }

                Flowable<ChatMessageAccumulator> flowable = mapStreamToAccumulator(sseModelApiResp.getFlowable());
                
                flowable.doOnNext(accumulator -> {
                    if (accumulator.getDelta() != null && accumulator.getDelta().getContent() != null) {
                        String content = accumulator.getDelta().getContent();
                        fullResponse.updateAndGet(v -> v + content);
                        emitter.send(SseEmitter.event().data(content));
                    }
                })
                .doOnComplete(() -> {
                    diagnosisHistoryService.addDiagnosisHistory(userId, "herb_recognition", "中草药图片识别", fullResponse.get());
                    emitter.send(SseEmitter.event().name("complete").data("[DONE]"));
                    emitter.complete();
                })
                .doOnError(error -> {
                    emitter.send(SseEmitter.event().data("发生错误: " + error.getMessage()));
                    emitter.completeWithError(error);
                })
                .blockingSubscribe();
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("发生错误: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        emitter.onCompletion(() -> System.out.println("SSE completed"));
        emitter.onTimeout(() -> {
            System.out.println("SSE timeout");
            emitter.complete();
        });
        emitter.onError(e -> System.out.println("SSE error: " + e.getMessage()));
        
        return emitter;
    }

    @Override
    public SseEmitter tongueDiagnosisStream(Integer userId, String imageUrl) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        
        executorService.execute(() -> {
            try {
                String prompt = "你是一位经验丰富的中医舌诊专家，精通中医体质学说。请仔细观察这张舌部照片，进行全面的舌诊分析，并按照以下结构给出专业、详细的分析报告：\n\n" +
                               "**一、舌象特征分析**\n\n" +
                               "**舌色**：观察舌体的颜色（如淡白、红、绛、紫等），分析其代表的病理意义\n\n" +
                               "**舌形**：观察舌体的形态（如胖大、瘦薄、齿痕、裂纹、点刺等），分析其代表的病理意义\n\n" +
                               "**舌苔**：观察舌苔的颜色（白、黄、灰、黑）和质地（薄、厚、腻、燥、滑等），分析其代表的病理意义\n\n" +
                               "**舌下脉络**：观察舌下静脉的形态和颜色变化\n\n" +
                               "**二、体质辨识结论**\n\n" +
                               "根据舌象特征，判断属于以下哪种中医体质类型（可多种并存）：\n\n" +
                               "1. **平和质**：健康体质，阴阳平衡\n" +
                               "2. **气虚质**：元气不足，易疲乏、气短、自汗\n" +
                               "3. **阳虚质**：阳气不足，怕冷、四肢不温\n" +
                               "4. **阴虚质**：阴液亏少，手足心热、口燥咽干\n" +
                               "5. **痰湿质**：痰湿凝聚，形体肥胖、腹部肥满\n" +
                               "6. **湿热质**：湿热内蕴，面垢油光、口苦苔黄腻\n" +
                               "7. **血瘀质**：血行不畅，肤色晦暗、舌质紫暗\n" +
                               "8. **气郁质**：气机郁滞，神情抑郁、忧虑脆弱\n" +
                               "9. **特禀质**：先天失常，易过敏\n\n" +
                               "**三、个性化养生方案**\n\n" +
                               "**饮食调养**：\n" +
                               "- 推荐食材（性味、功效、食用方法）\n" +
                               "- 忌口食物及原因\n\n" +
                               "**茶饮方推荐**：\n" +
                               "- 推荐1-2个适合该体质的代茶饮方\n" +
                               "- 每个方子包含：组成、用量、泡法、功效、适用症状\n\n" +
                               "**穴位按摩建议**：\n" +
                               "- 推荐3-5个关键穴位\n" +
                               "- 每个穴位包含：定位方法、按摩手法、时间频率、功效说明\n\n" +
                               "**起居调摄**：\n" +
                               "- 作息建议\n" +
                               "- 运动建议（推荐运动类型、强度、时间）\n" +
                               "- 情志调养方法\n\n" +
                               "**四、温馨提示**\n\n" +
                               "- 日常注意事项\n" +
                               "- 何时需要就医\n\n" +
                               "注意：请用通俗易懂的语言解释专业术语，让非专业人士也能理解。舌诊结果仅供参考，如有不适请及时就医咨询专业中医师。";
                
                List<ChatMessage> messages = new ArrayList<>();
                List<Map<String, Object>> contentList = new ArrayList<>();
                Map<String, Object> textContent = new HashMap<>();
                textContent.put("type", "text");
                textContent.put("text", prompt);
                contentList.add(textContent);
                
                Map<String, Object> imageUrlObj = new HashMap<>();
                imageUrlObj.put("url", imageUrl);
                Map<String, Object> imageContent = new HashMap<>();
                imageContent.put("type", "image_url");
                imageContent.put("image_url", imageUrlObj);
                contentList.add(imageContent);
                
                ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), contentList);
                messages.add(chatMessage);
                String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

                ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                        .model(Constants.ModelChatGLM4VFlash)
                        .stream(Boolean.TRUE)
                        .messages(messages)
                        .requestId(requestId)
                        .build();

                ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
                
                if (!sseModelApiResp.isSuccess()) {
                    emitter.send(SseEmitter.event().data("抱歉，服务暂时不可用，请稍后再试。"));
                    emitter.complete();
                    return;
                }

                Flowable<ChatMessageAccumulator> flowable = mapStreamToAccumulator(sseModelApiResp.getFlowable());
                
                flowable.doOnNext(accumulator -> {
                    if (accumulator.getDelta() != null && accumulator.getDelta().getContent() != null) {
                        String content = accumulator.getDelta().getContent();
                        fullResponse.updateAndGet(v -> v + content);
                        emitter.send(SseEmitter.event().data(content));
                    }
                })
                .doOnComplete(() -> {
                    diagnosisHistoryService.addDiagnosisHistory(userId, "tongue_diagnosis", "舌部照片", fullResponse.get());
                    emitter.send(SseEmitter.event().name("complete").data("[DONE]"));
                    emitter.complete();
                })
                .doOnError(error -> {
                    emitter.send(SseEmitter.event().data("发生错误: " + error.getMessage()));
                    emitter.completeWithError(error);
                })
                .blockingSubscribe();
                
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("发生错误: " + e.getMessage()));
                    emitter.completeWithError(e);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
        
        emitter.onCompletion(() -> System.out.println("SSE completed"));
        emitter.onTimeout(() -> {
            System.out.println("SSE timeout");
            emitter.complete();
        });
        emitter.onError(e -> System.out.println("SSE error: " + e.getMessage()));
        
        return emitter;
    }
}
