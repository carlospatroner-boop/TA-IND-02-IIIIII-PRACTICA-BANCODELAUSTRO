package ec.edu.uteq.bancoaustro.service;

import ec.edu.uteq.bancoaustro.dto.TransferenciaRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConsultaDistribuidaService {

    /**
     * Agrupa, por sede, todo lo necesario para operar contra ese nodo:
     * el nombre logico (usado como valor de la columna "oficina"), el
     * JdbcTemplate y un TransactionTemplate para operaciones atomicas
     * dentro del mismo nodo.
     */
    private record NodoBanco(String nombre, JdbcTemplate jdbc, TransactionTemplate tx) {
    }

    private final NodoBanco cuenca;
    private final NodoBanco quito;
    private final NodoBanco guayaquil;
    private final List<NodoBanco> todosLosNodos;

    public ConsultaDistribuidaService(
            @Qualifier("dsCuenca") DataSource dsCuenca,
            @Qualifier("dsQuito") DataSource dsQuito,
            @Qualifier("dsGuayaquil") DataSource dsGuayaquil,
            @Qualifier("txCuenca") PlatformTransactionManager txCuenca,
            @Qualifier("txQuito") PlatformTransactionManager txQuito,
            @Qualifier("txGuayaquil") PlatformTransactionManager txGuayaquil) {

        this.cuenca = new NodoBanco("CUENCA", new JdbcTemplate(dsCuenca), new TransactionTemplate(txCuenca));
        this.quito = new NodoBanco("QUITO", new JdbcTemplate(dsQuito), new TransactionTemplate(txQuito));
        this.guayaquil = new NodoBanco("GUAYAQUIL", new JdbcTemplate(dsGuayaquil), new TransactionTemplate(txGuayaquil));
        this.todosLosNodos = List.of(cuenca, quito, guayaquil);
    }

    // ---------------------------------------------------------------
    // Consulta simple de saldo (practica base)
    // ---------------------------------------------------------------

    public Map<String, Object> consultarSaldo(String numero) {
        NodoBanco destino = enrutar(numero);
        String sql = "SELECT numero, saldo, oficina FROM cuentas WHERE numero = ?";
        List<Map<String, Object>> filas = destino.jdbc().queryForList(sql, numero);
        if (filas.isEmpty()) {
            return Map.of(
                    "error", "Cuenta no encontrada",
                    "numero", numero);
        }
        return filas.get(0);
    }

    // ---------------------------------------------------------------
    // Ejercicio propuesto 3: union de fragmentos con fallback.
    // Si un nodo esta caido, se devuelven los clientes de los nodos
    // vivos junto con una advertencia, en vez de fallar toda la
    // peticion.
    // ---------------------------------------------------------------

    public Map<String, Object> listarTodosLosClientes() {
        String sql = "SELECT cedula, nombre, ciudad FROM clientes";
        List<Map<String, Object>> union = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        for (NodoBanco nodo : todosLosNodos) {
            try {
                union.addAll(nodo.jdbc().queryForList(sql));
            } catch (Exception ex) {
                advertencias.add("No se pudo consultar el nodo " + nodo.nombre()
                        + " (" + ex.getClass().getSimpleName() + "): resultados incompletos.");
            }
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("clientes", union);
        respuesta.put("nodosConsultados", todosLosNodos.size());
        respuesta.put("nodosConErrores", advertencias.size());
        if (!advertencias.isEmpty()) {
            respuesta.put("advertencias", advertencias);
        }
        return respuesta;
    }

    // ---------------------------------------------------------------
    // Ejercicio propuesto 2: transferencia entre cuentas.
    // Caso simple: origen y destino en la misma sede -> una sola
    // transaccion local (atomica de verdad, ACID completo).
    // Caso cross-site: origen y destino en sedes distintas -> como el
    // 2PC real queda para una practica posterior, se implementa un saga
    // con compensacion: se debita en el origen, se intenta acreditar en
    // el destino y, si eso falla, se revierte el debito original.
    // ---------------------------------------------------------------

    public Map<String, Object> transferir(TransferenciaRequest req) {
        if (req.getOrigen() == null || req.getDestino() == null || req.getMonto() == null) {
            return error("Debe indicar origen, destino y monto.");
        }
        if (req.getOrigen().equals(req.getDestino())) {
            return error("La cuenta origen y destino no pueden ser la misma.");
        }
        if (req.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            return error("El monto debe ser mayor que cero.");
        }

        NodoBanco nodoOrigen;
        NodoBanco nodoDestino;
        try {
            nodoOrigen = enrutar(req.getOrigen());
            nodoDestino = enrutar(req.getDestino());
        } catch (IllegalArgumentException ex) {
            return error(ex.getMessage());
        }

        if (nodoOrigen.nombre().equals(nodoDestino.nombre())) {
            return transferirMismaSede(nodoOrigen, req);
        }
        return transferirEntreSedes(nodoOrigen, nodoDestino, req);
    }

    private Map<String, Object> transferirMismaSede(NodoBanco nodo, TransferenciaRequest req) {
        try {
            return nodo.tx().execute(status -> {
                int filasActualizadas = nodo.jdbc().update(
                        "UPDATE cuentas SET saldo = saldo - ? WHERE numero = ? AND saldo >= ?",
                        req.getMonto(), req.getOrigen(), req.getMonto());
                if (filasActualizadas == 0) {
                    throw new IllegalStateException(
                            "Fondos insuficientes o cuenta origen inexistente: " + req.getOrigen());
                }
                int destinoActualizado = nodo.jdbc().update(
                        "UPDATE cuentas SET saldo = saldo + ? WHERE numero = ?",
                        req.getMonto(), req.getDestino());
                if (destinoActualizado == 0) {
                    throw new IllegalStateException(
                            "Cuenta destino inexistente en la misma sede: " + req.getDestino());
                }
                nodo.jdbc().update(
                        "INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, estado, oficina) "
                                + "VALUES (?, ?, ?, 'COMPLETADA', ?)",
                        req.getOrigen(), req.getDestino(), req.getMonto(), nodo.nombre());

                Map<String, Object> resultado = new LinkedHashMap<>();
                resultado.put("estado", "COMPLETADA");
                resultado.put("tipo", "MISMA_SEDE");
                resultado.put("sede", nodo.nombre());
                resultado.put("origen", req.getOrigen());
                resultado.put("destino", req.getDestino());
                resultado.put("monto", req.getMonto());
                return resultado;
            });
        } catch (IllegalStateException ex) {
            return error(ex.getMessage());
        }
    }

    private Map<String, Object> transferirEntreSedes(NodoBanco nodoOrigen, NodoBanco nodoDestino,
            TransferenciaRequest req) {

        // Paso 1: debitar en el origen y dejar registrada la transaccion como PENDIENTE.
        try {
            nodoOrigen.tx().execute(status -> {
                int filas = nodoOrigen.jdbc().update(
                        "UPDATE cuentas SET saldo = saldo - ? WHERE numero = ? AND saldo >= ?",
                        req.getMonto(), req.getOrigen(), req.getMonto());
                if (filas == 0) {
                    throw new IllegalStateException(
                            "Fondos insuficientes o cuenta origen inexistente: " + req.getOrigen());
                }
                nodoOrigen.jdbc().update(
                        "INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, estado, oficina) "
                                + "VALUES (?, ?, ?, 'PENDIENTE_CROSS_SITE', ?)",
                        req.getOrigen(), req.getDestino(), req.getMonto(), nodoOrigen.nombre());
                return null;
            });
        } catch (IllegalStateException ex) {
            return error(ex.getMessage());
        }

        // Paso 2: intentar acreditar en el destino.
        try {
            nodoDestino.tx().execute(status -> {
                int filas = nodoDestino.jdbc().update(
                        "UPDATE cuentas SET saldo = saldo + ? WHERE numero = ?",
                        req.getMonto(), req.getDestino());
                if (filas == 0) {
                    throw new IllegalStateException(
                            "Cuenta destino inexistente: " + req.getDestino());
                }
                nodoDestino.jdbc().update(
                        "INSERT INTO transacciones (cuenta_orig, cuenta_dest, monto, estado, oficina) "
                                + "VALUES (?, ?, ?, 'COMPLETADA', ?)",
                        req.getOrigen(), req.getDestino(), req.getMonto(), nodoDestino.nombre());
                return null;
            });
        } catch (Exception ex) {
            // Paso 3 (compensacion): el destino fallo (nodo caido, cuenta inexistente,
            // etc.) -> se revierte el debito hecho en el origen (patron saga).
            compensarDebito(nodoOrigen, req);
            return error("La transferencia no se pudo completar en la sede destino ("
                    + nodoDestino.nombre() + "): " + ex.getMessage()
                    + ". Se revirtio el debito en " + nodoOrigen.nombre() + ".");
        }

        // Ambos pasos ok: se marca la transaccion de origen como completada.
        // El dinero ya se movio correctamente aunque esta actualizacion de
        // estado fallara, asi que un error aqui no debe revertir nada: solo
        // se ignora (el registro se queda en PENDIENTE_CROSS_SITE como rastro
        // de que el estado contable quedo desactualizado).
        try {
            nodoOrigen.tx().execute(status -> {
                nodoOrigen.jdbc().update(
                        "UPDATE transacciones SET estado = 'COMPLETADA' "
                                + "WHERE cuenta_orig = ? AND cuenta_dest = ? AND estado = 'PENDIENTE_CROSS_SITE'",
                        req.getOrigen(), req.getDestino());
                return null;
            });
        } catch (Exception ex) {
            // No se revierte: el dinero ya llego a destino. Solo se pierde el
            // marcado de estado en el nodo origen.
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("estado", "COMPLETADA");
        resultado.put("tipo", "CROSS_SITE");
        resultado.put("sedeOrigen", nodoOrigen.nombre());
        resultado.put("sedeDestino", nodoDestino.nombre());
        resultado.put("origen", req.getOrigen());
        resultado.put("destino", req.getDestino());
        resultado.put("monto", req.getMonto());
        return resultado;
    }

    private void compensarDebito(NodoBanco nodoOrigen, TransferenciaRequest req) {
        nodoOrigen.tx().execute(status -> {
            nodoOrigen.jdbc().update(
                    "UPDATE cuentas SET saldo = saldo + ? WHERE numero = ?",
                    req.getMonto(), req.getOrigen());
            nodoOrigen.jdbc().update(
                    "UPDATE transacciones SET estado = 'REVERTIDA' "
                            + "WHERE cuenta_orig = ? AND cuenta_dest = ? AND estado = 'PENDIENTE_CROSS_SITE'",
                    req.getOrigen(), req.getDestino());
            return null;
        });
    }

    private Map<String, Object> error(String mensaje) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("estado", "ERROR");
        resultado.put("error", mensaje);
        return resultado;
    }

    // ---------------------------------------------------------------
    // Enrutamiento por prefijo (transparencia de fragmentacion).
    // Ejercicio propuesto 1: se agrega el case "09" -> Guayaquil.
    // ---------------------------------------------------------------

    private NodoBanco enrutar(String numero) {
        if (numero == null || numero.length() < 2) {
            throw new IllegalArgumentException("Numero de cuenta invalido: " + numero);
        }
        String prefijo = numero.substring(0, 2);
        return switch (prefijo) {
            case "22" -> cuenca;
            case "17" -> quito;
            case "09" -> guayaquil;
            default -> throw new IllegalArgumentException(
                    "Prefijo de oficina no reconocido: " + prefijo);
        };
    }
}
