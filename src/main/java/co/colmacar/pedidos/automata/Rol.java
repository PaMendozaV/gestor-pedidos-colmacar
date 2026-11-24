package co.colmacar.pedidos.automata;

import java.util.Optional;

/**
 * Quien puede emitir un evento.
 * SISTEMA es interno (pasarela de pagos y bodega): nunca se acepta desde la API HTTP.
 * ADMIN solo supervisa: no emite ningun evento del alfabeto.
 */
public enum Rol {
    CLIENTE,
    ADMIN,
    REPARTIDOR,
    SISTEMA;

    public static Optional<Rol> desde(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Rol.valueOf(texto.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
