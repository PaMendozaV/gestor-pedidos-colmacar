package co.colmacar.pedidos.seguridad;

import co.colmacar.pedidos.automata.Rol;
import co.colmacar.pedidos.dominio.Usuario;
import co.colmacar.pedidos.repositorio.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

/** Alta de clientes. El rol es SIEMPRE CLIENTE: nadie se registra como ADMIN o REPARTIDOR. */
@Service
public class RegistroService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public RegistroService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    /** Agrega errores al BindingResult; si no hay, crea el usuario. Devuelve true si se registro. */
    @Transactional
    public boolean registrar(RegistroForm f, BindingResult errores) {
        if (f.getPassword() != null && !f.getPassword().equals(f.getConfirmacion())) {
            errores.rejectValue("confirmacion", "distinta", "Las contrasenas no coinciden");
        }
        if (f.getUsername() != null && usuarios.existsByUsernameIgnoreCase(f.getUsername())) {
            errores.rejectValue("username", "duplicado", "Ese usuario ya existe");
        }
        if (f.getEmail() != null && usuarios.existsByEmailIgnoreCase(f.getEmail())) {
            errores.rejectValue("email", "duplicado", "Ese correo ya esta registrado");
        }
        if (errores.hasErrors()) {
            return false;
        }
        Usuario u = new Usuario(f.getUsername(), encoder.encode(f.getPassword()), Rol.CLIENTE);
        u.setNombre(f.getNombre());
        u.setEmail(f.getEmail());
        u.setTelefono(f.getTelefono() == null || f.getTelefono().isBlank() ? null : f.getTelefono());
        usuarios.save(u);
        return true;
    }
}
