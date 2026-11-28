package es.uclm.esi.iso2.B101.servidor.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import es.uclm.esi.iso2.B101.servidor.dominio.ResultadoAutenticacion;
import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAgendas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAsignacionListaEspera;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioCitas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioEncuestasSatisfaccion;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioIncidencias;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioInformesPrueba;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioListaEspera;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioNotificaciones;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesDiagnosticas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioPrescripcionesSeguimiento;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioRealizacionPruebas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioSeguimientos;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioTiposPrueba;

/**
 * Servidor HTTP de la aplicacion de radiodiagnostico.
 */
public class ServidorHTTP {
    public static final int MAX_CUERPO_AUTENTICACION_BYTES = 16 * 1024;

    private final HttpServer server;
    private final ServicioAutenticacion servicioAutenticacion;

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion) throws IOException {
        this.servicioAutenticacion = servicioAutenticacion;
        this.server = HttpServer.create(
                new InetSocketAddress(
                        InetAddress.getByAddress(new byte[] {127, 0, 0, 1}),
                        puerto),
                0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        registrarRutas();
    }

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion,
            ServicioCitas servicioCitas,
            AutorizadorCitas autorizadorCitas,
            ServicioPrescripcionesDiagnosticas servicioPrescripciones,
            AutorizadorPrescripcionesDiagnosticas autorizadorPrescripciones)
            throws IOException {
        this(puerto, servicioAutenticacion);

        Cu02HttpHandler.registrarEn(
                server, servicioCitas, autorizadorCitas);
        PrescripcionesPruebasHttpHandler.registrarEn(
                server, servicioPrescripciones, autorizadorPrescripciones);
    }

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion,
            ServicioCitas servicioCitas,
            AutorizadorCitas autorizadorCitas,
            ServicioPrescripcionesDiagnosticas servicioPrescripciones,
            AutorizadorPrescripcionesDiagnosticas autorizadorPrescripciones,
            ServicioNotificaciones servicioNotificaciones,
            AutorizadorNotificaciones autorizadorNotificaciones,
            ServicioHistorialPruebas servicioHistorialPruebas,
            AutorizadorHistorialPruebas autorizadorHistorialPruebas,
            ServicioIncidencias servicioIncidencias,
            AutorizadorIncidencias autorizadorIncidencias) throws IOException {
        this(
                puerto, servicioAutenticacion,
                servicioCitas, autorizadorCitas,
                servicioPrescripciones, autorizadorPrescripciones);

        NotificacionesHttpHandler.registrarEn(
                server, servicioNotificaciones, autorizadorNotificaciones);
        HistorialPruebasHttpHandler.registrarEn(
                server, servicioHistorialPruebas, autorizadorHistorialPruebas);
        IncidenciasHttpHandler.registrarEn(
                server, servicioIncidencias, autorizadorIncidencias);
    }

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion,
            ServicioCitas servicioCitas,
            AutorizadorCitas autorizadorCitas,
            ServicioPrescripcionesDiagnosticas servicioPrescripciones,
            AutorizadorPrescripcionesDiagnosticas autorizadorPrescripciones,
            ServicioNotificaciones servicioNotificaciones,
            AutorizadorNotificaciones autorizadorNotificaciones,
            ServicioHistorialPruebas servicioHistorialPruebas,
            AutorizadorHistorialPruebas autorizadorHistorialPruebas,
            ServicioIncidencias servicioIncidencias,
            AutorizadorIncidencias autorizadorIncidencias,
            ServicioEncuestasSatisfaccion servicioEncuestas,
            AutorizadorEncuestas autorizadorEncuestas,
            ServicioSeguimientos servicioSeguimientos,
            AutorizadorSeguimientos autorizadorSeguimientos) throws IOException {
        this(
                puerto, servicioAutenticacion,
                servicioCitas, autorizadorCitas,
                servicioPrescripciones, autorizadorPrescripciones,
                servicioNotificaciones, autorizadorNotificaciones,
                servicioHistorialPruebas, autorizadorHistorialPruebas,
                servicioIncidencias, autorizadorIncidencias);

        EncuestasSatisfaccionHttpHandler.registrarEn(
                server, servicioEncuestas, autorizadorEncuestas);
        SeguimientosHttpHandler.registrarEn(
                server, servicioSeguimientos, autorizadorSeguimientos);
    }

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion,
            ServicioCitas servicioCitas,
            AutorizadorCitas autorizadorCitas,
            ServicioPrescripcionesDiagnosticas servicioPrescripciones,
            AutorizadorPrescripcionesDiagnosticas autorizadorPrescripciones,
            ServicioNotificaciones servicioNotificaciones,
            AutorizadorNotificaciones autorizadorNotificaciones,
            ServicioHistorialPruebas servicioHistorialPruebas,
            AutorizadorHistorialPruebas autorizadorHistorialPruebas,
            ServicioIncidencias servicioIncidencias,
            AutorizadorIncidencias autorizadorIncidencias,
            ServicioEncuestasSatisfaccion servicioEncuestas,
            AutorizadorEncuestas autorizadorEncuestas,
            ServicioSeguimientos servicioSeguimientos,
            AutorizadorSeguimientos autorizadorSeguimientos,
            ServicioPrescripcionesSeguimiento servicioPrescripcionesSeguimiento,
            AutorizadorPrescripcionesSeguimiento autorizadorPrescripcionesSeguimiento,
            ServicioAgendas servicioAgendas) throws IOException {
        this(
                puerto, servicioAutenticacion,
                servicioCitas, autorizadorCitas,
                servicioPrescripciones, autorizadorPrescripciones,
                servicioNotificaciones, autorizadorNotificaciones,
                servicioHistorialPruebas, autorizadorHistorialPruebas,
                servicioIncidencias, autorizadorIncidencias,
                servicioEncuestas, autorizadorEncuestas,
                servicioSeguimientos, autorizadorSeguimientos);

        PrescripcionesSeguimientoHttpHandler.registrarEn(
                server,
                servicioPrescripcionesSeguimiento,
                autorizadorPrescripcionesSeguimiento);
        AgendasHttpHandler.registrarEn(
                server, servicioAgendas, autorizadorPrescripciones);
    }

    public ServidorHTTP(
            int puerto,
            ServicioAutenticacion servicioAutenticacion,
            ServicioCitas servicioCitas,
            AutorizadorCitas autorizadorCitas,
            ServicioPrescripcionesDiagnosticas servicioPrescripciones,
            AutorizadorPrescripcionesDiagnosticas autorizadorPrescripciones,
            ServicioNotificaciones servicioNotificaciones,
            AutorizadorNotificaciones autorizadorNotificaciones,
            ServicioHistorialPruebas servicioHistorialPruebas,
            AutorizadorHistorialPruebas autorizadorHistorialPruebas,
            ServicioIncidencias servicioIncidencias,
            AutorizadorIncidencias autorizadorIncidencias,
            ServicioEncuestasSatisfaccion servicioEncuestas,
            AutorizadorEncuestas autorizadorEncuestas,
            ServicioSeguimientos servicioSeguimientos,
            AutorizadorSeguimientos autorizadorSeguimientos,
            ServicioPrescripcionesSeguimiento servicioPrescripcionesSeguimiento,
            AutorizadorPrescripcionesSeguimiento autorizadorPrescripcionesSeguimiento,
            ServicioAgendas servicioAgendas,
            ServicioInformesPrueba servicioInformesPrueba,
            AutorizadorInformesPrueba autorizadorInformesPrueba,
            ServicioRealizacionPruebas servicioRealizacionPruebas)
            throws IOException {
        this(
                puerto, servicioAutenticacion,
                servicioCitas, autorizadorCitas,
                servicioPrescripciones, autorizadorPrescripciones,
                servicioNotificaciones, autorizadorNotificaciones,
                servicioHistorialPruebas, autorizadorHistorialPruebas,
                servicioIncidencias, autorizadorIncidencias,
                servicioEncuestas, autorizadorEncuestas,
                servicioSeguimientos, autorizadorSeguimientos,
                servicioPrescripcionesSeguimiento,
                autorizadorPrescripcionesSeguimiento,
                servicioAgendas);

        InformesPruebaHttpHandler.registrarEn(
                server, servicioInformesPrueba, autorizadorInformesPrueba);
        RealizacionPruebasHttpHandler.registrarEn(
                server, servicioRealizacionPruebas, autorizadorPrescripciones);
    }

    public void registrarListaEspera(
            ServicioListaEspera gestion,
            ServicioAsignacionListaEspera asignacion,
            AutorizadorListaEspera autorizador) {
        ListaEsperaHttpHandler.registrarEn(
                server, gestion, asignacion, autorizador);
    }

    public void registrarTiposPrueba(
            ServicioTiposPrueba servicio,
            AutorizadorTiposPrueba autorizador) {
        TiposPruebaHttpHandler.registrarEn(server, servicio, autorizador);
    }

    private void registrarRutas() {
        server.createContext("/api/auth/login", this::manejarLogin);
        server.createContext("/api/auth/sso", this::manejarLoginSSO);
        server.createContext("/api/auth/session", this::manejarValidarSesion);
        server.createContext("/api/auth/logout", this::manejarLogout);
    }

    public void iniciar() {
        server.start();
    }

    public void detener() {
        server.stop(0);
    }

    public int getPuerto() {
        return server.getAddress().getPort();
    }

    public InetSocketAddress getDireccionEnlace() {
        return server.getAddress();
    }

    private void manejarLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            enviarError(
                    exchange, 405, "METODO_NO_PERMITIDO",
                    "Use POST para iniciar sesión.");
            return;
        }

        String cuerpo;
        try {
            cuerpo = leerCuerpoAutenticacion(exchange);
        } catch (CuerpoAutenticacionDemasiadoGrande error) {
            responderCuerpoAutenticacionDemasiadoGrande(exchange);
            return;
        }

        Map<String, String> datos = JsonUtil.parsearObjetoPlano(cuerpo);
        ResultadoAutenticacion resultado = servicioAutenticacion.iniciarSesion(
                datos.get("username"), datos.get("password"));

        responderAutenticacion(exchange, resultado);
    }

    private void manejarLoginSSO(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            enviarError(
                    exchange, 405, "METODO_NO_PERMITIDO",
                    "Use POST para iniciar sesión SSO.");
            return;
        }

        String cuerpo;
        try {
            cuerpo = leerCuerpoAutenticacion(exchange);
        } catch (CuerpoAutenticacionDemasiadoGrande error) {
            responderCuerpoAutenticacionDemasiadoGrande(exchange);
            return;
        }

        Map<String, String> datos = JsonUtil.parsearObjetoPlano(cuerpo);
        ResultadoAutenticacion resultado =
                servicioAutenticacion.iniciarSesionSSO(datos.get("ssoToken"));

        responderAutenticacion(exchange, resultado);
    }

    private String leerCuerpoAutenticacion(HttpExchange exchange)
            throws IOException, CuerpoAutenticacionDemasiadoGrande {
        String longitudDeclarada =
                exchange.getRequestHeaders().getFirst("Content-Length");

        if (longitudDeclarada != null) {
            long longitud;
            try {
                longitud = Long.parseLong(longitudDeclarada.trim());
            } catch (NumberFormatException error) {
                throw new IOException(
                        "Longitud de cuerpo de autenticación inválida", error);
            }

            if (longitud > MAX_CUERPO_AUTENTICACION_BYTES) {
                throw new CuerpoAutenticacionDemasiadoGrande();
            }
        }

        byte[] cuerpo = exchange.getRequestBody()
                .readNBytes(MAX_CUERPO_AUTENTICACION_BYTES + 1);

        if (cuerpo.length > MAX_CUERPO_AUTENTICACION_BYTES) {
            throw new CuerpoAutenticacionDemasiadoGrande();
        }

        return new String(cuerpo, StandardCharsets.UTF_8);
    }

    private void responderCuerpoAutenticacionDemasiadoGrande(
            HttpExchange exchange) throws IOException {
        enviarError(
                exchange, 413, "CUERPO_DEMASIADO_GRANDE",
                "El cuerpo de autenticación supera el tamaño máximo permitido.");
        exchange.close();
    }

    private void manejarValidarSesion(HttpExchange exchange)
            throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            enviarError(
                    exchange, 405, "METODO_NO_PERMITIDO",
                    "Use GET para validar la sesión.");
            return;
        }

        Optional<String> tokenOpt = extraerBearerToken(exchange);
        if (tokenOpt.isEmpty()) {
            enviarError(
                    exchange, 401, "TOKEN_AUSENTE",
                    "Debe proporcionar la cabecera Authorization: Bearer <token>.");
            return;
        }

        Optional<Sesion> sesionOpt =
                servicioAutenticacion.validarSesion(tokenOpt.get());

        if (sesionOpt.isEmpty()) {
            enviarError(
                    exchange, 401, "SESION_INVALIDA",
                    "La sesión no existe, ha expirado o fue cerrada.");
            return;
        }

        Map<String, Object> respuesta = mapearSesion(sesionOpt.get());
        respuesta.put("exito", true);
        respuesta.put("mensaje", "Sesión válida.");

        enviarJson(exchange, 200, respuesta);
    }

    private void manejarLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            enviarError(
                    exchange, 405, "METODO_NO_PERMITIDO",
                    "Use POST para cerrar sesión.");
            return;
        }

        Optional<String> tokenOpt = extraerBearerToken(exchange);
        if (tokenOpt.isEmpty()) {
            enviarError(
                    exchange, 401, "TOKEN_AUSENTE",
                    "Debe proporcionar la cabecera Authorization: Bearer <token>.");
            return;
        }

        boolean cerrada = servicioAutenticacion.cerrarSesion(tokenOpt.get());
        if (!cerrada) {
            enviarError(
                    exchange, 401, "SESION_INVALIDA",
                    "No se encontró una sesión activa para ese token.");
            return;
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("exito", true);
        respuesta.put("mensaje", "Sesión cerrada correctamente.");

        enviarJson(exchange, 200, respuesta);
    }

    private void responderAutenticacion(
            HttpExchange exchange,
            ResultadoAutenticacion resultado) throws IOException {
        if (resultado.isExito() && resultado.getSesion().isPresent()) {
            Map<String, Object> respuesta =
                    mapearSesion(resultado.getSesion().get());
            respuesta.put("exito", true);
            respuesta.put("mensaje", resultado.getMensaje());

            enviarJson(exchange, 200, respuesta);
            return;
        }

        String codigo = resultado.getCodigoError()
                .orElse("ERROR_AUTENTICACION");

        int status = switch (codigo) {
            case ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS -> 400;
            case ResultadoAutenticacion.ERROR_CUENTA_INACTIVA -> 403;
            case ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA -> 423;
            default -> 401;
        };

        enviarError(exchange, status, codigo, resultado.getMensaje());
    }

    private Map<String, Object> mapearSesion(Sesion sesion) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("token", sesion.getToken());
        mapa.put("usuarioId", sesion.getUsuarioId());
        mapa.put("username", sesion.getUsername());
        mapa.put("nombreCompleto", sesion.getNombreCompleto());
        mapa.put("rol", sesion.getRol().name());
        mapa.put("expiraEn", sesion.getExpiraEn().toString());
        mapa.put("origenAutenticacion", sesion.getOrigenAutenticacion());
        return mapa;
    }

    private Optional<String> extraerBearerToken(HttpExchange exchange) {
        String header =
                exchange.getRequestHeaders().getFirst("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }

        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    private void enviarError(
            HttpExchange exchange,
            int status,
            String codigoError,
            String mensaje) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("exito", false);
        cuerpo.put("codigoError", codigoError);
        cuerpo.put("mensaje", mensaje);

        enviarJson(exchange, status, cuerpo);
    }

    private void enviarJson(
            HttpExchange exchange,
            int status,
            Map<String, Object> cuerpo) throws IOException {
        byte[] bytes = JsonUtil.aJson(cuerpo)
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream salida = exchange.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static final class CuerpoAutenticacionDemasiadoGrande
            extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
