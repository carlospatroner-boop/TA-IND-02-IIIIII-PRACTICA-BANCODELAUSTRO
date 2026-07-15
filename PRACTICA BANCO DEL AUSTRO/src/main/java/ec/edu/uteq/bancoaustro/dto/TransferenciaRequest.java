package ec.edu.uteq.bancoaustro.dto;

import java.math.BigDecimal;

/**
 * Cuerpo esperado por POST /api/banco/transferencia (Ejercicio propuesto 2).
 */
public class TransferenciaRequest {

    private String origen;
    private String destino;
    private BigDecimal monto;

    public TransferenciaRequest() {
    }

    public TransferenciaRequest(String origen, String destino, BigDecimal monto) {
        this.origen = origen;
        this.destino = destino;
        this.monto = monto;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }
}
