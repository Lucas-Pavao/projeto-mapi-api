package com.projeto.mapi.model;

/** Perfis (ADR 0010). Hierarquia: ADMIN herda OPERADOR, que herda USER (ver SecurityConfig). */
public enum Role {
    USER,
    OPERADOR,
    ADMIN
}
