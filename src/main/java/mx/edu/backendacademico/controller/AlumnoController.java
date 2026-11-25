package mx.edu.backendacademico.controller;

// Traduce HTTP a llamadas de aplicación y construye respuestas mediante DTO.

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import mx.edu.backendacademico.dto.ActualizarAlumnoRequest;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.dto.CrearAlumnoRequest;
import mx.edu.backendacademico.service.AlumnoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/alumnos")
public class AlumnoController {
    private final AlumnoService service;
    public AlumnoController(AlumnoService service) { this.service = service; }

    // GET consulta sin solicitar cambios de estado de negocio.
    @GetMapping
    public List<AlumnoResponse> listar() { return service.listar().stream().map(AlumnoMapper::toResponse).toList(); }

    // GET consulta sin solicitar cambios de estado de negocio.
    @GetMapping("/{id}")
    public AlumnoResponse buscar(@PathVariable Long id) { return AlumnoMapper.toResponse(service.buscar(id)); }

    // El servicio asigna identidad; ResponseEntity publica 201 y Location.
    @PostMapping
    public ResponseEntity<AlumnoResponse> crear(
            @RequestHeader(value = "Idempotency-Key", required = false) String claveIdempotencia,
            @RequestBody @Valid CrearAlumnoRequest request) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank()) {
            var creado = service.crear(request.matricula(), request.nombre(), request.correo());
            return ResponseEntity.created(URI.create("/api/v1/alumnos/" + creado.id())).body(AlumnoMapper.toResponse(creado));
        }
        var resultado = service.crearIdempotente(claveIdempotencia, request.matricula(), request.nombre(), request.correo());
        var ubicacion = URI.create("/api/v1/alumnos/" + resultado.alumno().id());
        var cuerpo = AlumnoMapper.toResponse(resultado.alumno());
        return resultado.creadoAhora()
                ? ResponseEntity.created(ubicacion).body(cuerpo)
                : ResponseEntity.ok().location(ubicacion).body(cuerpo);
    }

    // PUT sustituye todos los campos de la representación editable.
    @PutMapping("/{id}")
    public AlumnoResponse actualizar(@PathVariable Long id, @RequestBody @Valid ActualizarAlumnoRequest request) {
        return AlumnoMapper.toResponse(service.actualizar(id, request.nombre(), request.correo()));
    }

    // DELETE exitoso termina con 204, que no permite cuerpo de respuesta.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
