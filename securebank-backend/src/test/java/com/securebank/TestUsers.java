package com.securebank;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Cadastra e autentica usuários pela API real, como um cliente faria. */
public final class TestUsers {

    public static final String PASSWORD = "Correct-Horse-Battery-9";
    public static final String ADMIN_EMAIL = "admin@securebank.test";
    public static final String ADMIN_PASSWORD = "Bootstrap-Test-Pass-2026!";

    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

    private TestUsers() {}

    public record Login(String accessToken, String refreshToken, String userId, String customerId, String email) {}

    public static MockHttpServletRequestBuilder bearer(String token, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token);
    }

    public static String registerBody(String email, String password) {
        return registerBody(email, password, "+5511999990001");
    }

    public static String registerBody(String email, String password, String phone) {
        return String.format("""
                {"name":"Cliente Teste","document":"%s","email":"%s","phone":"%s","password":"%s"}""",
                cpf(), email, phone, password);
    }

    public static String newEmail() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }

    /** Registra um cliente novo e faz login. */
    public static Login registerAndLogin(MockMvc mvc) throws Exception {
        return registerAndLogin(mvc, "+5511999990001");
    }

    /** Igual a {@link #registerAndLogin(MockMvc)}, com o celular escolhido (para buscas que precisam de um número só deste cliente). */
    public static Login registerAndLogin(MockMvc mvc, String phone) throws Exception {
        String email = newEmail();
        MvcResult registered = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(email, PASSWORD, phone))).andExpect(status().isCreated()).andReturn();
        String userId = read(registered, "$.userId");
        String customerId = read(registered, "$.customerId");
        MvcResult login = login(mvc, email, PASSWORD);
        return new Login(read(login, "$.accessToken"), read(login, "$.refreshToken"), userId, customerId, email);
    }

    public static MvcResult login(MockMvc mvc, String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password)))
                .andExpect(status().isOk()).andReturn();
    }

    public static String adminToken(MockMvc mvc) throws Exception {
        return read(login(mvc, ADMIN_EMAIL, ADMIN_PASSWORD), "$.accessToken");
    }

    /** Corpo de "criar usuário da equipe" para um papel (SUPPORT, RISK, CREDIT, FINANCE, AUDITOR, ADMIN) e nível. */
    public static String staffBody(String email, String password, String role, String level) {
        String department = switch (role) {
            case "SUPPORT" -> "ATENDIMENTO";
            case "RISK" -> "RISCO";
            case "CREDIT" -> "CREDITO";
            case "FINANCE" -> "FINANCEIRO";
            case "AUDITOR" -> "AUDITORIA";
            case "ADMIN" -> "DIRETORIA";
            default -> throw new IllegalArgumentException(role);
        };
        return String.format("{\"email\":\"%s\",\"password\":\"%s\",\"department\":\"%s\",\"level\":\"%s\"}",
                email, password, department, level);
    }

    /** Admin cria um analista da equipe e devolve o access token dele. */
    public static String staffToken(MockMvc mvc, String role) throws Exception {
        return staffToken(mvc, role, role.equals("ADMIN") ? "MANAGER" : "ANALYST");
    }

    public static String staffToken(MockMvc mvc, String role, String level) throws Exception {
        return read(staffLogin(mvc, role, level), "$.accessToken");
    }

    /** Cria o usuário da equipe e faz o login dele. */
    public static MvcResult staffLogin(MockMvc mvc, String role, String level) throws Exception {
        String email = newEmail();
        mvc.perform(bearer(adminToken(mvc), post("/api/v1/admin/users")).contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody(email, PASSWORD, role, level)))
                .andExpect(status().isCreated());
        return login(mvc, email, PASSWORD);
    }

    /** Payload (claims) de um JWT, como JSON — para conferir o que o token carrega. */
    public static String claims(String jwt) {
        return new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    public static String cpf() {
        String base = String.format("%09d", 100_000_000L + SEQ.incrementAndGet() % 800_000_000L);
        int first = digit(base, 10);
        int second = digit(base + first, 11);
        return base + first + second;
    }

    private static int digit(String digits, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < digits.length(); i++) {
            sum += (digits.charAt(i) - '0') * (firstWeight - i);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
