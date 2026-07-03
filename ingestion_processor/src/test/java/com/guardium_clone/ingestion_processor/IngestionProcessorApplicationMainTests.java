package com.guardium_clone.ingestion_processor;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class IngestionProcessorApplicationMainTests {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            IngestionProcessorApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(IngestionProcessorApplication.class, args));
        }
    }
}
