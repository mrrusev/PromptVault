package com.promptvault;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PromptVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptVaultApplication.class, args);
    }
}
