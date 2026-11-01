package com.example.elibrary.common.config;

import com.example.elibrary.common.ratelimit.RateLimitProperties;
import com.example.elibrary.common.ratelimit.UserRateLimitInterceptor;
import com.example.elibrary.common.web.CurrentUserIdArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    public WebConfig(RateLimitProperties rateLimitProperties, ObjectMapper objectMapper) {
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitProperties.isEnabled()) {
            registry.addInterceptor(new UserRateLimitInterceptor(rateLimitProperties, objectMapper))
                    .addPathPatterns("/api/v1/loans", "/api/v1/loans/**");
        }
    }
}
