package ru.diplom.cicd.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
class BuildServiceApplicationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Environment environment;

    @Test
    void actuatorHealthAndMetricsEndpointsAreAvailable() throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/cicd.executor.jobs.active")).andExpect(status().isOk());
    }

    @Test
    void testProfileKeepsHumanReadableLogsAndConfiguresSourceService() {
        assertEquals("", environment.getProperty("logging.structured.format.console"));
        assertEquals("build-service", environment.getProperty("logging.structured.json.add.sourceService"));
    }
}
