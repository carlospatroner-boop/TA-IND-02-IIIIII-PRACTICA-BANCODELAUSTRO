package ec.edu.uteq.bancoaustro.controller;

import ec.edu.uteq.bancoaustro.dto.TransferenciaRequest;
import ec.edu.uteq.bancoaustro.service.ConsultaDistribuidaService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/banco")
public class BancoController {

    private final ConsultaDistribuidaService service;

    public BancoController(ConsultaDistribuidaService service) {
        this.service = service;
    }

    @GetMapping("/saldo/{numero}")
    public Map<String, Object> saldo(@PathVariable String numero) {
        return service.consultarSaldo(numero);
    }

    // Ejercicio propuesto 3: ahora devuelve tambien advertencias si algun
    // nodo esta caido, en vez de fallar toda la peticion.
    @GetMapping("/clientes")
    public Map<String, Object> clientes() {
        return service.listarTodosLosClientes();
    }

    // Ejercicio propuesto 2: transferencia same-node y cross-site.
    @PostMapping("/transferencia")
    public ResponseEntity<Map<String, Object>> transferencia(@RequestBody TransferenciaRequest request) {
        Map<String, Object> resultado = service.transferir(request);
        if ("ERROR".equals(resultado.get("estado"))) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(resultado);
        }
        return ResponseEntity.ok(resultado);
    }
}
