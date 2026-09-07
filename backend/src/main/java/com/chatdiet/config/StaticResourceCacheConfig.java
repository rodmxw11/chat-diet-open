package com.chatdiet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * Cache policy for the bundled frontend (served from {@code classpath:/static}, see
 * {@code backend/build.gradle}'s {@code processResources} task). Every file under
 * {@code /assets/**} is Vite's content-hashed output - its filename changes whenever its content
 * does, so it's safe to cache for a year, immutable: a stale copy can never be served under a URL
 * that still resolves. Nothing else is (index.html, sw.js, manifest.webmanifest, icons); those
 * stay on the app-wide {@code spring.web.resources.cache.cachecontrol.no-cache} policy
 * (application.yml) so a browser always revalidates with the server instead of reusing a locally
 * cached app shell - the gap that let deployed fixes sit invisible on installed PWA clients
 * despite the server already serving the current build.
 */
@Configuration
public class StaticResourceCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
