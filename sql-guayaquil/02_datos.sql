-- Datos iniciales del nodo Guayaquil

INSERT INTO clientes (cedula, nombre, ciudad) VALUES
    ('0901234567', 'Jose Alvarado',    'Guayaquil'),
    ('0909876543', 'Monica Zambrano',  'Guayaquil'),
    ('0904567890', 'Ricardo Bravo',    'Guayaquil');

INSERT INTO cuentas (numero, cliente_id, saldo, oficina) VALUES
    ('0901000001', 1, 12000.00, 'GUAYAQUIL'),
    ('0901000002', 2,  3300.00, 'GUAYAQUIL'),
    ('0901000003', 3,   690.10, 'GUAYAQUIL');

INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, oficina) VALUES
    ('0901000001', '0901000002', 200.00, 'GUAYAQUIL'),
    ('0901000002', '0901000003',  50.00, 'GUAYAQUIL');
