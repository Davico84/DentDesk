package com.odontorisas.common;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * El conjunto <b>real</b> de números FDI de piezas dentales (notación universal), para validar
 * un diagnóstico ubicado en {@code PIEZA} contra piezas que de verdad existen — no contra un
 * rango numérico laxo, que aceptaría números como {@code 19}/{@code 20}/{@code 29} que no
 * corresponden a ninguna pieza (design.md D5, `add-clinical-history`).
 *
 * <ul>
 *   <li>Permanentes: cuadrantes 1–4, piezas 1–8 (11–18, 21–28, 31–38, 41–48).</li>
 *   <li>Temporales: cuadrantes 5–8, piezas 1–5 (51–55, 61–65, 71–75, 81–85).</li>
 * </ul>
 */
public final class FdiTooth {

    public static final Set<Integer> VALID = Set.copyOf(
        java.util.stream.Stream.of(
                quadrant(1, 8), quadrant(2, 8), quadrant(3, 8), quadrant(4, 8),
                quadrant(5, 5), quadrant(6, 5), quadrant(7, 5), quadrant(8, 5))
            .flatMap(Set::stream)
            .collect(Collectors.toSet()));

    private FdiTooth() {
    }

    public static boolean isValid(int fdiNumber) {
        return VALID.contains(fdiNumber);
    }

    private static Set<Integer> quadrant(int quadrant, int teethInQuadrant) {
        return IntStream.rangeClosed(1, teethInQuadrant)
            .map(tooth -> quadrant * 10 + tooth)
            .boxed()
            .collect(Collectors.toSet());
    }
}
