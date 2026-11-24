package co.colmacar.pedidos.seguridad;

import co.colmacar.pedidos.automata.Rol;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/** Quien hace la peticion HTTP: el rol sale SIEMPRE del usuario autenticado, nunca de un encabezado. */
public record UsuarioActual(String username, Rol rol) {

    public static Optional<UsuarioActual> de(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Rol.desde(a.substring(5)))
                .flatMap(Optional::stream)
                .findFirst()
                .map(r -> new UsuarioActual(auth.getName(), r));
    }

    public boolean esCliente() {
        return rol == Rol.CLIENTE;
    }

    /** ADMIN y REPARTIDOR ven todos los pedidos; el CLIENTE solo los suyos. */
    public boolean puedeVer(String duenio) {
        return rol == Rol.ADMIN || rol == Rol.REPARTIDOR || (esCliente() && username.equals(duenio));
    }
}
