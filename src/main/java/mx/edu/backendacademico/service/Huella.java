package mx.edu.backendacademico.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Huella {
    private Huella() {}

    static String calcular(String matricula, String nombre, String correo) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var texto = matricula.strip() + '\u0000' + nombre.strip() + '\u0000' + correo.strip();
            var hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}