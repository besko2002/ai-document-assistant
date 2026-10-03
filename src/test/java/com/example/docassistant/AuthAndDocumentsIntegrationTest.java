package com.example.docassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Register / login / JWT and the PDF upload API, against real Postgres and real PDFs. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.context.annotation.Import(FakeEmbeddingConfig.class)
class AuthAndDocumentsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("docassistant").withUsername("doc").withPassword("doc");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- helpers

    private ResultActions register(String name, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("name", name, "email", email, "password", password))));
    }

    private String token(String email) throws Exception {
        String body = register("Test", email, "Password123").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static byte[] pdf(String... pageTexts) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pageTexts) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text);
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private ResultActions upload(String token, String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("file", filename, "application/pdf", content))
                .header("Authorization", bearer(token)));
    }

    private String uploadedId(String token, byte[] content) throws Exception {
        String body = upload(token, "doc.pdf", content).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asText();
    }

    // ---------------------------------------------------------------- auth

    @Test
    void registerReturnsTokenAndStoresOnlyAHash() throws Exception {
        String email = uniqueEmail();
        String body = register("Ada", email, "Password123").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).path("accessToken").asText()).isNotBlank();

        String stored = jdbc.queryForObject("select password_hash from users where email = ?", String.class, email);
        assertThat(stored).startsWith("$2").isNotEqualTo("Password123");
    }

    @Test
    void emailIsTrimmedAndLowercasedAndDuplicatesAreRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register("A", "  " + email.toUpperCase() + "  ", "Password123").andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(email));
        register("B", email, "Password123").andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationIsRejectedWithFieldErrors() throws Exception {
        register("", "not-an-email", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void loginWorksAndFailuresGiveTheSameGenericMessage() throws Exception {
        String email = uniqueEmail();
        register("Ada", email, "Password123").andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "Password123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").exists());

        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("email", uniqueEmail(), "password", "Password123"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(wrongPassword).path("message").asText())
                .isEqualTo(json.readTree(unknownEmail).path("message").asText());
    }

    @Test
    void protectedEndpointsNeedAValidToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());

        String email = uniqueEmail();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token(email))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- documents

    @Test
    void uploadExtractsTextPerPageAndChunksIt() throws Exception {
        String token = token(uniqueEmail());
        String body = upload(token, "notes.pdf", pdf("Spring Boot makes services easy.", "Kafka moves events around."))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("notes.pdf"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.chunkCount").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode doc = json.readTree(body);

        Integer rows = jdbc.queryForObject("select count(*) from document_chunks where document_id = ?::uuid",
                Integer.class, doc.path("id").asText());
        assertThat(rows).isEqualTo(2);
        Integer page2 = jdbc.queryForObject(
                "select page_number from document_chunks where document_id = ?::uuid and content like '%Kafka%'",
                Integer.class, doc.path("id").asText());
        assertThat(page2).isEqualTo(2);
    }

    @Test
    void invalidUploadsAreRejected() throws Exception {
        String token = token(uniqueEmail());
        upload(token, "fake.pdf", "this is not a pdf".getBytes()).andExpect(status().isBadRequest());
        upload(token, "empty.pdf", new byte[0]).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        // A valid PDF with no text (a blank page) cannot be searched, so it is refused.
        try (PDDocument blank = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            blank.addPage(new PDPage());
            blank.save(out);
            upload(token, "blank.pdf", out.toByteArray()).andExpect(status().isBadRequest());
        }
        // A header-only "PDF" that is not readable.
        upload(token, "broken.pdf", "%PDF-1.4 garbage".getBytes()).andExpect(status().isBadRequest());
    }

    @Test
    void usersOnlySeeAndDeleteTheirOwnDocuments() throws Exception {
        String alice = token(uniqueEmail());
        String bob = token(uniqueEmail());
        String aliceDoc = uploadedId(alice, pdf("Alice private text."));

        mvc.perform(get("/api/documents").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/documents").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        // Bob gets 404, not 403, so he cannot even tell the id exists.
        mvc.perform(get("/api/documents/" + aliceDoc).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/" + aliceDoc).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/" + aliceDoc).header("Authorization", bearer(alice)))
                .andExpect(status().isOk());
    }

    @Test
    void deletingADocumentRemovesItsChunks() throws Exception {
        String token = token(uniqueEmail());
        String id = uploadedId(token, pdf("Some text to chunk."));
        assertThat(jdbc.queryForObject("select count(*) from document_chunks where document_id = ?::uuid",
                Integer.class, id)).isEqualTo(1);

        mvc.perform(delete("/api/documents/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from document_chunks where document_id = ?::uuid",
                Integer.class, id)).isZero();
        mvc.perform(get("/api/documents/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void badDocumentIdIsA400() throws Exception {
        String token = token(uniqueEmail());
        mvc.perform(get("/api/documents/not-a-uuid").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientMistakesOnRoutesAreNot500() throws Exception {
        String token = token(uniqueEmail());
        // Unknown path -> 404, wrong method -> 405 (both used to fall into the 500 catch-all).
        mvc.perform(get("/api/nope").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mvc.perform(delete("/api/documents").header("Authorization", bearer(token)))
                .andExpect(status().isMethodNotAllowed());
    }
}
