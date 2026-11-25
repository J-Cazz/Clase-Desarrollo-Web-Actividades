package mx.edu.backendacademico.exception;

public class ClaveIdempotenciaReutilizadaException extends RuntimeException {
    public ClaveIdempotenciaReutilizadaException(String clave) {
        super("La clave de idempotencia " + clave + " ya se usó con un contenido distinto");
    }
}