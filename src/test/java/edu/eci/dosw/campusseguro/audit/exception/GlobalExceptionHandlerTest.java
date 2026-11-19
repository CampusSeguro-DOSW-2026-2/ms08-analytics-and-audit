package edu.eci.dosw.campusseguro.audit.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Checks that every kind of error is answered in the standard format (section 7.1). */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    record Body(@NotBlank String name) {
    }

    @RestController
    @RequestMapping("/t")
    static class ThrowingController {

        @PostMapping("/valid")
        String valid(@Valid @RequestBody Body body) {
            return "ok";
        }

        @GetMapping("/int")
        String number(@RequestParam int n) {
            return "ok";
        }

        @GetMapping("/business")
        String business() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "No existe");
        }

        @GetMapping("/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/unauth")
        String unauth() {
            throw new BadCredentialsException("bad");
        }

        @GetMapping("/db-down")
        String dbDown() {
            throw new DataAccessResourceFailureException("down");
        }

        @GetMapping("/db-timeout")
        String dbTimeout() {
            throw new QueryTimeoutException("slow");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret detail");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidBodyAnswers400WithTheFieldDetail() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}")
                        .header("X-Correlation-ID", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores[0].campo").value("name"))
                .andExpect(jsonPath("$.correlation_id").value("abc"));
    }

    @Test
    void malformedJsonAnswers400() throws Exception {
        mockMvc.perform(post("/t/valid").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void wrongParameterTypeAnswers400WithTheParameterName() throws Exception {
        mockMvc.perform(get("/t/int").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("n"));
    }

    @Test
    void businessExceptionUsesItsOwnCode() throws Exception {
        mockMvc.perform(get("/t/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No existe"));
    }

    @Test
    void accessDeniedAnswers403() throws Exception {
        mockMvc.perform(get("/t/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void authenticationFailureAnswers401() throws Exception {
        mockMvc.perform(get("/t/unauth"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void databaseDownAnswers503() throws Exception {
        mockMvc.perform(get("/t/db-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        mockMvc.perform(get("/t/db-timeout"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unexpectedErrorAnswers500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje").value(ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    @Test
    void unknownPathAnswers404InTheStandardFormat() throws Exception {
        mockMvc.perform(get("/t/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    @Test
    void wrongMethodAnswers405() throws Exception {
        mockMvc.perform(post("/t/int"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }
}
