# Registro de decisiones
 
## D-001 · Agente para implementar
- **Fecha:** 13/10 · It. 0 · Acta: 13/10
- ...
## D-002 · Estructura Maven multimódulo
- **Fecha:** 19/10 · It. 0 · Acta: 19/10 · Issue: #12
- **Contexto:** hay dos aplicaciones, servidor y cliente, en un mismo repositorio.
- **Decisión:** pom padre `radiodiagnostico` con dos módulos: `servidor` y `cliente`.
- **Alternativas:** dos repositorios; un solo módulo.
- **Motivo:** un `mvn verify` construye todo y las versiones se fijan una vez en el padre.
- **Consecuencias:** cada módulo con su versión; al cambiar la del padre, actualizar `<parent>`.

## D-003 · Implementación de casos de uso CU02-CU09
- **Fecha:** 22/11/2026
- **Contexto:** hay que implementar funcionalidades para pacientes y médicos en el sistema.
- **Decisión:** desarrollar de forma independiente:
  - CU02: gestión de citas
  - CU03: notificaciones
  - CU04: historial de pruebas
  - CU05: incidencias
  - CU06: encuestas de satisfacción
  - CU07: consulta de seguimientos
  - CU08: prescripción de pruebas
  - CU09: prescripción de seguimientos periódicos
- **Alternativas:** implementar todas las funcionalidades conjuntamente.
- **Motivo:** facilitar el trabajo en equipo, las pruebas y la revisión del código.
- **Consecuencias:** cada caso de uso dispone de sus propias clases y pruebas. Los CU02-CU05 están integrados en `develop`. Los CU06-CU09 tienen pull requests abiertas.

## D-004 · Organización de paquetes
- **Fecha:** 22/11/2026
- **Contexto:** el proyecto necesita una estructura clara que facilite el mantenimiento.
- **Decisión:** organizar el servidor en los paquetes `dominio`, `persistencia`, `servicio` y `api`. Separar los clientes HTTP en paquetes específicos para cada caso de uso.
- **Alternativas:** colocar todas las clases en un único paquete.
- **Motivo:** separar las responsabilidades y evitar dependencias innecesarias.
- **Consecuencias:** código más organizado, reutilizable y fácil de probar.

## D-005 · Persistencia mediante repositorios
- **Fecha:** 22/11/2026
- **Contexto:** es necesario guardar y consultar información de los distintos casos de uso.
- **Decisión:** utilizar interfaces de repositorio con implementaciones en memoria.
- **Alternativas:** conectar directamente una base de datos.
- **Motivo:** simplificar el desarrollo inicial y permitir pruebas independientes.
- **Consecuencias:** los datos se pueden gestionar durante la ejecución, pero queda pendiente incorporar persistencia definitiva.

## D-006 · Autenticación y autorización
- **Fecha:** 22/11/2026
- **Contexto:** pacientes y médicos deben acceder únicamente a la información autorizada.
- **Decisión:** utilizar interfaces de autorización inyectables y comprobar los roles y la identidad de cada usuario.
- **Alternativas:** implementar autenticación independiente en cada caso de uso.
- **Motivo:** evitar duplicar funcionalidades y facilitar la integración con el CU01.
- **Consecuencias:** se pueden probar los permisos de acceso, pero queda pendiente conectar los módulos con la autenticación real.

## D-007 · Separación de prescripciones y citas
- **Fecha:** 22/11/2026
- **Contexto:** una prueba prescrita, una prueba realizada y una cita son conceptos distintos.
- **Decisión:** separar las prescripciones diagnósticas del CU08 y los seguimientos periódicos del CU09 de la gestión de citas.
- **Alternativas:** utilizar una única entidad para prescripciones, pruebas y citas.
- **Motivo:** evitar mezclar responsabilidades médicas y administrativas.
- **Consecuencias:** el CU09 genera necesidades de nuevas citas sin asignar directamente una fecha, sala o profesional. Queda pendiente integrar estos procesos con los módulos de seguimiento y citas.

## D-008 · Pruebas y control de versiones
- **Fecha:** 22/11/2026
- **Contexto:** varios integrantes desarrollan funcionalidades en un mismo repositorio.
- **Decisión:** utilizar Java 21, Maven y JUnit para verificar el código con `mvn clean verify`. Trabajar en ramas `feature` y utilizar pull requests y GitHub Actions.
- **Alternativas:** realizar pruebas manuales y fusionar directamente los cambios.
- **Motivo:** detectar errores antes de integrar el código y facilitar su revisión.
- **Consecuencias:** los casos de uso tienen pruebas automatizadas. Se corrigió un test no determinista en el CU06. Quedan pendientes las pruebas conjuntas después de integrar todas las ramas.
## D-009 · Consulta de disponibilidad de agendas médicas
- **Fecha:** 22/11/2026
- **Contexto:** el servicio debe permitir consultar la disponibilidad de las agendas diarias de trabajo de los médicos para organizar pruebas radiológicas e imagen médica.
- **Decisión:** ofrecer al personal médico una consulta de disponibilidad por tipo de prueba, delimitada por un intervalo de un único día y con la duración de cada hueco indicada en la consulta. La identidad del médico se obtiene de la sesión autenticada. Las citas activas asociadas a ese médico ocupan huecos; las canceladas no.
- **Alternativas:** consultar agendas de pacientes o recursos técnicos; guardar turnos y duraciones como configuración previa de cada agenda.
- **Motivo:** habilitar una primera consulta respetando el modelo de citas existente y restringir la operación al personal médico.
- **Consecuencias:** las citas deben estar asociadas a un médico para afectar a su disponibilidad. Las citas antiguas sin esa asociación no se atribuyen a ninguna agenda. Queda pendiente persistir horarios de trabajo y duraciones específicas por tipo de prueba.

## D-010 · Informes de pruebas e imágenes adjuntas
- **Fecha:** 22/11/2026
- **Contexto:** el CDU11 requiere crear y consultar informes de pruebas diagnósticas e incorporar opcionalmente una imagen.
- **Decisión:** vincular cada informe a una prueba realizada y guardar hallazgos, conclusión y fecha de creación asignada por el servidor. El médico autor puede crearlo y adjuntar una imagen; el paciente propietario y el médico autor pueden consultarlo.
- **Alternativas:** permitir consulta a cualquier usuario médico o almacenar referencias externas a las imágenes.
- **Motivo:** mantener el informe asociado al historial de la prueba y limitar la exposición de información clínica a las identidades relacionadas.
- **Consecuencias:** en esta versión se admiten imágenes PNG o JPEG de hasta 5 MiB en memoria; los datos se pierden al reiniciar. Quedan pendientes la persistencia duradera, formatos de imagen médica como DICOM y la integración del autorizador con la autenticación real.

## D-011 · Registrar realización o incidencia de una prueba
- **Fecha:** 22/11/2026
- **Contexto:** es necesario registrar el resultado de las pruebas programadas y dejar constancia cuando no pueden completarse.
- **Decisión:** el médico asignado a la cita registra la prueba realizada o una incidencia de realización. La realización actualiza el estado de la cita y crea una entrada en el historial del paciente; la incidencia se registra en el CU05 y mantiene la cita sin marcar como realizada.
- **Alternativas:** permitir registrar resultados a cualquier médico; guardar las incidencias en un módulo separado del CU05.
- **Motivo:** vincular el registro clínico a la cita y reutilizar el seguimiento de incidencias ya disponible, evitando duplicar su gestión.
- **Consecuencias:** las citas canceladas o asignadas a otro médico no admiten registro de resultado. No se puede registrar dos veces una realización para la misma cita. Los datos se almacenan en memoria y la sesión médica se valida mediante el autorizador inyectado.

## D-012 · Configuración administrativa: tipos de prueba (CU20), protocolos (CU21) y horarios (CU22)
- **Fecha:** 24/11/2026
- **Contexto:** el actor Administrador del sistema necesita configurar el catálogo de tipos de prueba y sus atributos (`RF20`/`CU20`), los protocolos clínicos/técnicos (`RF21`/`CU21`) y los horarios operativos de salas, equipos y médicos (`RF22`/`CU22`), integrando estos datos con el modelo existente (`PrioridadPrueba`, `ServicioAgendas`) y siguiendo versionado SemVer 2.0.0.
- **Decisión:**
  - Implementar `TipoPrueba` (CU20, reutilizando `PrioridadPrueba` y validando consentimiento informado obligatorio si requiere contraste), `Protocolo` (CU21, validando existencia del `tipoPruebaId` de CU20 y con versionado incremental al actualizar) y `HorarioOperativo` junto con `TipoRecursoHorario` (`SALA`, `EQUIPO`, `MEDICO`, CU22, impidiendo solapamientos en horarios activos del mismo recurso y día).
  - Organizar el código en las capas `dominio`, `persistencia` (repositorios en memoria *thread-safe*), `servicio`, `api` (handlers HTTP/REST protegidos con `AutorizadorAdministrador`) y clientes HTTP en `cliente` (`cu20`, `cu21`, `cu22`).
  - Integrar CU20 y CU22 en `ServicioAgendas` añadiendo el constructor sobrecargado y `consultarDisponibilidadConfigurada` sin romper la compatibilidad hacia atrás.
  - Aplicar incremento **MINOR** según **SemVer 2.0.0** al tratarse de nueva funcionalidad compatible hacia atrás: `radiodiagnostico` pasa de `0.1.0-SNAPSHOT` a `0.2.0-SNAPSHOT`, y `servidor` y `cliente` pasan de `1.0.0-SNAPSHOT` a `1.1.0-SNAPSHOT`.
- **Alternativas:** crear módulos Maven separados o duplicar el cálculo de huecos fuera de `ServicioAgendas`.
- **Motivo:** respetar la arquitectura multimódulo (`D-002`) y la organización de paquetes (`D-004`), mantener compatibilidad con los CU02–CU12 existentes y reutilizar `ServicioAgendas` y `PrioridadPrueba`.
- **Consecuencias:** la administración del catálogo, protocolos y horarios queda cubierta de extremo a extremo (dominio, servicio, REST y cliente HTTP) con pruebas JUnit y cobertura JaCoCo verificada; la persistencia definitiva y la conexión con el autorizador de sesiones real quedan preparadas tras las interfaces de repositorio y `AutorizadorAdministrador`.
- **Prompt utilizado:**
  > Actua como ingeniero de software espeficifaco en software administrativo.
  > ---
  > 1. Analiza el estado actual del repositorio "https://github.com/FernandoGL05/ISO26-ISO2-3B1.01"  
  > 2. Implementa los casos de uso 20, 21 y 22 que estan en su respectivo apartado de la wiki (te proporciono imagen por si acaso)con cuidado de que quede todo bien integrado con lo que ya hay actualmente implementado en el proyecto.
  > 3. Genera los Test pertinentes para Maven y JUnit
  > 4. Actualiza el Desicions.md con este prompt 
  >    ---
  > # Finalmente
  > Cuando termines, documenta todo lo que has hecho siguiendo las correctas normas y sube el código al repositorio con pull request teniendo en cuenta la normativa de branches y de versionado con la normativa SemVer 2.0.0

 