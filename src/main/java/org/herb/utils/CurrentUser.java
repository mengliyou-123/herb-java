package org.herb.utils;

import org.herb.exception.ForbiddenException;

import java.util.Map;

public final class CurrentUser {
    private CurrentUser() {}

    public static Integer id() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return (Integer) claims.get("id");
    }

    public static boolean isAdmin() {
        Map<String, Object> claims = ThreadLocalUtil.get();
        return "ROLE_ADMIN".equals(claims.get("role"));
    }

    public static void requireOwnerOrAdmin(Integer ownerId) {
        if (!isAdmin() && !id().equals(ownerId)) {
            throw new ForbiddenException("无权操作该记录");
        }
    }

    public static void requireSelf(Integer userId) {
        if (userId == null || !id().equals(userId)) {
            throw new ForbiddenException("无权访问该用户的数据");
        }
    }
}
