package com.tianshu.assets.common.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private static final List<String> ORIGINS = List.of(
            "http://localhost:5173", "http://127.0.0.1:5173");
    private static final List<String> METHODS = List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(ORIGINS.toArray(String[]::new))
                .allowedMethods(METHODS.toArray(String[]::new))
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ORIGINS);
        configuration.setAllowedMethods(METHODS);
        configuration.addAllowedHeader("*");
        configuration.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
