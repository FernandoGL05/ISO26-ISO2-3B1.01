
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

## D-003 · Implementación de CU01 (Iniciar sesión) con agente IA
Fecha: 19/11 · It. 1 · Caso de uso: CU01 (RF01)
Contexto: se necesita implementar el primer caso de uso del sistema
  (CU01 — Iniciar sesión) siguiendo la arquitectura cliente-servidor
  acordada en la wiki (Acta 3) y la estructura Maven multimódulo (D-002),
  cubriendo a todos los actores (Paciente, Médico, Admisión y Administrador)
  y preparando la integración con el SSO corporativo (RT05).
Decisión: implementar CU01 en Java 21 sin dependencias externas de
  ejecución, con API HTTP/REST + JSON en el módulo `servidor`
  (`com.sun.net.httpserver.HttpServer`), cliente HTTP y controlador en el
  módulo `cliente` (`java.net.http.HttpClient`), contraseñas con
  PBKDF2WithHmacSHA256, bloqueo temporal por intentos fallidos, auditoría
  de accesos, puerto `ProveedorSSO` y 12 tests JUnit verificados con
  `mvn -B verify`.
Alternativas: usar Spring Boot o sockets TCP propietarios.
Motivo: evitar sobrecarga de dependencias en esta fase inicial, garantizar
  compilación rápida en CI y mantener un protocolo HTTP/REST estándar
  compatible con clientes de escritorio, web y móvil (RT02-RT04).
Consecuencias: base reutilizable para el resto de casos de uso
  autenticados y para la auditoría (RF25); la persistencia en memoria y el
  SSO simulado podrán sustituirse por implementaciones reales sin cambiar
  `ServicioAutenticacion`.
Prompts utilizados:
1. "Analiza este repositorio \"https://github.com/FernandoGL05/ISO26-ISO2-3B1.01\" implementa el primer caso de uso que está en su respectivo apartado de la wiki (te proporciono imagen por si acaso) y sube el código al repositorio"
2. "A continuación quiero que:
3. Analiza por qué he tenido que subirlo yo a mano y corrijelo, seguramente sea alguna cosa de windows.
4. Documenta todo el proceso del código que has hecho.
5. Escribe en el decisions.md todos los prompts que te he dado (incluyendo este)."

