package com.odontorisas.service.auth;

/**
 * Datos de registro que la presentación pasa al servicio (ya validados en el DTO). Además de
 * la identidad, lleva el nombre de la organización que se crea en el mismo alta.
 */
public record RegisterCommand(String token, String email, String password, String fullName) {}
