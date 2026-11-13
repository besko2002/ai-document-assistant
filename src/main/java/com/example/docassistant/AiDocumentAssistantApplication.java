package com.example.docassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiDocumentAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDocumentAssistantApplication.class, args);
    }
}
