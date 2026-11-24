package co.colmacar.pedidos.dominio;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Producto del catalogo (tmproductos). Baja logica con fkcods = 0 (ELIMINADO). */
@Entity
@Table(name = "tmproductos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcodproducto")
    private Integer id;

    @Column(name = "nombre_p", nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "precio_mayor", precision = 10, scale = 2)
    private BigDecimal precioMayor;

    @Column(name = "imagen_produ", length = 255)
    private String imagen;

    @Column(name = "fkcods", nullable = false)
    private Integer estado = Usuario.ACTIVO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fkcodcategoria")
    private Categoria categoria;

    protected Producto() {
    }

    public Producto(String nombre, BigDecimal precio, BigDecimal precioMayor, String imagen, Categoria categoria) {
        this.nombre = nombre;
        this.precio = precio;
        this.precioMayor = precioMayor;
        this.imagen = imagen;
        this.categoria = categoria;
    }

    /** true si la cantidad alcanza el umbral (colmacar.mayorista.umbral) y hay precio_mayor. */
    public boolean aplicaMayorista(int cantidad, int umbral) {
        return precioMayor != null && cantidad >= umbral;
    }

    /** Precio unitario segun la cantidad. */
    public BigDecimal precioPara(int cantidad, int umbral) {
        return aplicaMayorista(cantidad, umbral) ? precioMayor : precio;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public BigDecimal getPrecioMayor() { return precioMayor; }
    public void setPrecioMayor(BigDecimal precioMayor) { this.precioMayor = precioMayor; }
    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }
    public boolean isActivo() { return estado != null && estado == Usuario.ACTIVO; }
    public void setActivo(boolean activo) { this.estado = activo ? Usuario.ACTIVO : Usuario.ELIMINADO; }
}
