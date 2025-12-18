package com.ticketblitz.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketblitz.backend.controller.TicketController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "scalper_bot", roles = {"CUSTOMER"})
    public void testBackpressureRateLimiting() throws Exception {
        TicketController.PurchaseRequest req = new TicketController.PurchaseRequest();
        req.setEventId(1L);
        req.setTierId(1L);
        req.setSeatId(200L); // assume seat 200

        String jsonBody = objectMapper.writeValueAsString(req);

        // First 5 requests succeed (or return 409 depending on stock/mock, but not 429)
        for (int i = 0; i < 5; i++) {
            req.setIdempotencyKey(java.util.UUID.randomUUID().toString()); // Different UUID each time to simulate attempts
            jsonBody = objectMapper.writeValueAsString(req);
            
            MvcResult res = mockMvc.perform(post("/api/stock/purchase")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody))
                    .andReturn();
            
            // Should NOT be 429
            assert res.getResponse().getStatus() != 429 : "First 5 requests should not be rate limited";
        }

        // 6th to 50th request should return 429 TOO_MANY_REQUESTS
        for (int i = 0; i < 45; i++) {
            req.setIdempotencyKey(java.util.UUID.randomUUID().toString()); 
            jsonBody = objectMapper.writeValueAsString(req);
            
            mockMvc.perform(post("/api/stock/purchase")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody))
                    .andExpect(status().isTooManyRequests()); // 429
        }
    }
}
