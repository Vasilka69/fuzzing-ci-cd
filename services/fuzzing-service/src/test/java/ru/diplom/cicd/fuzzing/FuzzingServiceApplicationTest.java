package ru.diplom.cicd.fuzzing;

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
class FuzzingServiceApplicationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Environment environment;

    @Test
    void healthEndpointIsAvailable() throws Exception {
        MockMvc mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void testProfileKeepsHumanReadableLogsAndConfiguresSourceService() {
        assertEquals("", environment.getProperty("logging.structured.format.console"));
        assertEquals("fuzzing-service", environment.getProperty("logging.structured.json.add.sourceService"));
    }
}
