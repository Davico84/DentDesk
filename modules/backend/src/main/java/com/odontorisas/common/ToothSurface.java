package com.odontorisas.common;

/**
 * Las siete superficies dentales de NTS 188 (columna {@code surface} de {@code
 * dental_chart_finding}: {@code M/D/V/P/L/O/I}), usadas para localizar un hallazgo de alcance
 * {@link LocationKind#SURFACE}.
 *
 * <p>Mesial, distal, vestibular, palatino y lingual son comunes a toda pieza; oclusal aplica solo
 * a piezas posteriores e incisal solo a anteriores — ver {@link FdiTooth#applicableSurfaces(int)}
 * (design.md D7). El código no gatea palatino/lingual por arco: la norma no distingue esa regla
 * en el catálogo (6.1.1–6.1.38), así que ambas quedan disponibles para cualquier pieza.
 */
public enum ToothSurface {
    MESIAL("M"),
    DISTAL("D"),
    VESTIBULAR("V"),
    PALATINO("P"),
    LINGUAL("L"),
    OCLUSAL("O"),
    INCISAL("I");

    private final String code;

    ToothSurface(String code) {
        this.code = code;
    }

    /** El código de una letra que persiste la columna {@code surface} de la base de datos. */
    public String code() {
        return code;
    }
}
