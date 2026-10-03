package com.example.docassistant;

import com.example.docassistant.ai.ChatModelClient;
import com.example.docassistant.ai.FallbackChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the whole application against a throwaway Postgres+pgvector and checks Flyway ran. */
@SpringBootTest
@Testcontainers
class ApplicationContextTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("docassistant")
            .withUsername("doc")
            .withPassword("doc");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithTheFallbackChatClientAsThePrimary() {
        assertThat(applicationContext.getBean(ChatModelClient.class))
                .isInstanceOf(FallbackChatClient.class);
    }

    @Test
    void flywayAppliedTheBaselineMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    void embeddingColumnAndVectorSearchWork() {
        Integer hasColumn = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name = 'document_chunks' "
                        + "and column_name = 'embedding'", Integer.class);
        assertThat(hasColumn).isEqualTo(1);
        // pgvector's cosine distance operator is available: identical vectors are at distance 0.
        Double distance = jdbcTemplate.queryForObject("select '[1,0,0]'::vector <=> '[1,0,0]'::vector", Double.class);
        assertThat(distance).isZero();
    }
}
