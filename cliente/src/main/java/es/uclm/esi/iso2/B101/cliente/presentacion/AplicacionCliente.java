package es.uclm.esi.iso2.B101.cliente.presentacion;

import es.uclm.esi.iso2.B101.cliente.comunicacion.ClienteAutenticacion;
import es.uclm.esi.iso2.B101.cliente.modelo.RespuestaLogin;
import es.uclm.esi.iso2.B101.cliente.modelo.SesionCliente;
import java.util.Scanner;

/**
 * Aplicación cliente de consola para probar el caso de uso CU01 — Iniciar sesión.
 */
public class AplicacionCliente {

    public static final String URL_SERVIDOR_POR_DEFECTO = "http://localhost:8080";

    public static void main(String[] args) {
        String urlServidor = args.length > 0 ? args[0] : URL_SERVIDOR_POR_DEFECTO;
        ControladorInicioSesion controlador = new ControladorInicioSesion(new ClienteAutenticacion(urlServidor));

        System.out.println("=== Sistema de Radiodiagnóstico SESCAM — Cliente (CU01) ===");
        System.out.println("Servidor configurado: " + urlServidor);

        try (Scanner scanner = new Scanner(System.in)) {
            boolean ejecutando = true;
            while (ejecutando) {
                System.out.println("\n1. Iniciar sesión con usuario y contraseña");
                System.out.println("2. Iniciar sesión con SSO corporativo");
                System.out.println("3. Consultar sesión actual");
                System.out.println("4. Cerrar sesión");
                System.out.println("0. Salir");
                System.out.print("Seleccione una opción: ");

                String opcion = scanner.nextLine().trim();
                switch (opcion) {
                    case "1" -> {
                        System.out.print("Usuario: ");
                        String usuario = scanner.nextLine();
                        System.out.print("Contraseña: ");
                        String contrasena = scanner.nextLine();
                        mostrarResultadoLogin(controlador.iniciarSesion(usuario, contrasena));
                    }
                    case "2" -> {
                        System.out.print("Token SSO corporativo: ");
                        String tokenSso = scanner.nextLine();
                        mostrarResultadoLogin(controlador.iniciarSesionSSO(tokenSso));
                    }
                    case "3" -> {
                        if (controlador.verificarSesionActivaEnServidor()) {
                            SesionCliente sesion = controlador.obtenerSesionActual().orElseThrow();
                            System.out.println("Sesión activa -> " + sesion.nombreCompleto()
                                    + " (" + sesion.username() + ") | Rol: " + sesion.rol()
                                    + " | Origen: " + sesion.origenAutenticacion());
                        } else {
                            System.out.println("No hay ninguna sesión activa.");
                        }
                    }
                    case "4" -> {
                        boolean cerrada = controlador.cerrarSesion();
                        System.out.println(cerrada ? "Sesión cerrada correctamente." : "No había ninguna sesión activa.");
                    }
                    case "0" -> ejecutando = false;
                    default -> System.out.println("Opción no válida.");
                }
            }
        }
    }

    private static void mostrarResultadoLogin(RespuestaLogin respuesta) {
        if (respuesta.exito() && respuesta.sesion() != null) {
            SesionCliente sesion = respuesta.sesion();
            System.out.println("Acceso concedido. Bienvenido/a, " + sesion.nombreCompleto()
                    + " [" + sesion.rol() + "].");
        } else {
            System.out.println("Error de autenticación [" + respuesta.codigoError() + "]: " + respuesta.mensaje());
        }
    }
}
