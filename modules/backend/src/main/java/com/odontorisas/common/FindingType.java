package com.odontorisas.common;

import java.util.List;

import static com.odontorisas.common.ColorBehavior.SEGUN_ESTADO;
import static com.odontorisas.common.ColorBehavior.SIEMPRE_AZUL;
import static com.odontorisas.common.ColorBehavior.SIEMPRE_ROJO;
import static com.odontorisas.common.LocationKind.ARCH;
import static com.odontorisas.common.LocationKind.BETWEEN_TEETH;
import static com.odontorisas.common.LocationKind.SURFACE;
import static com.odontorisas.common.LocationKind.TOOTH;
import static com.odontorisas.common.LocationKind.TOOTH_RANGE;

/**
 * El catálogo cerrado de 38 hallazgos del odontograma, NTS N°188-MINSA/DGIESP-2022 numerales
 * 6.1.1–6.1.38 (ADR-0017 D5/D7). Verificado <b>entrada por entrada</b> contra el texto oficial
 * (tarea {@code 0.7}, 2026-09-23; PDF versionado en {@code docs/compliance/normas/}).
 *
 * <p><b>Atributos solo en código</b> (design.md D6): {@code colorBehavior}, {@code scope},
 * {@code requiresGrade}, {@code requiresSpecification} y los subtipos son derivables del texto y
 * se desincronizarían si vivieran también en columnas. La <b>integridad</b> del par {@code
 * (type, subtype)} la garantiza {@code dental_finding_catalog} (V16), cargada por migración; este
 * enum es la fuente de los <b>tipos</b> que el dominio conoce, no al revés — el código no lee el
 * catálogo para saber qué tipos existen.
 *
 * <p><b>La sigla NO identifica un hallazgo.</b> Colisiona en el catálogo oficial: {@code M} es la
 * sigla de {@link #MOVILIDAD_PATOLOGICA} (roja, con grado) y también el subtipo {@code M} de
 * {@link #POSICION_ANORMAL} (azul, mesializado); {@code S} es la sigla de {@link
 * #PIEZA_SUPERNUMERARIA} y también de {@link #SELLANTE}. La clave es siempre {@code (type,
 * subtype)}.
 *
 * <p><b>Dos typos son de la propia norma</b>, no de este catálogo: la pieza en erupción se
 * imprime <i>"5.2.23"</i> (pág. 14 del texto oficial) y el implante dental <i>"6.115"</i> (pág.
 * 11) — el numeral corregido por posición es {@code 6.1.23} y {@code 6.1.15} respectivamente. Esos
 * dos numerales (impreso y corregido) se guardan en {@code dental_finding_catalog}, no en este
 * enum, que no necesita el numeral para su comportamiento.
 */
public enum FindingType {

    APARATO_ORTODONTICO_FIJO(TOOTH_RANGE, SEGUN_ESTADO, false, false, null, List.of()),
    APARATO_ORTODONTICO_REMOVIBLE(ARCH, SEGUN_ESTADO, false, false, null, List.of()),

    /** 6.1.3 — cinco materiales: metálica, fenestrada, metal-cerámica, veneer, libre de metal. */
    CORONA(TOOTH, SEGUN_ESTADO, false, true, null, List.of(
        new FindingSubtype("CM", "CM"),
        new FindingSubtype("CF", "CF"),
        new FindingSubtype("CMC", "CMC"),
        new FindingSubtype("CV", "CV"),
        new FindingSubtype("CLM", "CLM"))),

    /** 6.1.4 — SIEMPRE roja: lo temporal no es un eje, es un tipo propio (ADR-0017 D1). */
    CORONA_TEMPORAL(TOOTH, SIEMPRE_ROJO, false, true, "CT", List.of()),

    /** 6.1.5 — opacidades (O) y pigmentación/fluorosis (PE), detallada en especificaciones. */
    DEFECTOS_DESARROLLO_ESMALTE(SURFACE, SIEMPRE_ROJO, false, true, null, List.of(
        new FindingSubtype("O", "O"),
        new FindingSubtype("PE", "PE"))),

    /** 6.1.6 — entre piezas: no tiene número FDI propio. */
    DIASTEMA(BETWEEN_TEETH, SIEMPRE_AZUL, false, false, null, List.of()),

    EDENTULO_TOTAL(ARCH, SIEMPRE_AZUL, false, false, null, List.of()),
    ESPIGO_MUNON(TOOTH, SEGUN_ESTADO, false, false, null, List.of()),
    FOSAS_FISURAS_PROFUNDAS(TOOTH, SIEMPRE_AZUL, false, false, "FFP", List.of()),
    FRACTURA_DENTAL(TOOTH, SIEMPRE_ROJO, false, false, null, List.of()),

    /** 6.1.11 — entre piezas: dos piezas unidas. */
    FUSION(BETWEEN_TEETH, SIEMPRE_AZUL, false, false, null, List.of()),

    GEMINACION(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),

    /** 6.1.13 — AZUL, SIN CONDICIONAL. La "regla de contagio" que pintaría este hallazgo según el
     *  estado de los demás de la pieza NO existe en la norma (ADR-0017 D2, corrige ADR-0012 D2). */
    GIROVERSION(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),

    IMPACTACION(TOOTH, SIEMPRE_AZUL, false, false, "I", List.of()),

    /** 6.115 [sic, por 6.1.15] — COEXISTE con la ausencia de la pieza, no la implica: solo se
     *  registra cuando se observa clínicamente el implante (ADR-0017 D7). */
    IMPLANTE_DENTAL(TOOTH, SEGUN_ESTADO, false, false, "IMP", List.of()),

    /** 6.1.16 — por profundidad: mancha blanca, esmalte, dentina, dentina con compromiso pulpar.
     *  Alcance dual (ADR-0017 D7): el hallazgo es de SUPERFICIE; la sigla del recuadro de la pieza
     *  se deriva al renderizar, sin cambiar este modelo. */
    LESION_CARIES(SURFACE, SIEMPRE_ROJO, false, false, null, List.of(
        new FindingSubtype("MB", "MB"),
        new FindingSubtype("CE", "CE"),
        new FindingSubtype("CD", "CD"),
        new FindingSubtype("CDP", "CDP"))),

    MACRODONCIA(TOOTH, SIEMPRE_AZUL, false, false, "MAC", List.of()),
    MICRODONCIA(TOOTH, SIEMPRE_AZUL, false, false, "MIC", List.of()),

    /** 6.1.19 — ROJA, no azul: corrige a {@code ADR-0013}, que la había cerrado como azul citando
     *  un análisis derivado (ADR-0017, lección registrada). Único tipo con {@code requiresGrade}. */
    MOVILIDAD_PATOLOGICA(TOOTH, SIEMPRE_ROJO, true, false, "M", List.of()),

    /** 6.1.20 — la causa como subtipo: no erupcionado, extracción por caries, por otra razón. */
    PIEZA_AUSENTE(TOOTH, SIEMPRE_AZUL, false, false, null, List.of(
        new FindingSubtype("DNE", "DNE"),
        new FindingSubtype("DEX", "DEX"),
        new FindingSubtype("DAO", "DAO"))),

    PIEZA_EN_CLAVIJA(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),
    PIEZA_ECTOPICA(TOOTH, SIEMPRE_AZUL, false, false, "E", List.of()),

    /** 5.2.23 [sic, por 6.1.23] — segundo typo de la norma (pág. 14: aparece entre 6.1.22 y
     *  6.1.24, fuera de su propia secuencia numérica). */
    PIEZA_EN_ERUPCION(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),

    /** 6.1.24 — AZUL, no roja: el análisis derivado que ADR-0017 corrigió la daba en rojo. */
    PIEZA_EXTRUIDA(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),

    /** 6.1.25 — AZUL, no roja: mismo error corregido que la extruida. */
    PIEZA_INTRUIDA(TOOTH, SIEMPRE_AZUL, false, false, null, List.of()),

    /** 6.1.26 — entre piezas: se localiza entre los ápices de las piezas adyacentes, sin número
     *  FDI propio. Su sigla colisiona con {@link #SELLANTE}. */
    PIEZA_SUPERNUMERARIA(BETWEEN_TEETH, SIEMPRE_AZUL, false, false, "S", List.of()),

    PULPOTOMIA(TOOTH, SEGUN_ESTADO, false, false, "PP", List.of()),

    /** 6.1.28 — la dirección como subtipo. Las siglas coinciden con letras de superficie
     *  (M/D/V/P/L) y NO lo son: acá 'M' es mesializado, no mesial — el alcance TOOTH las
     *  desambigua de {@link ToothSurface}. */
    POSICION_ANORMAL(TOOTH, SIEMPRE_AZUL, false, false, null, List.of(
        new FindingSubtype("M", "M"),
        new FindingSubtype("D", "D"),
        new FindingSubtype("V", "V"),
        new FindingSubtype("P", "P"),
        new FindingSubtype("L", "L"))),

    /** 6.1.29 — RANGO: los pilares son dato (el orden se conserva, no se normaliza como en
     *  {@code BETWEEN_TEETH}). */
    PROTESIS_PARCIAL_FIJA(TOOTH_RANGE, SEGUN_ESTADO, false, false, null, List.of()),

    PROTESIS_COMPLETA(ARCH, SEGUN_ESTADO, false, false, null, List.of()),

    /** 6.1.31 — verificado el 2026-09-23 (tarea 0.7): la matriz y las tasks del change llegaron a
     *  atribuir este numeral a "recesión gingival" por error; es la prótesis parcial REMOVIBLE. */
    PROTESIS_PARCIAL_REMOVIBLE(TOOTH_RANGE, SEGUN_ESTADO, false, false, null, List.of()),

    REMANENTE_RADICULAR(TOOTH, SIEMPRE_ROJO, false, false, "RR", List.of()),

    /** 6.1.33 — el material como subtipo: amalgama, resina, ionómero de vidrio, incrustación
     *  metálica/estética, carilla. */
    RESTAURACION_DEFINITIVA(SURFACE, SEGUN_ESTADO, false, false, null, List.of(
        new FindingSubtype("AM", "AM"),
        new FindingSubtype("R", "R"),
        new FindingSubtype("IV", "IV"),
        new FindingSubtype("IM", "IM"),
        new FindingSubtype("IE", "IE"),
        new FindingSubtype("C", "C"))),

    RESTAURACION_TEMPORAL(SURFACE, SIEMPRE_ROJO, false, false, null, List.of()),

    /** 6.1.35 — COEXISTE con una caries en la misma superficie (ADR-0017 D7): son dos hallazgos,
     *  uno no sustituye al otro. Su sigla colisiona con {@link #PIEZA_SUPERNUMERARIA}. */
    SELLANTE(SURFACE, SEGUN_ESTADO, false, false, "S", List.of()),

    /** 6.1.36 — alcance dual (ADR-0017 D7): el hallazgo es de SUPERFICIE; la sigla {@code DES}
     *  del recuadro de la pieza se deriva al renderizar. */
    SUPERFICIE_DESGASTADA(SURFACE, SIEMPRE_ROJO, false, false, "DES", List.of()),

    /** 6.1.37 — tratamiento de conducto (TC) y pulpectomía (PC). */
    TRATAMIENTO_CONDUCTO(TOOTH, SEGUN_ESTADO, false, false, null, List.of(
        new FindingSubtype("TC", "TC"),
        new FindingSubtype("PC", "PC"))),

    /** 6.1.38 — entre piezas: dos piezas que intercambiaron su posición. */
    TRANSPOSICION_DENTARIA(BETWEEN_TEETH, SIEMPRE_AZUL, false, false, null, List.of());

    private final LocationKind scope;
    private final ColorBehavior colorBehavior;
    private final boolean requiresGrade;
    private final boolean requiresSpecification;
    /** Sigla propia cuando el tipo NO tiene subtipos; {@code null} si los tiene (la sigla vive
     *  entonces en cada {@link FindingSubtype}). */
    private final String acronym;
    private final List<FindingSubtype> subtypes;

    FindingType(LocationKind scope, ColorBehavior colorBehavior, boolean requiresGrade,
                boolean requiresSpecification, String acronym, List<FindingSubtype> subtypes) {
        this.scope = scope;
        this.colorBehavior = colorBehavior;
        this.requiresGrade = requiresGrade;
        this.requiresSpecification = requiresSpecification;
        this.acronym = acronym;
        this.subtypes = subtypes;
    }

    /** El alcance normativo: determina qué variante de {@code Location} admite este tipo. */
    public LocationKind scope() {
        return scope;
    }

    public ColorBehavior colorBehavior() {
        return colorBehavior;
    }

    /** Solo {@link #MOVILIDAD_PATOLOGICA} lo exige (6.1.19: sigla + dígito de grado). */
    public boolean requiresGrade() {
        return requiresGrade;
    }

    /** El tipo obliga a llenar una entrada en el campo Especificaciones del bloque (ADR-0017 D6). */
    public boolean requiresSpecification() {
        return requiresSpecification;
    }

    public boolean hasSubtypes() {
        return !subtypes.isEmpty();
    }

    public List<FindingSubtype> subtypes() {
        return subtypes;
    }

    /**
     * La sigla oficial cuando el tipo no tiene subtipos, o {@code null} si el tipo exige elegir un
     * subtipo (y la sigla vive en {@link FindingSubtype#acronym()}). Ninguna de las dos formas es
     * única entre tipos — ver la nota de la clase sobre colisiones.
     */
    public String acronym() {
        return acronym;
    }
}
