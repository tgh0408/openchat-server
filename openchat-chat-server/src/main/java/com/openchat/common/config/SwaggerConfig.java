package com.openchat.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openchatOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("openchat接口文档")
                        .version("v1.0")
                        .description("openchat接口文档")
                        .contact(new Contact()
                                .name("TGH")
                                .url("http://www.baidu.com")
                                .email("1003340666@qq.com")));
    }
}
