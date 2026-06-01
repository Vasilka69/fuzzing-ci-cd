package ru.diplom.cicd.master.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.diplom.cicd.master.repository.JobTemplateRepository;
import ru.diplom.cicd.master.repository.UserRoleRepository;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
class FlywayRepositorySmokeTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cicd_master")
            .withUsername("cicd")
            .withPassword("cicd");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private JobTemplateRepository jobTemplateRepository;

    @Test
    void migrationSeedsAreAvailable() {
        assertFalse(userRoleRepository.findAll().isEmpty());
        assertFalse(jobTemplateRepository.findAll().isEmpty());
    }
}
