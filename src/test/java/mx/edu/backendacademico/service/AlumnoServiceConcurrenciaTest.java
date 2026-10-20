package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import org.junit.jupiter.api.Test;

class AlumnoServiceConcurrenciaTest {

    @Test
    void dosHilosGuardandoLaMismaMatriculaProducenExactamenteUnRegistroYUnConflicto()
            throws InterruptedException {
        var repositorio = new AlumnoRepositoryEnMemoria();
        var servicio = new AlumnoService(repositorio);

        // Sincroniza el arranque: ambos hilos esperan en la misma línea de salida.
        var salida = new CountDownLatch(1);
        // Avisa cuándo terminaron ambos, sin usar pausas arbitrarias (sleep).
        var fin = new CountDownLatch(2);
        List<Exception> errores = new CopyOnWriteArrayList<>();

        Runnable tarea = () -> {
            try {
                salida.await(); // ambos hilos arrancan lo más juntos posible
                servicio.crear("A777", "Alumno", "alumno@u.mx");
            } catch (Exception e) {
                errores.add(e);
            } finally {
                fin.countDown();
            }
        };

        var hilo1 = new Thread(tarea);
        var hilo2 = new Thread(tarea);
        hilo1.start();
        hilo2.start();

        salida.countDown(); // suelta a los dos al mismo tiempo
        fin.await();        // espera determinista a que ambos terminen

        assertEquals(1, repositorio.buscarTodos().size(),
                "Debe quedar exactamente un registro con la matrícula A777");
        assertEquals(1, errores.size(),
                "Debe producirse exactamente un conflicto (MatriculaDuplicadaException)");
        assertInstanceOf(MatriculaDuplicadaException.class, errores.get(0));
    }
}