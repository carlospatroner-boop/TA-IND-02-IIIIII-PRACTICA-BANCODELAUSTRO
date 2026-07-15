-- Datos iniciales del nodo Cuenca

INSERT INTO clientes (cedula, nombre, ciudad) VALUES
    ('0101234567', 'Mario Vintimilla',  'Cuenca'),
    ('0107654321', 'Sofia Vasquez',     'Cuenca'),
    ('0103456789', 'Andres Cordero',    'Cuenca');

INSERT INTO cuentas (numero, cliente_id, saldo, oficina) VALUES
    ('2201000001', 1, 15000.00, 'CUENCA'),
    ('2201000002', 2,  4200.50, 'CUENCA'),
    ('2201000003', 3,   850.75, 'CUENCA');

INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, oficina) VALUES
    ('2201000001', '2201000002', 500.00, 'CUENCA'),
    ('2201000002', '2201000003', 120.25, 'CUENCA');
