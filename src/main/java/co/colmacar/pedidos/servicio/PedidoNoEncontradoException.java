package co.colmacar.pedidos.servicio;

public class PedidoNoEncontradoException extends RuntimeException {
    public PedidoNoEncontradoException(String codigo) {
        super("No existe el pedido " + codigo);
    }
}
