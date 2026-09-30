package org.herb;

import org.herb.exception.ForbiddenException;
import org.herb.interceptors.LoginInterceptor;
import org.herb.controller.HerbController;
import org.herb.controller.AiController;
import org.herb.controller.UserController;
import org.herb.mapper.DiagnosisHistoryMapper;
import org.herb.mapper.PostMapper;
import org.herb.mapper.UserMapper;
import org.herb.pojo.Post;
import org.herb.pojo.User;
import org.herb.service.impl.DiagnosisHistoryServiceImpl;
import org.herb.service.impl.PostServiceImpl;
import org.herb.service.impl.UserServiceImpl;
import org.herb.service.UserService;
import org.herb.service.SessionService;
import org.herb.utils.JwtUtil;
import org.herb.utils.Md5Util;
import org.herb.utils.PasswordUtil;
import org.herb.utils.ThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityHardeningTest {
    @AfterEach
    void clearIdentity() {
        ThreadLocalUtil.remove();
    }

    @Test
    void passwordHashesAreSaltedAndLegacyHashesCanMigrate() {
        String first = PasswordUtil.hash("correct horse battery staple");
        String second = PasswordUtil.hash("correct horse battery staple");
        assertNotEquals(first, second);
        assertTrue(PasswordUtil.matches("correct horse battery staple", first));
        assertFalse(PasswordUtil.matches("wrong password", first));
        String legacy = Md5Util.getMD5String("old password");
        assertTrue(PasswordUtil.matches("old password", legacy));
        assertTrue(PasswordUtil.needsUpgrade(legacy));
    }

    @Test
    void normalUserCannotWriteAdminHerbEndpoint() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        UserMapper users = mock(UserMapper.class);
        LoginInterceptor interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(interceptor, "userMapper", users);

        String token = JwtUtil.genToken(Map.of("id", 7, "username", "reader"));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(token)).thenReturn(token);
        User user = new User();
        user.setId(7);
        user.setRole("ROLE_USER");
        when(users.getUserById(7)).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/herb");
        request.addHeader("Authorization", token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handler = new HandlerMethod(new HerbController(),
                HerbController.class.getMethod("delete", Integer.class));
        assertFalse(interceptor.preHandle(request, response, handler));
        assertEquals(403, response.getStatus());
    }

    @Test
    void readingAiHistoryDoesNotUseGenerationQuota() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        UserMapper users = mock(UserMapper.class);
        LoginInterceptor interceptor = new LoginInterceptor();
        ReflectionTestUtils.setField(interceptor, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(interceptor, "userMapper", users);
        String token = JwtUtil.genToken(Map.of("id", 7, "username", "reader"));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(token)).thenReturn(token);
        User user = new User();
        user.setId(7);
        user.setRole("ROLE_USER");
        when(users.getUserById(7)).thenReturn(user);

        MockHttpServletRequest history = new MockHttpServletRequest("GET", "/ai/history/type");
        history.addHeader("Authorization", token);
        HandlerMethod historyHandler = new HandlerMethod(new AiController(),
                AiController.class.getMethod("getHistoryByType", String.class));
        assertTrue(interceptor.preHandle(history, new MockHttpServletResponse(), historyHandler));
        verify(values, never()).increment("ai:daily:7");

        MockHttpServletRequest generation = new MockHttpServletRequest("POST", "/ai/diagnosis");
        generation.addHeader("Authorization", token);
        when(values.increment("ai:daily:7")).thenReturn(31L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod generationHandler = new HandlerMethod(new AiController(),
                AiController.class.getMethod("diagnosis", Map.class));
        assertFalse(interceptor.preHandle(generation, response, generationHandler));
        assertEquals(429, response.getStatus());
    }

    @Test
    void usersCannotUpdateAnotherProfileOrPost() {
        ThreadLocalUtil.set(Map.of("id", 7, "role", "ROLE_USER"));
        UserServiceImpl userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "userMapper", mock(UserMapper.class));
        User other = new User();
        other.setId(8);
        assertThrows(ForbiddenException.class, () -> userService.update(other));

        PostMapper posts = mock(PostMapper.class);
        PostServiceImpl postService = new PostServiceImpl();
        ReflectionTestUtils.setField(postService, "postMapper", posts);
        Post post = new Post();
        post.setId(21);
        post.setPosterId(8);
        when(posts.findById(21)).thenReturn(post);
        assertThrows(ForbiddenException.class, () -> postService.delete(21));
        verify(posts, never()).delete(21);
    }

    @Test
    void historyDeletionIncludesCurrentUserId() {
        ThreadLocalUtil.set(Map.of("id", 7, "role", "ROLE_USER"));
        DiagnosisHistoryMapper mapper = mock(DiagnosisHistoryMapper.class);
        DiagnosisHistoryServiceImpl service = new DiagnosisHistoryServiceImpl();
        ReflectionTestUtils.setField(service, "diagnosisHistoryMapper", mapper);
        service.deleteHistory(21);
        verify(mapper).deleteForUser(21, 7);
        verify(mapper, never()).delete(21);
    }

    @Test
    void registrationUsesUsernameAndNeverAcceptsClientRole() {
        UserController controller = new UserController();
        UserService service = mock(UserService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", redis);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(1, controller.register(Map.of("username", "bad", "email", "user@example.com", "password", "weakpass"), request).getCode());
        verify(service, never()).register(anyString(), anyString(), anyString(), anyString());

        assertEquals(0, controller.register(Map.of("username", "reader123", "email", "user@example.com", "password", "Strongpass1", "role", "ROLE_ADMIN"), request).getCode());
        verify(service).register(eq("reader123"), eq("Strongpass1"), eq("user@example.com"), eq("ROLE_USER"));
    }

    @Test
    void loginFindsAccountByUsername() {
        UserController controller = new UserController();
        UserService service = mock(UserService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        SessionService sessions = mock(SessionService.class);
        ReflectionTestUtils.setField(controller, "userService", service);
        ReflectionTestUtils.setField(controller, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(controller, "sessionService", sessions);
        when(redis.opsForValue()).thenReturn(values);
        User user = new User();
        user.setId(9);
        user.setUsername("legacy");
        user.setPassword(PasswordUtil.hash("Strongpass1"));
        when(service.FindByUserName("legacy")).thenReturn(user);
        assertEquals(0, controller.login(Map.of("username", "legacy", "password", "Strongpass1"),
                new MockHttpServletRequest()).getCode());
        verify(service).FindByUserName("legacy");
        verify(sessions).register(eq(9), anyString());
    }
}
