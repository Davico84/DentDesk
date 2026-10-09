package com.odontorisas.common;

/**
 * Los campos de texto de la nota de consulta (design.md D3) — y, por eso mismo, los únicos que
 * admiten corrección de error material (design.md D6, DS 009-2017-SA art. 47 c). Los diagnósticos
 * no figuran: el art. 47 c los excluye. Las fechas tampoco (riesgo asumido en el design).
 */
public enum NoteTextField {
    REASON,
    ILLNESS_DURATION,
    SYMPTOMS_AND_SIGNS,
    PREVIOUS_TREATMENT,
    BIOLOGICAL_FUNCTIONS,
    EXAMINATION,
    TREATMENT,
    AUXILIARY_EXAMS,
    REFERRAL
}
