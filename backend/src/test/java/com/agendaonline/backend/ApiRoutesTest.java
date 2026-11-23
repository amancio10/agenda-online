package com.agendaonline.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agendaonline.backend.booking.BookingRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Testa as rotas de ponta a ponta (HTTP → segurança → JPA), com H2 no lugar do Neon. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRoutesTest {

    private static final String BOOKING = """
            {"date":"2030-01-07","time":"09:00","name":"Ana","email":"ana@example.com","topic":"Projeto"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BookingRepository bookings;

    @BeforeEach
    void clean() {
        bookings.deleteAll();
    }

    // ---------- Visitante ----------

    @Test
    void visitorCreatesBookingAndSeesOnlyBusySlots() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.time").value("09:00"));

        mvc.perform(get("/api/slots/busy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2030-01-07"))
                .andExpect(jsonPath("$[0].time").value("09:00"))
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void sameSlotTwiceReturns409() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Este horário já está reservado."));
    }

    @Test
    void invalidOrPastBookingReturns400() throws Exception {
        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2030-01-07\",\"time\":\"09:00\",\"name\":\"\",\"email\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists());

        mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2020-01-07\",\"time\":\"09:00\",\"name\":\"A\",\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Não é possível agendar no passado."));
    }

    // ---------- Administrador ----------

    @Test
    void adminRoutesRequireLogin() throws Exception {
        mvc.perform(get("/api/admin/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/bookings").header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("E-mail ou senha inválidos."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ninguem@example.com\",\"password\":\"senha-de-teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLogsInListsAndCancels() throws Exception {
        String created = mvc.perform(post("/api/bookings").contentType(MediaType.APPLICATION_JSON).content(BOOKING))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ADMIN@example.com\",\"password\":\"senha-de-teste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + JsonPath.read(login, "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"));

        mvc.perform(get("/api/admin/bookings").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].email").value("ana@example.com"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].startsAt").value("2030-01-07T12:00:00Z"))
                .andExpect(jsonPath("$[0].endsAt").value("2030-01-07T12:30:00Z"))
                .andExpect(jsonPath("$[0].completedAt").doesNotExist());

        mvc.perform(post("/api/admin/bookings/" + id + "/complete").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty());

        mvc.perform(delete("/api/admin/bookings/" + id).header("Authorization", auth))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/bookings/" + id).header("Authorization", auth))
                .andExpect(status().isNotFound());
        assertThat(bookings.count()).isZero();
    }

    @Test
    void visitorCannotCancel() throws Exception {
        mvc.perform(delete("/api/admin/bookings/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/bookings/00000000-0000-0000-0000-000000000000/complete"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- CORS ----------

    @Test
    void corsAllowsOnlyConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/bookings").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(options("/api/bookings").header("Origin", "https://malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
