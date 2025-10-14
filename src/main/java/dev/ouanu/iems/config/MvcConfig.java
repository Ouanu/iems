package dev.ouanu.iems.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Value("${file.storage.base-dir:./storage}")
    private String storageDir;

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path storagePath = Paths.get(storageDir).toAbsolutePath();
        String resourceLocation = storagePath.toUri().toString();
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(resourceLocation);
    }
}