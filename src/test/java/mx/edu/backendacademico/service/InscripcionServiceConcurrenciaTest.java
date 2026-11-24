package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Corre contra el PostgreSQL real de docker compose (sin override a H2): la
// exclusión mutua que se demuestra aquí depende del bloqueo FOR UPDATE de
// PostgreSQL, que H2 no reproduce de forma fiable. Requiere "docker compose
// up -d" antes de ejecutarse. Testcontainers automatiza esto desde U09.
@SpringBootTest
class InscripcionServiceConcurrenciaTest {

    @Autowired private InscripcionService inscripcionService;
    @Autowired private AlumnoService alumnoService;
    @Autowired private MateriaRepository materiaRepository;
    @Autowired private InscripcionRepository inscripcionRepository;

    @Test
    void dosAdmisionesSimultaneasParaUnSoloLugarDejanUnaInscripcionYUnRechazo()
            throws InterruptedException {
        String sufijo = String.valueOf(System.nanoTime());

        // Materia de un solo lugar, creada exclusivamente para este reto.
        MateriaEntity materia = materiaRepository.save(
                new MateriaEntity("RETO" + sufijo, "Taller de concurrencia", 4, 1));

        // Dos alumnos distintos compitiendo por la última plaza.
        var alumno1 = alumnoService.crear("C1" + sufijo, "Alumno Uno", "uno" + sufijo + "@u.mx");
        var alumno2 = alumnoService.crear("C2" + sufijo, "Alumno Dos", "dos" + sufijo + "@u.mx");

        // Sincroniza el arranque: ambos hilos esperan en la misma línea de salida.
        var salida = new CountDownLatch(1);
        // Avisa cuándo terminaron ambos, sin usar pausas arbitrarias (sleep).
        var fin = new CountDownLatch(2);
        List<Long> admitidos = new CopyOnWriteArrayList<>();
        List<Exception> errores = new CopyOnWriteArrayList<>();

        Runnable tarea1 = () -> intentar(alumno1.id(), materia.getId(), salida, fin, admitidos, errores);
        Runnable tarea2 = () -> intentar(alumno2.id(), materia.getId(), salida, fin, admitidos, errores);

        var hilo1 = new Thread(tarea1);
        var hilo2 = new Thread(tarea2);
        hilo1.start();
        hilo2.start();

        salida.countDown(); // suelta a los dos al mismo tiempo, hacia el bean real del servicio
        fin.await();        // espera determinista a que ambos terminen

        assertEquals(1, admitidos.size(), "Debe admitirse exactamente una inscripción");
        assertEquals(1, errores.size(), "Debe rechazarse exactamente una por falta de cupo");
        assertInstanceOf(OperacionAcademicaException.class, errores.get(0));
        assertEquals("SIN_CUPO", ((OperacionAcademicaException) errores.get(0)).getCodigo());

        // Confirma el estado persistido real en PostgreSQL, no solo lo observado en memoria.
        assertEquals(1, inscripcionRepository.countByMateriaId(materia.getId()));
    }

    private void intentar(Long alumnoId, Long materiaId, CountDownLatch salida, CountDownLatch fin,
                          List<Long> admitidos, List<Exception> errores) {
        try {
            salida.await();
            // Ambos hilos llaman al mismo bean de InscripcionService administrado por Spring.
            Long id = inscripcionService.inscribir(alumnoId, materiaId);
            admitidos.add(id);
        } catch (Exception e) {
            errores.add(e);
        } finally {
            fin.countDown();
        }
    }
}