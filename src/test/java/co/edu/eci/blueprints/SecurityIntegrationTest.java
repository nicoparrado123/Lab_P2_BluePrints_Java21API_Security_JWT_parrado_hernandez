package co.edu.eci.blueprints;

import co.edu.eci.blueprints.security.RsaKeyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion de punta a punta: login real, JWT real y filtros de Spring Security reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    private static final String API = "/api/v1/blueprints";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired RsaKeyProperties props;

    // ----------------------------------------------------------------- helpers

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("access_token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private String signedToken(String issuer, Instant issuedAt, Instant expiresAt, String scope) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject("assistant")
                .claim("scope", scope)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static String newBlueprintJson(String author, String name) {
        return "{\"author\":\"%s\",\"name\":\"%s\",\"points\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":2}]}"
                .formatted(author, name);
    }

    // ------------------------------------------------------------------- login

    @Test
    void studentLoginReturnsReadOnlyToken() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"student123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andExpect(jsonPath("$.scope").value("blueprints.read"));
    }

    @Test
    void assistantLoginReturnsReadWriteToken() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"assistant\",\"password\":\"assistant123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("blueprints.read blueprints.write"));
    }

    @Test
    void tokenContainsExpectedClaims() throws Exception {
        Jwt jwt = decoder.decode(login("student", "student123"));
        assertEquals(props.issuer(), jwt.getClaimAsString("iss"));
        assertEquals("student", jwt.getSubject());
        assertEquals("blueprints.read", jwt.getClaimAsString("scope"));
        assertEquals(Duration.ofSeconds(props.tokenTtlSeconds()), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("invalid_credentials"));
    }

    @Test
    void unknownUserReturns401() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingPasswordReturns400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ------------------------------------------------------------ public paths

    @Test
    void healthAndOpenApiArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // --------------------------------------------------------------------- 401

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mvc.perform(get(API))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void malformedTokenReturns401() throws Exception {
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void tamperedTokenReturns401() throws Exception {
        String token = login("assistant", "assistant123");
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Instant past = Instant.now().minusSeconds(120);
        String expired = signedToken(props.issuer(), past, past.plusSeconds(60), "blueprints.read");
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("expired")));
    }

    @Test
    void tokenFromAnotherIssuerReturns401() throws Exception {
        Instant now = Instant.now();
        String foreign = signedToken("https://evil.example", now, now.plusSeconds(600), "blueprints.read");
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    // --------------------------------------------------------------------- 403

    @Test
    void studentCannotCreateBlueprint() throws Exception {
        mvc.perform(post(API).header(HttpHeaders.AUTHORIZATION, bearer(login("student", "student123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newBlueprintJson("student", "forbidden")))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("insufficient_scope")))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void studentCannotAddPoints() throws Exception {
        mvc.perform(put(API + "/john/house/points").header(HttpHeaders.AUTHORIZATION, bearer(login("student", "student123")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":5,\"y\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutScopesCannotRead() throws Exception {
        Instant now = Instant.now();
        String noScopes = signedToken(props.issuer(), now, now.plusSeconds(600), "");
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, bearer(noScopes)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ happy paths

    @Test
    void studentCanReadAllByAuthorAndByName() throws Exception {
        String auth = bearer(login("student", "student123"));
        mvc.perform(get(API).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(API + "/jane").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        mvc.perform(get(API + "/jane/garden").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.author").value("jane"))
                .andExpect(jsonPath("$.data.points", hasSize(3)));
    }

    @Test
    void unknownResourcesReturn404() throws Exception {
        String auth = bearer(login("student", "student123"));
        mvc.perform(get(API + "/nobody").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        mvc.perform(get(API + "/john/nothing").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void assistantCanCreateAndAddPoints() throws Exception {
        String auth = bearer(login("assistant", "assistant123"));
        String name = "bp-" + UUID.randomUUID();

        mvc.perform(post(API).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newBlueprintJson("assistant", name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.name").value(name));

        mvc.perform(post(API).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newBlueprintJson("assistant", name)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mvc.perform(put(API + "/assistant/" + name + "/points").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":7,\"y\":8}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value(202));

        mvc.perform(get(API + "/assistant/" + name).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.points", hasSize(3)));
    }

    @Test
    void invalidPayloadsReturn400() throws Exception {
        String auth = bearer(login("assistant", "assistant123"));
        mvc.perform(post(API).header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"author\":\"\",\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(put(API + "/john/house/points").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(API + "/nobody/x/points").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1,\"y\":1}"))
                .andExpect(status().isNotFound());
    }
}
