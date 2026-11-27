package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Mejora de la U12: regla de acceso por objeto sobre GET /api/v1/alumnos/{id}.
@WebMvcTest(AlumnoController.class)
@Import(SecurityConfig.class)
class AccesoPorObjetoTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    private static final Alumno ALUMNO_1 =
            new Alumno(1L, "A001", "Ada Lovelace", "ada@u.mx", EstatusAlumno.ACTIVO);

    @Test void propietarioConsultaSuPropioExpediente() throws Exception {
        when(service.buscar(1L)).thenReturn(ALUMNO_1);
        mvc.perform(get("/api/v1/alumnos/1").with(user("A001").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void usuarioAjenoNoPuedeConsultarElExpedienteDeOtro() throws Exception {
        when(service.buscar(1L)).thenReturn(ALUMNO_1);
        mvc.perform(get("/api/v1/alumnos/1").with(user("B002").roles("ALUMNO")))
                .andExpect(status().isForbidden());
    }

    @Test void administradorConsultaCualquierExpedienteSinSerElDueno() throws Exception {
        when(service.buscar(1L)).thenReturn(ALUMNO_1);
        mvc.perform(get("/api/v1/alumnos/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}