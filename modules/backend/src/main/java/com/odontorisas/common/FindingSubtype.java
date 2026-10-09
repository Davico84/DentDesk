package com.odontorisas.common;

/**
 * Una variante de un {@link FindingType} que agrupa hallazgos bajo el mismo concepto con distinta
 * sigla según material, profundidad o causa (ADR-0017 D5) — la lesión de caries es {@code
 * MB}/{@code CE}/{@code CD}/{@code CDP}, la corona es {@code CM}/{@code CF}/{@code CMC}/{@code
 * CV}/{@code CLM}.
 *
 * <p>{@code code} es el valor que se persiste en la columna {@code subtype} (clave junto con el
 * tipo, {@code (type, subtype)}). {@code acronym} es la sigla oficial de NTS 188 — dato normativo,
 * se imprime exacta. En este catálogo coinciden en todos los casos, pero se declaran como campos
 * separados a propósito: son conceptos distintos (identidad vs. representación) que solo suceden
 * a tener el mismo valor.
 *
 * <p>NO es un enum global: la misma letra de código ({@code M}, {@code D}, {@code V}, {@code P},
 * {@code L}) aparece como subtipo de {@link FindingType#POSICION_ANORMAL} y también, con otro
 * significado, en la sigla de {@link FindingType#MOVILIDAD_PATOLOGICA} — un enum plano de siglas
 * colisionaría. Cada {@link FindingType} declara su propia lista de subtipos.
 */
public record FindingSubtype(String code, String acronym) {

    public FindingSubtype {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código del subtipo no puede estar vacío");
        }
        if (acronym == null || acronym.isBlank()) {
            throw new IllegalArgumentException("La sigla del subtipo no puede estar vacía");
        }
    }
}
