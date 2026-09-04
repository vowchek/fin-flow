package com.ledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.api.dto.MoneyEntryRequest;
import com.ledger.api.dto.ProjectRequest;
import com.ledger.api.dto.TimeEntryRequest;
import com.ledger.domain.MoneyDirection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createsProjectTimeAndMoneyThenSummarizes() throws Exception {
        MvcResult createdProject = mvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProjectRequest("Клиент А", "Сопровождение"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Клиент А")))
                .andReturn();

        UUID projectId = UUID.fromString(objectMapper.readTree(createdProject.getResponse().getContentAsString())
                .get("id").asText());

        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T11:30:00Z");

        mvc.perform(post("/api/v1/time-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TimeEntryRequest(projectId, start, end, "Созвон и правки"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.durationMinutes", is(150)));

        mvc.perform(post("/api/v1/money-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyEntryRequest(
                                projectId,
                                MoneyDirection.INCOME,
                                new BigDecimal("15000.00"),
                                "RUB",
                                LocalDate.parse("2026-09-01"),
                                "invoice",
                                "Аванс"
                        ))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/money-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MoneyEntryRequest(
                                projectId,
                                MoneyDirection.EXPENSE,
                                new BigDecimal("500.00"),
                                "RUB",
                                LocalDate.parse("2026-09-01"),
                                "software",
                                "Подписка"
                        ))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/summary")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes", is(150)))
                .andExpect(jsonPath("$.income", closeTo(15000.0, 0.001)))
                .andExpect(jsonPath("$.expense", closeTo(500.0, 0.001)))
                .andExpect(jsonPath("$.net", closeTo(14500.0, 0.001)))
                .andExpect(jsonPath("$.currency", is("RUB")));
    }

    @Test
    void rejectsUnknownProject() throws Exception {
        Instant start = Instant.parse("2026-09-01T09:00:00Z");
        Instant end = Instant.parse("2026-09-01T10:00:00Z");
        mvc.perform(post("/api/v1/time-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TimeEntryRequest(UUID.randomUUID(), start, end, null))))
                .andExpect(status().isNotFound());
    }
}
