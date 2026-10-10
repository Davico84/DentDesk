-- Capacidad: update-clinical-history — el sellado de la nota de consulta es un hecho de la base
-- (design.md D2/D6/D7), mismo patrón que V20 para el odontograma:
--   - una nota SELLADA no se modifica ni se borra (ni reapertura, ni segundo sellado, ni texto);
--   - los diagnósticos y los antecedentes de una nota SELLADA no se insertan, modifican ni borran;
--   - una corrección no se modifica ni se borra NUNCA: es el registro nuevo del art. 47 c.
-- ABIERTA -> SELLADA sigue permitido: en ese UPDATE la fila vieja todavía es ABIERTA.
--
-- SQLSTATE 55000 (object_not_in_prerequisite_state). Es la última barrera: el servicio rechaza
-- antes con ConsultationNoteSealedException.

CREATE FUNCTION consultation_note_seal_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'SELLADA' THEN
        RAISE EXCEPTION 'La nota de consulta % está sellada: no se modifica ni se borra (ADR-0020 D2)', OLD.id
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_consultation_note_seal_guard
    BEFORE UPDATE OR DELETE ON consultation_note
    FOR EACH ROW EXECUTE FUNCTION consultation_note_seal_guard();

-- Diagnósticos y antecedentes: sellado si lo está la nota actual (OLD) o la de destino (NEW).
CREATE FUNCTION consultation_note_child_seal_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    sealed_note BIGINT;
BEGIN
    SELECT n.id INTO sealed_note
      FROM consultation_note n
     WHERE n.status = 'SELLADA'
       AND n.id IN (
           CASE WHEN TG_OP <> 'INSERT' THEN OLD.note_id END,
           CASE WHEN TG_OP <> 'DELETE' THEN NEW.note_id END)
     LIMIT 1;
    IF sealed_note IS NOT NULL THEN
        RAISE EXCEPTION 'La nota de consulta % está sellada: % no se modifica (ADR-0020 D2)', sealed_note, TG_TABLE_NAME
            USING ERRCODE = '55000';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_consultation_note_diagnosis_seal_guard
    BEFORE INSERT OR UPDATE OR DELETE ON consultation_note_diagnosis
    FOR EACH ROW EXECUTE FUNCTION consultation_note_child_seal_guard();

CREATE TRIGGER trg_clinical_background_entry_seal_guard
    BEFORE INSERT OR UPDATE OR DELETE ON clinical_background_entry
    FOR EACH ROW EXECUTE FUNCTION consultation_note_child_seal_guard();

-- La corrección solo se inserta, y solo sobre una nota sellada (no se corrige un borrador: se edita).
CREATE FUNCTION consultation_note_correction_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Una corrección no se modifica ni se borra (DS 009-2017-SA art. 47 c, ADR-0020 D5)'
            USING ERRCODE = '55000';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM consultation_note n WHERE n.id = NEW.note_id AND n.status = 'SELLADA') THEN
        RAISE EXCEPTION 'Solo se corrige una nota sellada (ADR-0020 D5)'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_consultation_note_correction_guard
    BEFORE INSERT OR UPDATE OR DELETE ON consultation_note_correction
    FOR EACH ROW EXECUTE FUNCTION consultation_note_correction_guard();
