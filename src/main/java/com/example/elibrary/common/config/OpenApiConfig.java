package com.example.elibrary.common.config;

import com.example.elibrary.common.web.CurrentUserId;
import com.example.elibrary.common.web.UserIdHeader;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.SpringDocUtils;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * Swagger UI at /swagger-ui.html. Endpoints that need a user get an explicit X-User-Id header parameter.
 */
@Configuration
public class OpenApiConfig {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("E-Library API")
                .version("v1")
                .description("Browse, borrow and return digital books."));
    }

    @Bean
    public OperationCustomizer userIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean needsUser = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(p -> p.hasParameterAnnotation(CurrentUserId.class));
            if (needsUser) {
                operation.addParametersItem(new HeaderParameter()
                        .name(UserIdHeader.NAME)
                        .required(true)
                        .description("Authenticated user id, set by the API gateway")
                        .schema(new IntegerSchema().format("int64").example(1)));
            }
            return operation;
        };
    }
}
