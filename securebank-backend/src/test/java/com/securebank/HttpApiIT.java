package com.securebank;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * API por HTTP de verdade (Tomcat real numa porta aleatória, REST Assured): cobre o que MockMvc não enxerga, como
 * cookies, cabeçalhos de segurança, CORS e o formato final das respostas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class HttpApiIT {

    @LocalServerPort int port;

    @BeforeEach
    void configure() {
        io.restassured.RestAssured.port = port;
        io.restassured.RestAssured.baseURI = "http://localhost";
    }

    private String register() {
        String email = TestUsers.newEmail();
        given().contentType(ContentType.JSON).body(TestUsers.registerBody(email, TestUsers.PASSWORD))
                .post("/api/v1/auth/register").then().statusCode(201);
        return email;
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}")
                .post("/api/v1/auth/login").then().statusCode(200).extract().path("accessToken");
    }

    @Test
    void healthIsPublicAndExposesNothingSensitive() {
        given().get("/api/v1/actuator/health").then().statusCode(200).body("status", equalTo("UP"))
                .body("components", org.hamcrest.Matchers.nullValue()); // show-details: never
        given().get("/api/v1/actuator/env").then().statusCode(401);
        given().get("/api/v1/actuator/heapdump").then().statusCode(401);
    }

    @Test
    void protectedResponsesCarrySecurityHeadersAndAreNeverCached() {
        String token = login(register());

        given().auth().oauth2(token).get("/api/v1/accounts").then().statusCode(200)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Cache-Control", containsString("no-store"))
                .header("Content-Security-Policy", containsString("default-src 'none'"))
                .header("Referrer-Policy", "no-referrer")
                .header("X-Trace-Id", not(emptyOrNullString()))
                .header("Server", not(containsString("Apache"))); // sem versão do servidor
    }

    @Test
    void errorsHaveTheStandardShapeWithoutLeakingInternals() {
        Response unauthenticated = given().get("/api/v1/accounts");
        unauthenticated.then().statusCode(401).contentType(ContentType.JSON)
                .body("code", equalTo("UNAUTHENTICATED"), "traceId", notNullValue(), "path", equalTo("/api/v1/accounts"))
                .header("WWW-Authenticate", equalTo("Bearer"));
        org.assertj.core.api.Assertions.assertThat(unauthenticated.asString()).doesNotContain("Exception", "at com.");

        String token = login(register());
        given().auth().oauth2(token).get("/api/v1/accounts/" + UUID.randomUUID()).then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
        given().auth().oauth2(token).contentType(ContentType.JSON).body("{not json")
                .post("/api/v1/accounts").then().statusCode(400).body("code", equalTo("BAD_REQUEST"));
        given().auth().oauth2(token).contentType(ContentType.TEXT).body("x")
                .post("/api/v1/accounts").then().statusCode(415).body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
        given().auth().oauth2(token).delete("/api/v1/accounts").then().statusCode(403); // método não declarado: negado
    }

    @Test
    void thereIsNoCorsForOtherOrigins() {
        // sem CORS configurado, uma origem estranha não ganha permissão alguma
        given().header("Origin", "https://evil.example").get("/api/v1/actuator/health").then()
                .header("Access-Control-Allow-Origin", emptyOrNullString());
        given().header("Origin", "https://evil.example").header("Access-Control-Request-Method", "POST")
                .options("/api/v1/auth/login").then().header("Access-Control-Allow-Origin", emptyOrNullString());
    }

    @Test
    void webLoginSetsAnHttpOnlyRefreshCookieAndTheCookieRenewsTheSession() {
        String email = register();

        Response login = given().header("X-Client", "web").contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + TestUsers.PASSWORD + "\"}")
                .post("/api/v1/auth/login");
        login.then().statusCode(200).body("refreshToken", org.hamcrest.Matchers.nullValue())
                .header("Set-Cookie", startsWith("refresh_token="))
                .header("Set-Cookie", containsString("HttpOnly"))
                .header("Set-Cookie", containsString("SameSite=Strict"))
                .header("Set-Cookie", containsString("Path=/api/v1/auth"));
        String refreshCookie = login.getCookie("refresh_token");

        given().header("X-Client", "web").cookie("refresh_token", refreshCookie).contentType(ContentType.JSON).body("{}")
                .post("/api/v1/auth/refresh").then().statusCode(200).body("accessToken", notNullValue());
        // sem o header de cliente web o cookie não vale (e o token foi rotacionado de qualquer forma)
        given().cookie("refresh_token", refreshCookie).contentType(ContentType.JSON).body("{}").post("/api/v1/auth/refresh").then().statusCode(401);
    }

    @Test
    void aFullMoneyFlowOverRealHttpWithIdempotentReplay() {
        String email = register();
        String token = login(email);
        String account = given().auth().oauth2(token).contentType(ContentType.JSON).body("{\"type\":\"CHECKING\"}")
                .post("/api/v1/accounts").then().statusCode(201).extract().path("id");
        String key = "idem-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) { // o segundo é replay: mesmo resultado, uma única vez no saldo
            given().auth().oauth2(token).header("Idempotency-Key", key).contentType(ContentType.JSON)
                    .body("{\"amount\":\"250.00\"}").post("/api/v1/accounts/" + account + "/deposits")
                    .then().statusCode(201).body("balanceAfter.amount", equalTo("250.00"));
        }
        given().auth().oauth2(token).get("/api/v1/accounts/" + account + "/balance").then().statusCode(200)
                .body("balance.amount", equalTo("250.00"));
        given().auth().oauth2(token).get("/api/v1/accounts/" + account + "/statement").then().statusCode(200)
                .body("totalElements", equalTo(1)).body("items.type", hasItem("DEPOSIT"));
    }
}
