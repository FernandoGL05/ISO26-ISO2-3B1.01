package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import es.uclm.esi.iso2.B101.servidor.servicio.ServicioAutenticacion;
import java.io.IOException;

/**
 * Punto de entrada del servidor de Radiodiagnóstico del SESCAM.
 */
public class AplicacionServidor {

    public static final int PUERTO_POR_DEFECTO = 8080;

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : PUERTO_POR_DEFECTO;

        GestorContrasenas gestorContrasenas = new GestorContrasenas();
        ServicioAutenticacion servicioAutenticacion = new ServicioAutenticacion(
                RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas),
                new RepositorioSesionesMemoria(),
                new RepositorioAuditoriaAccesoMemoria(),
                gestorContrasenas,
                new GeneradorTokens(),
                new ProveedorSSOSimulado()
        );

        ServidorHTTP servidor = new ServidorHTTP(puerto, servicioAutenticacion);
        servidor.iniciar();
        System.out.println("Servidor de Radiodiagnóstico SESCAM escuchando en http://localhost:" + servidor.getPuerto());
    }
}
