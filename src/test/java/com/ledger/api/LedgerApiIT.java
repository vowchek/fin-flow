package com.ledger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.api.dto.ExpenseRequest;
import com.ledger.api.dto.HoldingCreateRequest;
import com.ledger.api.dto.InstrumentUpsertRequest;
import com.ledger.api.dto.PortfolioRequest;
import com.ledger.api.dto.RegisterRequest;
import com.ledger.api.dto.TradeRequest;
import com.ledger.domain.ExpenseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
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
    void managesBuySellHistoryAndExpenses() throws Exception {
        String token = registerAndToken("owner@example.com", "password1");

        MvcResult stockInstrument = mvc.perform(post("/api/v1/admin/stock-instruments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InstrumentUpsertRequest("SBER", "SBER", "Сбербанк", "RUB", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID stockInstrumentId = UUID.fromString(objectMapper.readTree(stockInstrument.getResponse().getContentAsString())
                .get("id").asText());

        MvcResult cryptoInstrument = mvc.perform(post("/api/v1/admin/crypto-instruments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InstrumentUpsertRequest("BTC", "bitcoin", "Bitcoin", "USD", true))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cryptoInstrumentId = UUID.fromString(objectMapper.readTree(cryptoInstrument.getResponse().getContentAsString())
                .get("id").asText());

        MvcResult stockCreated = mvc.perform(post("/api/v1/stock-portfolios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PortfolioRequest("Брокер", "ИИС"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID stockId = UUID.fromString(objectMapper.readTree(stockCreated.getResponse().getContentAsString())
                .get("id").asText());

        LocalDate buyDate = LocalDate.parse("2026-08-01");
        MvcResult stockHolding = mvc.perform(post("/api/v1/stock-portfolios/" + stockId + "/holdings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HoldingCreateRequest(
                                stockInstrumentId, null, null, new BigDecimal("10"), buyDate, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("SBER")))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.openedOn", is("2026-08-01")))
                .andExpect(jsonPath("$.unitPrice").value(100.0))
                .andReturn();
        UUID stockHoldingId = UUID.fromString(objectMapper.readTree(stockHolding.getResponse().getContentAsString())
                .get("id").asText());

        mvc.perform(post("/api/v1/stock-portfolios/" + stockId + "/holdings/" + stockHoldingId + "/buys")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradeRequest(new BigDecimal("2.5"), LocalDate.parse("2026-09-01"), null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(12.5));

        mvc.perform(post("/api/v1/stock-portfolios/" + stockId + "/holdings/" + stockHoldingId + "/sells")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradeRequest(new BigDecimal("0.5"), LocalDate.parse("2026-09-02"), null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(12));

        mvc.perform(post("/api/v1/stock-portfolios/" + stockId + "/holdings/" + stockHoldingId + "/sells")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TradeRequest(new BigDecimal("100"), LocalDate.now(), null, null))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/stock-portfolios/" + stockId + "/holdings/" + stockHoldingId + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].side", is("BUY")))
                .andExpect(jsonPath("$[2].occurredOn", is("2026-08-01")));

        MvcResult cryptoCreated = mvc.perform(post("/api/v1/crypto-portfolios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PortfolioRequest("Cold", null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cryptoId = UUID.fromString(objectMapper.readTree(cryptoCreated.getResponse().getContentAsString())
                .get("id").asText());

        mvc.perform(post("/api/v1/crypto-portfolios/" + cryptoId + "/holdings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HoldingCreateRequest(
                                cryptoInstrumentId, null, null, new BigDecimal("0.01"), null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol", is("BTC")));

        mvc.perform(get("/api/v1/stock-instruments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol", is("SBER")));

        mvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpenseRequest(
                                ExpenseCategory.GROCERIES,
                                new BigDecimal("15000.00"),
                                "RUB",
                                "2026-08",
                                "Август"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    void isolatesPortfoliosBetweenUsers() throws Exception {
        String alice = registerAndToken("alice@example.com", "password1");
        String bob = registerAndToken("bob@example.com", "password1");

        MvcResult created = mvc.perform(post("/api/v1/stock-portfolios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PortfolioRequest("Alice", null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID portfolioId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText());

        mvc.perform(get("/api/v1/stock-portfolios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(get("/api/v1/stock-portfolios/" + portfolioId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateHoldingSymbol() throws Exception {
        String token = registerAndToken("dup@example.com", "password1");
        mvc.perform(post("/api/v1/admin/crypto-instruments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InstrumentUpsertRequest("ETH", "ethereum", "Ethereum", "USD", true))))
                .andExpect(status().isCreated());

        MvcResult created = mvc.perform(post("/api/v1/crypto-portfolios")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PortfolioRequest("Main", null))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID portfolioId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asText());

        MvcResult catalog = mvc.perform(get("/api/v1/crypto-instruments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        UUID ethId = UUID.fromString(objectMapper.readTree(catalog.getResponse().getContentAsString()).get(0).get("id").asText());

        mvc.perform(post("/api/v1/crypto-portfolios/" + portfolioId + "/holdings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HoldingCreateRequest(
                                ethId, null, null, new BigDecimal("1"), null, null, null))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/crypto-portfolios/" + portfolioId + "/holdings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HoldingCreateRequest(
                                ethId, null, null, new BigDecimal("2"), null, null, null))))
                .andExpect(status().isConflict());
    }

    private String registerAndToken(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(email, password, "Tester"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
