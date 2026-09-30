package org.herb;

import com.zhipu.oapi.service.v4.api.ChatApiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiSdkCompatibilityTest {
    @Test
    void aiSdkInitializesWithApplicationJackson() {
        assertNotNull(ChatApiService.defaultObjectMapper());
    }
}
