package co.colmacar.pedidos.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "tmcategorias")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pkcodcategoria")
    private Integer id;

    @Column(name = "dcategoria", nullable = false, length = 40)
    private String nombre;

    @Column(name = "imagen_categoria", length = 150)
    private String imagen;

    protected Categoria() {
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getImagen() { return imagen; }
}
