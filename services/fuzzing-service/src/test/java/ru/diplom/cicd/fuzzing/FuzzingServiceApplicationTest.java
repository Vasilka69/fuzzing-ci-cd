package ru.diplom.cicd.fuzzing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "management.health.kafka.enabled=false")
class FuzzingServiceApplicationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Test
    void healthEndpointIsAvailable() throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
