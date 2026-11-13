package com.example.docassistant.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Only the test controller below: the real AuthController/DocumentController need services that
// do not exist in a web slice.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestEndpoints.class)
// This test is about the error JSON, not security: skip the security filter chain.
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestEndpoints.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resourceNotFoundBecomes404() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Document 7 was not found"))
                .andExpect(jsonPath("$.path").value("/__test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void conflictBecomes409() throws Exception {
        mockMvc.perform(get("/__test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Document already exists"))
                .andExpect(jsonPath("$.path").value("/__test/conflict"));
    }

    @Test
    void badRequestBecomes400() throws Exception {
        mockMvc.perform(get("/__test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Only PDF files are supported"));
    }

    @Test
    void serviceUnavailableBecomes503() throws Exception {
        mockMvc.perform(get("/__test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("AI provider is rate-limited"));
    }

    @Test
    void validationFailureBecomes400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.path").value("/__test/validate"))
                .andExpect(jsonPath("$.fieldErrors.title").value("must not be blank"));
    }

    @Test
    void unexpectedExceptionBecomes500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret-internal-detail"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    /** Test-only endpoints: one per exception type handled by the advice. */
    @RestController
    @RequestMapping("/__test")
    static class TestEndpoints {

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Document 7 was not found");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Document already exists");
        }

        @GetMapping("/bad-request")
        void badRequest() {
            throw new BadRequestException("Only PDF files are supported");
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new ServiceUnavailableException("AI provider is rate-limited");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload payload) {
            // never reached in the failing case
        }

        record Payload(@NotBlank String title) {
        }
    }
}
