package com.example.elibrary.common.web;

import com.example.elibrary.common.error.BusinessException;
import com.example.elibrary.common.error.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;

/**
 * Resolves {@link CurrentUserId} parameters; responds 401 when the identity header is missing or invalid.
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && (parameter.getParameterType() == Long.class || parameter.getParameterType() == long.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        return UserIdHeader.read(request).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED,
                "missing or invalid " + UserIdHeader.NAME + " header"));
    }
}
