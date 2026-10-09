-- Capacidad: add-dental-chart — el sellado se protege también en la persistencia (design.md D9).
--
-- Hasta acá la inmutabilidad del odontograma sellado vivía en el servicio (bloqueo + comprobación
-- de OPEN) y en un CHECK de coherencia (status ↔ sealed_at). Ninguno impedía, a nivel de dato, un
-- UPDATE que devolviera un bloque sellado a OPEN, que reescribiera su sealed_at, o que tocara sus
-- hallazgos: una ruta olvidada, un save() accidental o SQL manual lo esquivaban. Estos triggers
-- hacen de "sellado" un hecho de la base:
--   - un bloque SEALED no se modifica ni se borra (ni reapertura, ni segundo sellado, ni texto);
--   - los hallazgos y las piezas de rango de un bloque SEALED no se insertan, modifican ni borran.
-- OPEN → SEALED sigue permitido: en ese UPDATE la fila vieja todavía es OPEN.
--
-- SQLSTATE 55000 (object_not_in_prerequisite_state). Es la última barrera: el servicio rechaza
-- antes con DentalChartBlockSealedException, así que un usuario normal nunca llega acá.
--
-- SECURITY INVOKER (por defecto): las consultas al bloque corren con la RLS de la sesión, que es
-- la misma organización del hallazgo (FK compuesta por tenant, V18/V19).
--
-- Cuando ADR-0014 D4 revoque UPDATE/DELETE al rol de aplicación, esto sigue valiendo como defensa
-- para el rol owner. No se contradicen.

CREATE FUNCTION dental_chart_block_seal_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'SEALED' THEN
        RAISE EXCEPTION 'El odontograma % está sellado: no se modifica ni se borra (ADR-0012 D3)', OLD.id
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dental_chart_block_seal_guard
    BEFORE UPDATE OR DELETE ON dental_chart_block
    FOR EACH ROW EXECUTE FUNCTION dental_chart_block_seal_guard();

-- Un hallazgo pertenece a un bloque sellado si lo es su bloque actual (OLD) o el de destino (NEW).
CREATE FUNCTION dental_chart_finding_seal_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    sealed_block BIGINT;
BEGIN
    SELECT b.id INTO sealed_block
      FROM dental_chart_block b
     WHERE b.status = 'SEALED'
       AND b.id IN (
           CASE WHEN TG_OP <> 'INSERT' THEN OLD.block_id END,
           CASE WHEN TG_OP <> 'DELETE' THEN NEW.block_id END)
     LIMIT 1;
    IF sealed_block IS NOT NULL THEN
        RAISE EXCEPTION 'El odontograma % está sellado: sus hallazgos no se modifican (ADR-0012 D3)', sealed_block
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dental_chart_finding_seal_guard
    BEFORE INSERT OR UPDATE OR DELETE ON dental_chart_finding
    FOR EACH ROW EXECUTE FUNCTION dental_chart_finding_seal_guard();

-- Las piezas de un rango: sellado si lo está el bloque de su hallazgo.
CREATE FUNCTION dental_chart_finding_tooth_seal_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    sealed_block BIGINT;
BEGIN
    SELECT b.id INTO sealed_block
      FROM dental_chart_finding f
      JOIN dental_chart_block b ON b.id = f.block_id
     WHERE b.status = 'SEALED'
       AND f.id IN (
           CASE WHEN TG_OP <> 'INSERT' THEN OLD.finding_id END,
           CASE WHEN TG_OP <> 'DELETE' THEN NEW.finding_id END)
     LIMIT 1;
    IF sealed_block IS NOT NULL THEN
        RAISE EXCEPTION 'El odontograma % está sellado: las piezas de sus rangos no se modifican (ADR-0012 D3)', sealed_block
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_dental_chart_finding_tooth_seal_guard
    BEFORE INSERT OR UPDATE OR DELETE ON dental_chart_finding_tooth
    FOR EACH ROW EXECUTE FUNCTION dental_chart_finding_tooth_seal_guard();
