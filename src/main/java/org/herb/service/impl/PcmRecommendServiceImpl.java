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
import org.herb.service.PcmRecommendService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.zhipu.oapi.service.v4.api.ChatApiService.defaultObjectMapper;

@Service
public class PcmRecommendServiceImpl implements PcmRecommendService {

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

    private String sseInvoke(String question) {
        StringBuilder builder = new StringBuilder();
        String q = "请帮我推荐适合" + question + "的中成药";
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), q);
        messages.add(chatMessage);
        String requestId = String.format(requestIdTemplate, System.currentTimeMillis());

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4Flash)
                .stream(Boolean.TRUE)
                .messages(messages)
                .requestId(requestId)
                .build();
        
        System.out.println("请求参数: " + chatCompletionRequest);
        ModelApiResponse sseModelApiResp = client.invokeModelApi(chatCompletionRequest);
        System.out.println("响应状态: " + sseModelApiResp.isSuccess());
        if (!sseModelApiResp.isSuccess()) {
            System.err.println("API调用失败: " + sseModelApiResp.getMsg());
            System.err.println("错误码: " + sseModelApiResp.getCode());
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
        System.out.println("model output:" + JSON.toJSONString(sseModelApiResp));
        String answer = builder.toString();
        return answer;
    }

    public static Flowable<ChatMessageAccumulator> mapStreamToAccumulator(Flowable<ModelData> flowable) {
        return flowable.map(chunk -> {
            return new ChatMessageAccumulator(chunk.getChoices().get(0).getDelta(), null, chunk.getChoices().get(0), chunk.getUsage(), chunk.getCreated(), chunk.getId());
        });
    }

    @Override
    public String recommend(String question) {
        return sseInvoke(question);
    }

    @Override
    public SseEmitter recommendStream(String question) {
        SseEmitter emitter = new SseEmitter(180000L);
        AtomicReference<String> fullResponse = new AtomicReference<>("");
        
        executorService.execute(() -> {
            try {
                String q = "请帮我推荐适合" + question + "的中成药";
                List<ChatMessage> messages = new ArrayList<>();
                ChatMessage chatMessage = new ChatMessage(ChatMessageRole.USER.value(), q);
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
