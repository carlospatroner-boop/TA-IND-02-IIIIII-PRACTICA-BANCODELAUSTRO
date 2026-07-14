# Ejercicios propuestos — Práctica Banco del Austro (Unidad 2, ISR-701)

Este documento resume cómo se resolvió cada ejercicio propuesto en la sección 14.3 de la guía,
y responde la pregunta de discusión (ejercicio 4).

## Ejercicio 1 — Nodo Guayaquil (prefijo 09)

Implementado de punta a punta:

- `docker-compose.yml`: nuevo servicio `db-guayaquil` (imagen `postgres:16-alpine`, contenedor
  `banco-guayaquil`, puerto host `5434`, volumen `vol-guayaquil`, monta `./sql-guayaquil`).
- `sql-guayaquil/01_schema.sql` y `02_datos.sql`: mismo esquema que Cuenca/Quito, con
  `CHECK (oficina = 'GUAYAQUIL')` y datos de prueba con cuentas `0901000001..03`.
- `application.yml`: bloque `datasources.guayaquil` apuntando a `localhost:5434/banco_guayaquil`.
- `DataSourceConfig.java`: bean `dsGuayaquil` (y su `PlatformTransactionManager` `txGuayaquil`,
  necesario para el ejercicio 2).
- `ConsultaDistribuidaService.java`: el enrutador (`enrutar`) agrega `case "09" -> guayaquil`, y
  `listarTodosLosClientes` ahora consulta los tres nodos.

Con esto, `GET /api/banco/saldo/0901000001` enruta a Guayaquil igual que `22.../17...` enrutan a
Cuenca/Quito, sin cambiar ninguna línea del controlador REST.

## Ejercicio 2 — `POST /api/banco/transferencia`

Nuevo endpoint en `BancoController` + lógica en `ConsultaDistribuidaService.transferir(...)`,
recibiendo `{ "origen": "...", "destino": "...", "monto": 100.00 }`.

Se distinguen dos casos:

- **Misma sede** (origen y destino enrutan al mismo nodo): débito, crédito e inserción del
  registro en `transacciones` ocurren dentro de **una sola transacción local** (`TransactionTemplate`
  sobre el `PlatformTransactionManager` del nodo), así que es atómica en el sentido ACID clásico.

- **Cross-site** (origen y destino en sedes distintas): la guía deja explícitamente el *commit en
  dos fases* (2PC) para una práctica posterior, así que aquí se implementó una alternativa más
  simple y honesta con esa limitación: un **saga con compensación**.
  1. Se debita la cuenta origen y se inserta la transacción como `PENDIENTE_CROSS_SITE`, todo en
     una transacción local del nodo origen.
  2. Se intenta acreditar la cuenta destino e insertar su registro `COMPLETADA` en una transacción
     local del nodo destino.
  3. Si el paso 2 falla (nodo destino caído, cuenta inexistente, etc.), se **compensa**: se vuelve
     a acreditar el monto en el origen y se marca la transacción como `REVERTIDA`.
  4. Si el paso 2 tiene éxito, se marca la transacción de origen como `COMPLETADA`.

  Esto no es 2PC real: existe una ventana (entre el paso 1 y el paso 2) en la que el dinero está
  "debitado pero aún no acreditado en ningún lado" de forma visible si alguien consulta en ese
  instante exacto. Es la misma discusión que la guía deja pendiente para la Unidad 3; se documenta
  aquí para que quede explícito en la entrega.

## Ejercicio 3 — Fallback en `listarTodosLosClientes`

`ConsultaDistribuidaService.listarTodosLosClientes()` ahora recorre los tres nodos dentro de un
`try/catch` individual por nodo, en lugar de un único `SELECT` por nodo sin protección. Si un nodo
falla, se omite y se agrega un mensaje a una lista `advertencias`. La respuesta del endpoint
`GET /api/banco/clientes` cambió de un arreglo plano a:

```json
{
  "clientes": [ ... ],
  "nodosConsultados": 3,
  "nodosConErrores": 1,
  "advertencias": ["No se pudo consultar el nodo QUITO (...): resultados incompletos."]
}
```

Si todos los nodos responden, `advertencias` simplemente no aparece.

## Ejercicio 4 — Discusión: ¿por qué `clientes` está replicada y no fragmentada?

**Por qué replicada en el diseño actual.** El requisito de negocio es "cualquier oficina debe
poder identificar al titular de una cuenta aunque pertenezca a otra sede", y las consultas de
saldo deben responder en menos de 200 ms. Si `clientes` estuviera fragmentada por oficina de
origen (como `cuentas`), cualquier consulta que una oficina hiciera sobre un cliente que no es
"suyo" —por ejemplo, Quito verificando el titular de una cuenta que participa en una transferencia
cross-site con Cuenca— requeriría una consulta remota al nodo dueño de ese cliente, con la latencia
de red y el riesgo de que ese nodo esté caído (rompiendo la autonomía local). Replicar evita eso:
toda lectura de `clientes` es local. El costo se traslada a la escritura (alta un cliente nuevo, o
actualiza sus datos) porque hay que propagar el cambio a todas las copias, pero como el patrón de
acceso es mucho más de lectura que de escritura (los clientes se registran una vez y se consultan
constantemente), ese costo es aceptable. En términos de CAP/PACELC, el diseño prioriza
disponibilidad y latencia de lectura (baja "EL", *even when there's no partition, favor low
Latency*) sobre consistencia inmediata entre copias.

**Qué cambiaría con un millón de titulares.**

- *Almacenamiento:* con filas pequeñas (cédula, nombre, ciudad, timestamp), un millón de clientes
  son a lo sumo unos cientos de MB por copia; replicarlos en 5 sedes sigue siendo trivial para
  PostgreSQL. El volumen de datos por sí solo no obliga a cambiar de estrategia todavía.

- *Costo de escritura y frescura:* lo que sí se vuelve un problema es la tasa de altas/actualizaciones
  y qué tan "fresca" debe estar cada copia. La práctica actual, si se implementara réplica de
  escritura, lo haría con `INSERT` manual duplicado; a esa escala hace falta replicación lógica real
  (p. ej. `logical replication` de PostgreSQL, o un bus de eventos tipo Debezium/Kafka) que propague
  cambios de forma asíncrona y tolere que, por unos segundos, una sede vea una versión desactualizada
  del cliente (consistencia eventual en vez de fuerte).

- *Conflictos de escritura concurrente:* con cinco sedes escribiendo (o pudiendo escribir) sobre el
  mismo cliente, aparece la necesidad de resolver conflictos (última escritura gana, vector clocks,
  o forzar que solo la sede "dueña" del cliente pueda modificarlo y las demás solo lean réplicas).

- *Alternativa híbrida:* en vez de replicar la fila completa en todos lados, se podría mantener un
  **directorio ligero replicado** (id, cédula, nombre, sede propietaria) —suficiente para resolver
  "¿quién es el titular y a qué sede pertenece?"— y dejar los atributos pesados o sensibles
  (dirección, documentos KYC, historial) fragmentados en el nodo de origen, consultados bajo demanda
  solo cuando se necesitan. Esto es, en esencia, el enrutamiento por tabla de directorio que la
  guía menciona como alternativa a los prefijos de cuenta.

- *Migración de producto:* a esa escala, con más sedes y más presión de consistencia, es cuando
  empieza a tener sentido evaluar un motor distribuido nativo (PostgreSQL + Citus, CockroachDB)
  que maneje la replicación y el particionamiento de forma transparente, en lugar de que la
  aplicación Spring Boot siga haciendo el enrutamiento a mano.

## Cómo se compone el proyecto entregado

```
banco-austro/
├── docker-compose.yml          (3 nodos: Cuenca, Quito, Guayaquil)
├── sql-cuenca/   01_schema.sql, 02_datos.sql
├── sql-quito/    01_schema.sql, 02_datos.sql
├── sql-guayaquil/01_schema.sql, 02_datos.sql   (Ejercicio 1)
├── pom.xml
└── src/main/
    ├── resources/application.yml
    └── java/ec/edu/uteq/bancoaustro/
        ├── BancoAustroApplication.java
        ├── config/DataSourceConfig.java        (3 DataSource + 3 TransactionManager)
        ├── dto/TransferenciaRequest.java        (Ejercicio 2)
        ├── service/ConsultaDistribuidaService.java  (Ejercicios 1, 2, 3)
        └── controller/BancoController.java      (Ejercicio 2)
```

Para levantarlo: seguir los pasos 6 y 7 de la guía original (`docker compose up -d` desde esta
carpeta), abrir el proyecto en IntelliJ como proyecto Maven existente, y ejecutar
`BancoAustroApplication`. Los endpoints nuevos:

- `POST /api/banco/transferencia` con body `{"origen":"2201000001","destino":"1701000001","monto":50}`
- `GET /api/banco/clientes` (ahora con `advertencias` si algún nodo está caído)
- `GET /api/banco/saldo/0901000001` (nuevo prefijo Guayaquil)
