package com.My.market.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig  implements WebMvcConfigurer {
    private String resourcePath = "/upload/**";

    // 상품 이미지가 저장되는 폴더 (application.properties 의 app.upload-dir)
    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 폴더 경로는 끝이 '/' 여야 그 안의 파일로 인식된다
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler(resourcePath)
                .addResourceLocations(location);
    }
}
