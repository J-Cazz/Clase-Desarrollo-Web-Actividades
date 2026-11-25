CREATE TABLE claves_idempotencia (
                                     clave VARCHAR(80) PRIMARY KEY,
                                     huella VARCHAR(64) NOT NULL,
                                     alumno_id BIGINT REFERENCES alumnos(id),
                                     creado_en TIMESTAMP NOT NULL DEFAULT now()
);