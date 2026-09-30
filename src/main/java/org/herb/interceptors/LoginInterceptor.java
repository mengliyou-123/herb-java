package org.herb.interceptors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.herb.utils.JwtUtil;
import org.herb.utils.ThreadLocalUtil;
import org.herb.mapper.UserMapper;
import org.herb.pojo.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    public StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        if ("OPTIONS".equals(request.getMethod())) return true;
        //令牌验证
        String token = request.getHeader("Authorization");

        //验证token
        //"claims"是一个用于存储有关用户身份和权限的声明的术语
        try {
            //从redis中获取相同的token
            ValueOperations<String, String> operations = stringRedisTemplate.opsForValue();
            String redisToken = operations.get(token);
            if (redisToken == null){
                //token已经失效了
                throw new RuntimeException();
            }
            Map<String, Object> claims = JwtUtil.parseToken(token);
            User user = userMapper.getUserById((Integer) claims.get("id"));
            if (user == null) {
                response.setStatus(401);
                return false;
            }
            Map<String, Object> authenticated = new HashMap<>(claims);
            authenticated.put("role", user.getRole());
            //把业务数据存储到ThreadLocal中
            ThreadLocalUtil.set(authenticated);
            if (requiresAdmin(request, handler) && !"ROLE_ADMIN".equals(user.getRole())) {
                ThreadLocalUtil.remove();
                response.setStatus(403);
                return false;
            }
            String pageSize = request.getParameter("pageSize");
            String pageNum = request.getParameter("pageNum");
            if ((pageSize != null && (!pageSize.matches("[1-9][0-9]{0,2}") || Integer.parseInt(pageSize) > 100))
                    || (pageNum != null && (!pageNum.matches("[1-9][0-9]{0,5}")))) {
                ThreadLocalUtil.remove();
                response.setStatus(400);
                return false;
            }
            if (handler instanceof HandlerMethod actionHandler &&
                    java.util.Set.of("AiController", "PcmRecommendController")
                            .contains(actionHandler.getBeanType().getSimpleName())) {
                if (request.getContentLengthLong() > 64 * 1024) {
                    ThreadLocalUtil.remove();
                    response.setStatus(413);
                    return false;
                }
                String quotaKey = "ai:daily:" + user.getId();
                Long count = stringRedisTemplate.opsForValue().increment(quotaKey);
                if (count != null && count == 1) stringRedisTemplate.expire(quotaKey, 1, TimeUnit.DAYS);
                if (count != null && count > 30) {
                    ThreadLocalUtil.remove();
                    response.setStatus(429);
                    return false;
                }
            }
            //放行
            return true;
        } catch (Exception e) {
            ThreadLocalUtil.remove();
            //http响应状态码为401
            response.setStatus(401);
            //不放行
            return false;
        }
    }

    private boolean requiresAdmin(HttpServletRequest request, Object handler) {
        if (!(handler instanceof HandlerMethod methodHandler)) return false;
        String controller = methodHandler.getBeanType().getSimpleName();
        String action = methodHandler.getMethod().getName();
        String method = request.getMethod();
        if ("OPTIONS".equals(method)) return false;
        if (controller.equals("UserController") &&
                (action.equals("list") || action.equals("delete"))) return true;
        if (java.util.Set.of("HerbController", "PrescriptionController", "PcmController",
                "BookController", "ChapterController", "ContentController").contains(controller)) {
            return !("GET".equals(method) || "HEAD".equals(method));
        }
        if (action.equals("deleteByUserId") &&
                java.util.Set.of("PostController", "CommentController", "PostCollectionController",
                        "BookCollectionController", "PreCollectionController").contains(controller)) return true;
        if (java.util.Set.of("BookCollectionController", "PreCollectionController").contains(controller)
                && (action.equals("deleteByBookId") || action.equals("deleteByPreId"))) return true;
        if (java.util.Set.of("BookCollectionController", "PreCollectionController",
                "PostCollectionController").contains(controller)
                && (action.equals("addCollNum") || action.equals("subtractCollNum"))) return true;
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //清空ThreadLocal中的数据
        ThreadLocalUtil.remove();
    }
}
