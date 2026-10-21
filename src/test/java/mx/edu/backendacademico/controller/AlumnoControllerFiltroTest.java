package mx.edu.backendacademico.controller;

import static org.junit.jupiter.api.Assertions.*;

import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;

class AlumnoControllerFiltroTest {

    @Test
    void filtrarPorEstatusNoModificaElRepositorio() {
        var repositorio = new AlumnoRepositoryEnMemoria();
        var servicio = new AlumnoService(repositorio);
        var controller = new AlumnoController(servicio);

        servicio.crear("A001", "Ada", "ada@u.mx");
        servicio.crear("A002", "Alan", "alan@u.mx");

        var totalAntes = repositorio.buscarTodos().size();

        // Tres consultas con distintos filtros, igual que las tres peticiones del reto.
        controller.listar(null);
        controller.listar(EstatusAlumno.ACTIVO);
        controller.listar(EstatusAlumno.BAJA);

        var totalDespues = repositorio.buscarTodos().size();

        // Consultar (GET) es seguro: el repositorio no cambia de tamaño ni de contenido.
        assertEquals(totalAntes, totalDespues,
                "Filtrar por estatus no debe alterar el repositorio");
    }

    @Test
    void sinFiltroConservaElContratoPrevio() {
        var repositorio = new AlumnoRepositoryEnMemoria();
        var servicio = new AlumnoService(repositorio);
        var controller = new AlumnoController(servicio);

        servicio.crear("A001", "Ada", "ada@u.mx");
        servicio.crear("A002", "Alan", "alan@u.mx");

        // Sin parámetro, debe regresar todos los alumnos, igual que antes del reto.
        assertEquals(2, controller.listar(null).size());
    }
}