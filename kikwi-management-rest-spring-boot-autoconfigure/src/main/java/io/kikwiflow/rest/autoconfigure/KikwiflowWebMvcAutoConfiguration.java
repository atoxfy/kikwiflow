package io.kikwiflow.rest.autoconfigure;

import io.kikwiflow.management.annotation.KikwiRestController;
import io.kikwiflow.rest.autoconfigure.properties.KikwiflowRestProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
public class KikwiflowWebMvcAutoConfiguration implements WebMvcConfigurer {

    private final KikwiflowRestProperties properties;

    public KikwiflowWebMvcAutoConfiguration(KikwiflowRestProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                properties.getBasePath(),
                HandlerTypePredicate.forAnnotation(KikwiRestController.class)
        );
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> kikwiflowCorsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        String[] origins = properties.getCors().getAllowedOrigins();
        if (origins != null && origins.length > 0) {
            config.setAllowedOrigins(Arrays.asList(origins));
            config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
            config.setAllowedHeaders(Arrays.asList("*"));
            config.setAllowCredentials(true);

            source.registerCorsConfiguration(properties.getBasePath() + "/**", config);
        }

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}