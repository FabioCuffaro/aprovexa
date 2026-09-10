package com.aprovexa.request.controller;

import com.aprovexa.common.error.RequestNotFoundException;
import com.aprovexa.request.dto.CreateRequestRequest;
import com.aprovexa.request.dto.RequestResponse;
import com.aprovexa.request.dto.TransitionRequest;
import com.aprovexa.request.model.RequestStatus;
import com.aprovexa.request.model.RequestType;
import com.aprovexa.request.service.RequestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequestController.class)
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestService requestService;

    @Test
    void createReturns201AndLocation() throws Exception {
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        when(requestService.create(any(CreateRequestRequest.class))).thenReturn(new RequestResponse(
                1L,
                RequestType.PURCHASE,
                RequestStatus.CREATED,
                "Development laptop",
                "Laptop required for backend development work",
                "Current equipment is insufficient",
                "Laura",
                now,
                now
        ));

        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PURCHASE",
                                  "title": "Development laptop",
                                  "description": "Laptop required for backend development work",
                                  "justification": "Current equipment is insufficient",
                                  "requester": "Laura"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/requests/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void invalidCreateReturns400WithValidationDetails() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "PURCHASE",
                                  "title": "",
                                  "description": "short",
                                  "requester": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.description").exists())
                .andExpect(jsonPath("$.validationErrors.requester").exists());
    }

    @Test
    void missingRequestReturns404() throws Exception {
        when(requestService.findById(99L)).thenThrow(new RequestNotFoundException(99L));

        mockMvc.perform(get("/api/v1/requests/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    void transitionRequiresActorPayload() throws Exception {
        mockMvc.perform(post("/api/v1/requests/1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void submitPassesActorToService() throws Exception {
        Instant now = Instant.parse("2026-09-08T08:00:00Z");
        when(requestService.submit(any(Long.class), any(TransitionRequest.class))).thenReturn(new RequestResponse(
                1L,
                RequestType.PURCHASE,
                RequestStatus.IN_REVIEW,
                "Development laptop",
                "Laptop required for backend development work",
                null,
                "Laura",
                now,
                now
        ));

        mockMvc.perform(post("/api/v1/requests/1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"Laura"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }
}
