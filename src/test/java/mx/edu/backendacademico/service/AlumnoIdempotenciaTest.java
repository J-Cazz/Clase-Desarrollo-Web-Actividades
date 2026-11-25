package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import mx.edu.backendacademico.exception.ClaveIdempotenciaReutilizadaException;
import mx.edu.backendacademico.service.AlumnoService.ResultadoCreacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Prueba de concurrencia real contra Postgres: no hay forma de simular esto con un mock.
@SpringBootTest
class AlumnoIdempotenciaTest {
    @Autowired private AlumnoService service;

    @Test void dosSolicitudesSimultaneasConLaMismaClaveNoDuplicanElAlumno() throws InterruptedException {
        String sufijo = String.valueOf(System.nanoTime());
        String clave = "IDEMP-" + sufijo;
        String matricula = "I" + sufijo.substring(sufijo.length() - 8);
        String nombre = "Ada Lovelace";
        String correo = "ada" + sufijo + "@u.mx";

        var salida = new CountDownLatch(1);
        var fin = new CountDownLatch(2);
        List<ResultadoCreacion> resultados = new CopyOnWriteArrayList<>();
        List<Exception> errores = new CopyOnWriteArrayList<>();

        Runnable tarea = () -> {
            try {
                salida.await();
                resultados.add(service.crearIdempotente(clave, matricula, nombre, correo));
            } catch (Exception e) {
                errores.add(e);
            } finally {
                fin.countDown();
            }
        };
        new Thread(tarea).start();
        new Thread(tarea).start();
        salida.countDown();
        fin.await();

        assertTrue(errores.isEmpty(), "Ninguna de las dos debe fallar: " + errores);
        assertEquals(2, resultados.size());
        long creaciones = resultados.stream().filter(ResultadoCreacion::creadoAhora).count();
        assertEquals(1, creaciones, "Solo una de las dos debe reportar creación nueva; la otra es la repetición");
        assertEquals(resultados.get(0).alumno().id(), resultados.get(1).alumno().id(),
                "Ambas respuestas deben apuntar al mismo alumno, nunca a dos filas distintas");

        long coincidencias = service.listar().stream()
                .filter(a -> a.matricula().equals(matricula))
                .count();
        assertEquals(1, coincidencias, "No debe haberse creado un alumno duplicado en la base de datos");
    }

    @Test void mismaClaveConContenidoDistintoSeRechaza() {
        String sufijo = String.valueOf(System.nanoTime());
        String clave = "IDEMP-" + sufijo;
        String matricula1 = "J" + sufijo.substring(sufijo.length() - 8);

        // Primer uso legítimo de la clave: queda resuelta con este contenido.
        service.crearIdempotente(clave, matricula1, "Ada Lovelace", "ada" + sufijo + "@u.mx");

        // Reutilizar la misma clave con otro contenido no es un reintento; es un error del cliente.
        String matriculaDistinta = "K" + sufijo.substring(sufijo.length() - 8);
        assertThrows(ClaveIdempotenciaReutilizadaException.class, () ->
                service.crearIdempotente(clave, matriculaDistinta, "Otro Nombre", "otro" + sufijo + "@u.mx"));
    }
}