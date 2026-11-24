package co.colmacar.pedidos.seguridad;

import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fija la clave del admin desde ADMIN_CLAVE_INICIAL solo si hace falta:
 * - la cuenta no existe -> se crea con rol ADMIN;
 * - existe pero su hash no es BCrypt (scrypt heredado de Flask) o esta inactiva -> se reemplaza y se activa.
 * Una clave BCrypt ya valida NUNCA se sobrescribe, asi la variable se puede dejar puesta sin riesgo.
 */
@Component
@Order(1)
public class InicializadorAdmin implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorAdmin.class);
    static final int LONGITUD_MINIMA = 8;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String usuario;
    private final String claveInicial;

    public InicializadorAdmin(UsuarioRepository usuarios, PasswordEncoder encoder,
                              @Value("${colmacar.admin.usuario:admin}") String usuario,
                              @Value("${colmacar.admin.clave-inicial:}") String claveInicial) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.usuario = usuario;
        this.claveInicial = claveInicial;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var existente = usuarios.findByUsername(usuario);
        boolean listo = existente.map(u -> u.isActivo() && esBcrypt(u.getPassword())).orElse(false);
        if (listo) {
            return;
        }
        if (claveInicial == null || claveInicial.length() < LONGITUD_MINIMA) {
            LOG.warn("El admin '{}' no puede iniciar sesion: define ADMIN_CLAVE_INICIAL (minimo {} caracteres)",
                    usuario, LONGITUD_MINIMA);
            return;
        }
        if (existente.isEmpty()) {
            usuarios.save(new Usuario(usuario, encoder.encode(claveInicial), Rol.ADMIN));
            LOG.info("Admin '{}' creado con la clave inicial", usuario);
            return;
        }
        Usuario u = existente.get();
        if (u.getRol() != Rol.ADMIN) {
            LOG.error("'{}' existe con rol {}: no se le asigna la clave de admin", usuario, u.getRol());
            return;
        }
        u.setPassword(encoder.encode(claveInicial));
        u.setActivo(true);
        usuarios.save(u);
        LOG.info("Clave del admin '{}' migrada a BCrypt y cuenta activada", usuario);
    }

    static boolean esBcrypt(String hash) {
        return hash != null && hash.matches("^\\$2[aby]?\\$\\d{2}\\$.{53}$");
    }
}
