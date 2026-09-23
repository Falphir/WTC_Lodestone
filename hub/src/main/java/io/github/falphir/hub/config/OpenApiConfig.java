package io.github.falphir.hub.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;

@Configuration
@SecuritySchemes({
        @SecurityScheme(name = "serverToken", type = SecuritySchemeType.HTTP, scheme = "bearer"),
        @SecurityScheme(name = "adminLogin", type = SecuritySchemeType.HTTP, scheme = "basic")
})
public class OpenApiConfig {
}