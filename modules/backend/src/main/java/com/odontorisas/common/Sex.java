package com.odontorisas.common;

/**
 * Sexo del paciente, como <b>conjunto cerrado</b> (no texto libre). Se persiste por su nombre
 * ({@code @Enumerated(STRING)}) y la columna lleva un {@code CHECK} — el conjunto queda enforced
 * en la app, en la base de datos y en el contrato (de ahí baja al cliente como enum). Es opcional
 * en la ficha (puede quedar sin especificar).
 */
public enum Sex {
    MALE,
    FEMALE,
    OTHER
}
