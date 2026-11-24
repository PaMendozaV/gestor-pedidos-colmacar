package co.colmacar.pedidos.seguridad;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Formulario de registro de clientes (clase con getters/setters para el binding de Thymeleaf). */
public class RegistroForm {

    @NotBlank(message = "El usuario es obligatorio")
    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Usuario: 3 a 50 caracteres (letras, numeros, . _ -)")
    private String username;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Nombre: maximo 100 caracteres")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo no valido")
    @Size(max = 120, message = "Correo: maximo 120 caracteres")
    private String email;

    @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "Telefono no valido")
    private String telefono;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "Contrasena: 8 a 72 caracteres")
    private String password;

    private String confirmacion;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? null : username.trim(); }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre == null ? null : nombre.trim(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim().toLowerCase(); }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono == null ? null : telefono.trim(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
}
