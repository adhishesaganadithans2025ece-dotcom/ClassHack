package com.classhack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/login.html").setViewName("forward:/index.html");
        registry.addViewController("/register-admin").setViewName("forward:/register-admin.html");
        registry.addViewController("/activate").setViewName("forward:/activate.html");
        registry.addViewController("/admin/dashboard").setViewName("forward:/admin/dashboard.html");
        registry.addViewController("/student/dashboard").setViewName("forward:/student/dashboard.html");
        registry.addViewController("/403").setViewName("forward:/403.html");
        registry.addViewController("/404").setViewName("forward:/404.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
}
