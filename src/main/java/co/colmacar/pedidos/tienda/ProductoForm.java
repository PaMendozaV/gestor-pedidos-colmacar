package co.colmacar.pedidos.tienda;

import co.colmacar.pedidos.dominio.Producto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Formulario de producto del admin (respaldo en servidor de la validacion del navegador). */
public class ProductoForm {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "Nombre: maximo 100 caracteres")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
    @DecimalMax(value = "99999999.99", message = "Precio demasiado alto")
    @Digits(integer = 8, fraction = 2, message = "Maximo 2 decimales")
    private BigDecimal precio;

    @DecimalMin(value = "0.01", message = "El precio mayorista debe ser mayor a 0")
    @DecimalMax(value = "99999999.99", message = "Precio demasiado alto")
    @Digits(integer = 8, fraction = 2, message = "Maximo 2 decimales")
    private BigDecimal precioMayor;

    private Integer categoriaId;

    private boolean disponible = true;

    public static ProductoForm de(Producto p) {
        ProductoForm f = new ProductoForm();
        f.nombre = p.getNombre();
        f.precio = p.getPrecio();
        f.precioMayor = p.getPrecioMayor();
        f.categoriaId = p.getCategoria() == null ? null : p.getCategoria().getId();
        f.disponible = p.isActivo();
        return f;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String v) { this.nombre = v == null ? null : v.trim(); }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal v) { this.precio = v; }
    public BigDecimal getPrecioMayor() { return precioMayor; }
    public void setPrecioMayor(BigDecimal v) { this.precioMayor = v; }
    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer v) { this.categoriaId = v; }
    public boolean isDisponible() { return disponible; }
    public void setDisponible(boolean v) { this.disponible = v; }
}
