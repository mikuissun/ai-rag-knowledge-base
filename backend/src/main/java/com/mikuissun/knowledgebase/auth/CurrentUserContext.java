package com.mikuissun.knowledgebase.auth;

import com.mikuissun.knowledgebase.common.exception.BusinessException;

public final class CurrentUserContext {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUser requireCurrentUser() {
        CurrentUser currentUser = CURRENT_USER.get();
        if (currentUser == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return currentUser;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
