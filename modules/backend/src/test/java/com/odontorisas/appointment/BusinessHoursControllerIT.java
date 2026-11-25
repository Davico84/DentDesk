package com.odontorisas.appointment;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de "Horario de atención configurable por el administrador" a nivel HTTP, contra PostgreSQL real
 * (add-appointments tarea 2.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BusinessHoursControllerIT extends AbstractIntegrationTest {

    private static final String CLOSED = "{\"day\":\"%s\",\"segments\":[]}";
    private static final String NINE_TO_SIX = "{\"day\":\"%s\",\"segments\":[{\"opens\":\"09:00\",\"closes\":\"18:00\"}]}";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TokenService tokenService;

    private String registerAdmin(String clinic) throws Exception {
        String email = "bh-" + UUID.randomUUID() + "@clinica.test";
        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invitedAdminRegisterBody(clinic, email, "password123", "Ana Pérez")))
            .andExpect(status().isCreated())
            .andReturn();
        return "Bearer " + JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.accessToken");
    }

    /** Semana con el lunes dado y el resto como el default (sábado medio día, domingo cerrado). */
    private static String week(String monday) {
        return "{\"days\":[" + monday + ","
            + NINE_TO_SIX.formatted("TUESDAY") + "," + NINE_TO_SIX.formatted("WEDNESDAY") + ","
            + NINE_TO_SIX.formatted("THURSDAY") + "," + NINE_TO_SIX.formatted("FRIDAY") + ","
            + "{\"day\":\"SATURDAY\",\"segments\":[{\"opens\":\"09:00\",\"closes\":\"13:00\"}]},"
            + CLOSED.formatted("SUNDAY") + "]}";
    }

    private static String monday(String... segments) {
        StringBuilder json = new StringBuilder("{\"day\":\"MONDAY\",\"segments\":[");
        for (int i = 0; i < segments.length; i += 2) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"opens\":\"").append(segments[i]).append("\",\"closes\":\"").append(segments[i + 1]).append("\"}");
        }
        return json.append("]}").toString();
    }

    @Test
    void a_new_organization_starts_with_the_default_hours_and_its_timezone() throws Exception {
        String admin = registerAdmin("Clínica Horario");

        mockMvc.perform(get("/business-hours").header("Authorization", admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("America/Lima"))
            .andExpect(jsonPath("$.days.length()").value(7))
            .andExpect(jsonPath("$.days[0].day").value("MONDAY"))
            .andExpect(jsonPath("$.days[0].segments[0].opens").value("09:00"))
            .andExpect(jsonPath("$.days[0].segments[0].closes").value("18:00"))
            .andExpect(jsonPath("$.days[5].segments[0].closes").value("13:00"))
            .andExpect(jsonPath("$.days[6].segments.length()").value(0));
    }

    @Test
    void the_admin_saves_a_split_shift() throws Exception {
        String admin = registerAdmin("Clínica Turno Partido");

        mockMvc.perform(put("/business-hours").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(week(monday("15:00", "19:00", "09:00", "13:00"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.days[0].segments[0].opens").value("09:00"))
            .andExpect(jsonPath("$.days[0].segments[1].opens").value("15:00"));

        mockMvc.perform(get("/business-hours").header("Authorization", admin))
            .andExpect(jsonPath("$.days[0].segments.length()").value(2))
            .andExpect(jsonPath("$.days[0].segments[1].closes").value("19:00"));
    }

    @Test
    void invalid_segments_are_rejected_without_saving_anything() throws Exception {
        String admin = registerAdmin("Clínica Inválida");

        for (String badMonday : new String[] {
            monday("14:00", "10:00"), monday("09:00", "13:00", "12:00", "15:00"), monday("09:07", "12:00")}) {
            mockMvc.perform(put("/business-hours").header("Authorization", admin)
                    .contentType(MediaType.APPLICATION_JSON).content(week(badMonday)))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/business-hours").header("Authorization", admin))
            .andExpect(jsonPath("$.days[0].segments.length()").value(1))
            .andExpect(jsonPath("$.days[0].segments[0].opens").value("09:00"));
    }

    @Test
    void an_incomplete_week_or_a_repeated_day_is_rejected() throws Exception {
        String admin = registerAdmin("Clínica Semana");

        mockMvc.perform(put("/business-hours").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":[" + monday("09:00", "13:00") + "]}"))
            .andExpect(status().isBadRequest());

        String repeated = week(monday("09:00", "13:00")).replace("\"SUNDAY\"", "\"MONDAY\"");
        mockMvc.perform(put("/business-hours").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(repeated))
            .andExpect(status().isBadRequest());
    }

    @Test
    void a_member_cannot_change_the_hours() throws Exception {
        String member = "Bearer " + tokenService.issueAccessToken(999L, "member@clinica.test", 999L, Role.MEMBER);

        mockMvc.perform(put("/business-hours").header("Authorization", member)
                .contentType(MediaType.APPLICATION_JSON).content(week(monday("09:00", "13:00"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void the_hours_are_always_those_of_the_active_organization() throws Exception {
        String adminA = registerAdmin("Clínica A");
        String adminB = registerAdmin("Clínica B");

        mockMvc.perform(put("/business-hours").header("Authorization", adminA)
                .contentType(MediaType.APPLICATION_JSON).content(week(monday("10:00", "12:00"))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/business-hours").header("Authorization", adminA))
            .andExpect(jsonPath("$.days[0].segments[0].opens").value("10:00"));
        mockMvc.perform(get("/business-hours").header("Authorization", adminB))
            .andExpect(jsonPath("$.days[0].segments[0].opens").value("09:00"))
            .andExpect(jsonPath("$.days[0].segments[0].closes").value("18:00"));
    }

    @Test
    void without_a_session_the_hours_are_not_readable() throws Exception {
        mockMvc.perform(get("/business-hours")).andExpect(status().isUnauthorized());
    }
}
