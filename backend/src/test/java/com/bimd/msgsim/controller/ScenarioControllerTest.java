package com.bimd.msgsim.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bimd.msgsim.domain.dto.ScenarioRequest;
import com.bimd.msgsim.domain.dto.ScenarioResponse;
import com.bimd.msgsim.domain.model.BrokerType;
import com.bimd.msgsim.service.scenario.ScenarioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScenarioService scenarioService;

    @Test
    void should_returnScenarios_when_userIsAuthenticated() throws Exception {
        ScenarioResponse response = new ScenarioResponse(
                UUID.randomUUID(), "Kafka saudável", BrokerType.KAFKA, 200, 4, 15,
                BigDecimal.ONE, 3, 2, 120, null, 6, null, true, false,
                com.bimd.msgsim.domain.model.ExecutionMode.SIMULATED,
                com.bimd.msgsim.domain.model.ServiceProfile.EXPONENTIAL, Instant.now(), Instant.now());
        when(scenarioService.listForOwner(eq("user@example.com"))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/scenarios").with(user("user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Kafka saudável"));
    }

    @Test
    void should_returnForbidden_when_userIsNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/scenarios")).andExpect(status().isForbidden());
    }

    @Test
    void should_returnCreated_when_scenarioRequestIsValid() throws Exception {
        ScenarioRequest request = new ScenarioRequest(
                "Novo cenário", BrokerType.SQS, 100, 2, 10, BigDecimal.TEN, 3, 2, 60, null, null, 30, true, false,
                com.bimd.msgsim.domain.model.ExecutionMode.SIMULATED,
                com.bimd.msgsim.domain.model.ServiceProfile.EXPONENTIAL);
        ScenarioResponse response = new ScenarioResponse(
                UUID.randomUUID(), "Novo cenário", BrokerType.SQS, 100, 2, 10, BigDecimal.TEN, 3, 2, 60,
                null, null, 30, true, false, com.bimd.msgsim.domain.model.ExecutionMode.SIMULATED,
                com.bimd.msgsim.domain.model.ServiceProfile.EXPONENTIAL, Instant.now(), Instant.now());
        when(scenarioService.create(eq("user@example.com"), any())).thenReturn(response);

        mockMvc.perform(post("/api/scenarios")
                        .with(user("user@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Novo cenário"));
    }
}
