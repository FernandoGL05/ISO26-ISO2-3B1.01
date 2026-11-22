
## D-001 · Agente para implementar
Fecha: 13/10 · It. 0 · Acta: 13/10
...
 
## D-002 · Estructura Maven multimódulo
Fecha: 19/10 · It. 0 · Acta: 19/10 · Issue: #12
Contexto: hay dos aplicaciones, servidor y
  cliente, en un mismo repositorio.
Decisión: pom padre radiodiagnostico con dos
  módulos: servidor y cliente.
Alternativas: dos repositorios; un solo módulo.
Motivo: un mvn verify construye todo y las
  versiones se fijan una vez en el padre.
Consecuencias: cada módulo con su versión; al
  cambiar la del padre, actualizar <parent>.


## D-003 · Gestión de agendas diarias (CU17)
Fecha: 22/11/2026 · Responsable: Ernesto
Rama: feature/CU17-agendas-diarias

Contexto:
Necesitamos que el personal de admisión pueda gestionar
las agendas diarias de los médicos y sus huecos.

Decisión:
Separar el código en dominio, persistencia, servicio y API,
siguiendo la estructura del proyecto. El cliente se comunica
con el servidor mediante HTTP. Los datos se guardan en memoria.

Se permite crear y consultar agendas, y añadir, consultar,
modificar y eliminar huecos. Se comprueba que no haya agendas
duplicadas para un médico y fecha ni huecos solapados.
También se validan los datos y el permiso de admisión.

Alternativas:
Usar una base de datos o añadir un framework web.
De momento seguimos el mismo enfoque que los CU anteriores
para facilitar la integración.

Uso de IA:
Se ha utilizado ChatGPT para ayudar con el código y las pruebas.
Este prompt resume lo pedido durante la conversación:

“Implementa el CU17, Gestionar agendas diarias, con Java 21
y los módulos Maven cliente y servidor. Sigue la estructura
y el estilo de los casos de uso existentes.

Permite crear y consultar agendas por médico y fecha, y gestionar
sus huecos horarios. Valida los datos, evita agendas duplicadas
y solapamientos, y exige el rol de admisión mediante un autorizador
que podamos conectar después al login común.

Utiliza repositorios en memoria y comunicación HTTP.
Incluye pruebas del servicio, API, cliente e integración.
Indica las rutas de los archivos y explica el código paso a paso.
Al finalizar, comprueba todo con mvn -B clean verify.”

Pruebas:
mvn -B clean verify → BUILD SUCCESS.
178 pruebas en total, sin fallos, errores ni pruebas omitidas.
36 pruebas nuevas corresponden al CU17.

Pendiente:
Conectar con las citas del CU16, los horarios del CU22
y la autenticación común. Los datos se pierden al cerrar
el servidor y todavía no se controla si un hueco tiene una cita.
El cliente HTTP no incluye interfaz gráfica.