package co.colmacar.pedidos.tienda;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos de entrega pedidos en /checkout. */
public class CheckoutForm {

    @NotBlank(message = "El nombre de contacto es obligatorio")
    @Size(max = 100, message = "Nombre: maximo 100 caracteres")
    private String nombre;

    @NotBlank(message = "El telefono es obligatorio")
    @Pattern(regexp = "^[0-9+ ()-]{7,20}$", message = "Telefono no valido")
    private String telefono;

    @NotBlank(message = "La direccion de entrega es obligatoria")
    @Size(min = 8, max = 200, message = "Direccion: 8 a 200 caracteres")
    private String direccion;

    @Size(max = 300, message = "Nota: maximo 300 caracteres")
    private String nota;

    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v == null ? null : v.trim(); }
    public String getTelefono() { return telefono; }
    public void setTelefono(String v) { this.telefono = v == null ? null : v.trim(); }
    public String getDireccion() { return direccion; }
    public void setDireccion(String v) { this.direccion = v == null ? null : v.trim(); }
    public String getNota() { return nota; }
    public void setNota(String v) { this.nota = v == null || v.isBlank() ? null : v.trim(); }
}
