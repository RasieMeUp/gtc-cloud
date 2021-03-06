package com.fmisser.gtc.base.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 修改消息处理的默认编码
 * 但是经过 spring cloud gateway 时还是会乱码，
 * 通过{@link RequestMapping}设置produces = "application/json;charset=UTF-8"解决
 */

@Configuration
public class WebConfig extends WebMvcConfigurationSupport {

//    @Override
//    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
//        // 重新加入Jackson解析json的类，让其顺序排在xml解析的前面
//        converters.add(new MappingJackson2HttpMessageConverter());
//    }

    @Override
    protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.forEach(c -> {
            if (c instanceof MappingJackson2HttpMessageConverter) {
                // 解决返回json数据中文乱码问题
                ((MappingJackson2HttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8);
            }

            if (c instanceof StringHttpMessageConverter) {
                ((StringHttpMessageConverter) c).setDefaultCharset(StandardCharsets.UTF_8);
            }
        });
    }

    @Override
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 过滤swagger
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        registry.addResourceHandler("/swagger-resources/**")
                .addResourceLocations("classpath:/META-INF/resources/swagger-resources/");

        registry.addResourceHandler("/swagger/**")
                .addResourceLocations("classpath:/META-INF/resources/swagger*");

        registry.addResourceHandler("/v2/api-docs/**")
                .addResourceLocations("classpath:/META-INF/resources/v2/api-docs/");
    }
}
