package com.testehan.finana.controller;

import com.testehan.finana.repository.UserPortfolioRepository;
import com.testehan.finana.service.QuoteService;
import com.testehan.finana.model.user.UserPortfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
public class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPortfolioRepository portfolioRepository;

    @MockitoBean
    private QuoteService quoteService;

    @Test
    public void testGetPortfolio() throws Exception {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user123");
        when(portfolioRepository.findByUserId("user123")).thenReturn(Optional.of(portfolio));

        mockMvc.perform(get("/users/user123/portfolio"))
                .andExpect(status().isOk());
    }

    @Test
    public void testAddItem() throws Exception {
        UserPortfolio portfolio = new UserPortfolio();
        portfolio.setUserId("user123");
        when(portfolioRepository.findByUserId("user123")).thenReturn(Optional.of(portfolio));

        String body = "{\"symbol\": \"AAPL\", \"shares\": 10, \"value\": 150, \"type\": \"stock\"}";

        mockMvc.perform(post("/users/user123/portfolio")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }
}
