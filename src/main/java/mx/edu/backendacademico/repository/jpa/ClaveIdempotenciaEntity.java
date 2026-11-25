package mx.edu.backendacademico.repository.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "claves_idempotencia")
public class ClaveIdempotenciaEntity {
    @Id
    private String clave;
    private String huella;
    @Column(name = "alumno_id")
    private Long alumnoId;
    @Column(name = "creado_en")
    private Instant creadoEn;

    protected ClaveIdempotenciaEntity() {}

    public String getClave() { return clave; }
    public String getHuella() { return huella; }
    public Long getAlumnoId() { return alumnoId; }
    public void setAlumnoId(Long alumnoId) { this.alumnoId = alumnoId; }
    public Instant getCreadoEn() { return creadoEn; }
}