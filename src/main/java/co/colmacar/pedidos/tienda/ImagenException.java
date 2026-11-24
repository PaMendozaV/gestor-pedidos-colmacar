package co.colmacar.pedidos.tienda;

/** Imagen invalida o error al subirla a ImageKit (mensaje apto para mostrar al admin). */
public class ImagenException extends RuntimeException {
    public ImagenException(String mensaje) {
        super(mensaje);
    }
}
