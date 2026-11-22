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
 