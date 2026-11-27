package mx.edu.backendacademico.config;

// Decide si el principal autenticado es dueño del recurso que pide, sin confiar en datos del cliente.

import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.service.AlumnoService;
import org.springframework.security.core.Authentication;

public class AlumnoAccessEvaluator {
    private final AlumnoService service;

    public AlumnoAccessEvaluator(AlumnoService service) {
        this.service = service;
    }

    public boolean esPropietario(Long id, Authentication authentication) {
        if (authentication == null || id == null) return false;
        try {
            return service.buscar(id).matricula().equalsIgnoreCase(authentication.getName());
        } catch (AlumnoNoEncontradoException ex) {
            return false;
        }
    }
}