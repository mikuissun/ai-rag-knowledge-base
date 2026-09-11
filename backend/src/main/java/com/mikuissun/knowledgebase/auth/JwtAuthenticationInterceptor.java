package com.mikuissun.knowledgebase.auth;

import com.mikuissun.knowledgebase.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements AsyncHandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        JwtTokenService.TokenPayload payload = jwtTokenService
                .parseAndValidate(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(() -> new BusinessException(401, "未登录或登录已过期"));
        CurrentUserContext.set(new CurrentUser(payload.userId(), payload.username()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        CurrentUserContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        CurrentUserContext.clear();
    }
}
