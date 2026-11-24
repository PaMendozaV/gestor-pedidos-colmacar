package co.colmacar.pedidos.dominio;

import co.colmacar.pedidos.automata.Rol;
import jakarta.persistence.*;

/** Usuario unificado (tabla tmusuario del catalogo). SISTEMA nunca tiene usuario. */
@Entity
@Table(name = "tmusuario")
public class Usuario {

    public static final int ACTIVO = 1;
    public static final int ELIMINADO = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcodusuario")
    private Integer id;

    @Column(name = "nom_usuario", nullable = false, unique = true, length = 50)
    private String username;

    /** Hash BCrypt. */
    @Column(name = "con_usuario", nullable = false, length = 260)
    private String password;

    @Column(length = 120)
    private String email;

    @Column(length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    /** FK a tmstatus: 1 ACTIVO, 0 ELIMINADO. */
    @Column(name = "fkcods", nullable = false)
    private Integer estado = ACTIVO;

    protected Usuario() {
    }

    public Usuario(String username, String passwordHash, Rol rol) {
        if (rol == Rol.SISTEMA) {
            throw new IllegalArgumentException("SISTEMA es interno y no puede tener usuario");
        }
        this.username = username;
        this.password = passwordHash;
        this.rol = rol;
    }

    public Integer getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public void setPassword(String passwordHash) { this.password = passwordHash; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return estado != null && estado == ACTIVO; }
    public void setActivo(boolean activo) { this.estado = activo ? ACTIVO : ELIMINADO; }
}
