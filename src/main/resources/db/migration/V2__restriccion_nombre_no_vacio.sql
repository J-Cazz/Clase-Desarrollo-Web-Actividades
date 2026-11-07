-- Refuerza a nivel de esquema que el nombre no puede ser cadena vacía o solo espacios.
-- No reescribe V1: Flyway nunca modifica una migración ya aplicada.
ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);