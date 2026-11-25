package mx.edu.backendacademico.repository.jpa;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ClaveIdempotenciaRepository extends JpaRepository<ClaveIdempotenciaEntity, String> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO claves_idempotencia (clave, huella, creado_en)
            VALUES (:clave, :huella, now())
            ON CONFLICT (clave) DO NOTHING
            """, nativeQuery = true)
    void reservarSiFalta(@Param("clave") String clave, @Param("huella") String huella);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClaveIdempotenciaEntity c where c.clave = :clave")
    Optional<ClaveIdempotenciaEntity> bloquearPorClave(@Param("clave") String clave);
}