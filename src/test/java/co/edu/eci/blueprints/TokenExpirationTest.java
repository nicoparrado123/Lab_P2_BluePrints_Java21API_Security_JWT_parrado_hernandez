package co.edu.eci.blueprints;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actividad 4 automatizada: con token-ttl-seconds=3 el mismo token pasa de 200 a 401
 * en cuanto vence (clock-skew-seconds=0, sin la tolerancia de 60 s de Spring).
 */
@SpringBootTest(properties = "blueprints.security.token-ttl-seconds=3")
@AutoConfigureMockMvc
class TokenExpirationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void tokenStopsWorkingAfterTtl() throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"student123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expires_in").value(3))
                .andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + mapper.readTree(body).get("access_token").asText();

        mvc.perform(get("/api/v1/blueprints").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        // exp del JWT se guarda en segundos enteros; 3.2 s garantizan que ya vencio
        Thread.sleep(3_200);

        mvc.perform(get("/api/v1/blueprints").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isUnauthorized());
    }
}
