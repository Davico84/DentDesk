package com.odontorisas.common;

import java.util.EnumSet;
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

    /** Posición dentro del cuadrante (1–3 en permanentes y temporales) que separa anteriores de
     *  posteriores — incisivos y caninos vs. premolares y molares. */
    private static final int LAST_ANTERIOR_POSITION = 3;

    private FdiTooth() {
    }

    public static boolean isValid(int fdiNumber) {
        return VALID.contains(fdiNumber);
    }

    /**
     * {@code true} si la pieza es anterior (incisivo o canino): posición 1–3 del cuadrante, igual
     * en dentición permanente y temporal.
     */
    public static boolean isAnterior(int fdiNumber) {
        if (!isValid(fdiNumber)) {
            throw new IllegalArgumentException("Número FDI inválido: " + fdiNumber);
        }
        return (fdiNumber % 10) <= LAST_ANTERIOR_POSITION;
    }

    /**
     * Las superficies que aplican a una pieza (design.md D7, extiende lo construido en vez de
     * crear un catálogo nuevo): mesial, distal, vestibular, palatino y lingual son comunes a toda
     * pieza; incisal solo aplica a piezas anteriores y oclusal solo a posteriores.
     *
     * @throws IllegalArgumentException si {@code fdiNumber} no es una pieza real
     */
    public static Set<ToothSurface> applicableSurfaces(int fdiNumber) {
        Set<ToothSurface> surfaces = EnumSet.of(
            ToothSurface.MESIAL, ToothSurface.DISTAL, ToothSurface.VESTIBULAR,
            ToothSurface.PALATINO, ToothSurface.LINGUAL);
        surfaces.add(isAnterior(fdiNumber) ? ToothSurface.INCISAL : ToothSurface.OCLUSAL);
        return surfaces;
    }

    private static Set<Integer> quadrant(int quadrant, int teethInQuadrant) {
        return IntStream.rangeClosed(1, teethInQuadrant)
            .map(tooth -> quadrant * 10 + tooth)
            .boxed()
            .collect(Collectors.toSet());
    }
}
