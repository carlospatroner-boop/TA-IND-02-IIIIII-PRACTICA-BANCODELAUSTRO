-- Datos iniciales del nodo Quito

INSERT INTO clientes (cedula, nombre, ciudad) VALUES
    ('1701234567', 'Pedro Jimenez',   'Quito'),
    ('1709876543', 'Luisa Paredes',   'Quito'),
    ('1704567890', 'Carla Espinoza',  'Quito');

INSERT INTO cuentas (numero, cliente_id, saldo, oficina) VALUES
    ('1701000001', 1, 8000.00, 'QUITO'),
    ('1701000002', 2, 2500.00, 'QUITO'),
    ('1701000003', 3,  975.40, 'QUITO');

INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, oficina) VALUES
    ('1701000001', '1701000002', 300.00, 'QUITO'),
    ('1701000003', '1701000001',  75.00, 'QUITO');
