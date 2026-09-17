package com.tatzains.doodle_hometask.config;

import com.tatzains.doodle_hometask.web.CurrentUserResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI doodleOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mini Doodle API")
                .description("Meeting-scheduling backend: create users, manage your own free/busy "
                        + "slots, check another user's availability, and book multi-participant "
                        + "meetings atomically.")
                .version("v1"));
    }

    /**
     * {@link CurrentUserResolver} reads {@code X-User-Id} off the raw request, so springdoc
     * can't infer it from the method signature — add it here for any handler that takes one.
     */
    @Bean
    public OperationCustomizer currentUserHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            boolean requiresCurrentUser = Arrays.stream(handlerMethod.getMethodParameters())
                    .anyMatch(param -> HttpServletRequest.class.isAssignableFrom(param.getParameterType()));
            if (requiresCurrentUser) {
                operation.addParametersItem(new HeaderParameter()
                        .name(CurrentUserResolver.USER_ID_HEADER)
                        .description("Id of the acting user. Stands in for real authentication in this project.")
                        .required(true)
                        .schema(new StringSchema().format("uuid")));
            }
            return operation;
        };
    }
}
