-- V3: datos de entrega capturados en /checkout (se validan antes de CONFIRMAR_PEDIDO).
ALTER TABLE ttpedido
    ADD COLUMN nombre_contacto   varchar(100),
    ADD COLUMN telefono_contacto varchar(20),
    ADD COLUMN direccion_entrega varchar(200),
    ADD COLUMN nota              varchar(300);
