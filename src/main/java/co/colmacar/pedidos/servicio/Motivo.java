package co.colmacar.pedidos.servicio;

/** Resultado de intentar aplicar un evento. */
public enum Motivo {
    ACEPTADO,
    /** El rol que lo envia no es el autorizado para ese evento. */
    NO_AUTORIZADO,
    /** El rol es correcto, pero delta no esta definida para ese estado y evento. */
    TRANSICION_NO_DEFINIDA,
    /** delta lo permite, pero ENTREGA_CONFIRMADA llego sin el codigo de entrega correcto. */
    CODIGO_ENTREGA_INVALIDO,
    /** CONFIRMAR_PEDIDO sin lineas, con productos dados de baja o sin datos de entrega. */
    PEDIDO_INVALIDO
}
