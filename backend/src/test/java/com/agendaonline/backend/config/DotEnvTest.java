package com.agendaonline.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agendaonline.backend.config.DotEnv.JdbcConnection;
import org.junit.jupiter.api.Test;

class DotEnvTest {

    @Test
    void convertsNeonUrlToJdbc() {
        JdbcConnection c = DotEnv.toJdbc(
                "postgresql://neondb_owner:p%40ss@ep-x-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");

        assertThat(c.url()).isEqualTo("jdbc:postgresql://ep-x-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require");
        assertThat(c.username()).isEqualTo("neondb_owner");
        assertThat(c.password()).isEqualTo("p@ss");
    }

    @Test
    void keepsPortAndDefaultsSslmode() {
        JdbcConnection c = DotEnv.toJdbc("postgres://u:p@localhost:5433/db");
        assertThat(c.url()).isEqualTo("jdbc:postgresql://localhost:5433/db?sslmode=require");
    }

    @Test
    void rejectsOtherSchemes() {
        assertThatThrownBy(() -> DotEnv.toJdbc("mysql://u:p@h/db")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stripsQuotesFromEnvFileValues() {
        assertThat(DotEnv.unquote("\"abc\"")).isEqualTo("abc");
        assertThat(DotEnv.unquote("'abc'")).isEqualTo("abc");
        assertThat(DotEnv.unquote("abc")).isEqualTo("abc");
    }
}
