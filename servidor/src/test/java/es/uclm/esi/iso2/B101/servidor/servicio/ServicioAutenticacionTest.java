package es.uclm.esi.iso2.B101.servidor.servicio;

import static org.junit.jupiter.api.Assertions.*;

import es.uclm.esi.iso2.B101.servidor.dominio.ResultadoAutenticacion;
import es.uclm.esi.iso2.B101.servidor.dominio.Rol;
import es.uclm.esi.iso2.B101.servidor.dominio.Sesion;
import es.uclm.esi.iso2.B101.servidor.integracion.ProveedorSSOSimulado;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioAuditoriaAccesoMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioSesionesMemoria;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioUsuariosMemoria;
import es.uclm.esi.iso2.B101.servidor.seguridad.GeneradorTokens;
import es.uclm.esi.iso2.B101.servidor.seguridad.GestorContrasenas;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicioAutenticacionTest {

    private RelojMutable reloj;
    private GestorContrasenas gestorContrasenas;
    private RepositorioUsuariosMemoria repoUsuarios;
    private RepositorioSesionesMemoria repoSesiones;
    private RepositorioAuditoriaAccesoMemoria repoAuditoria;
    private ProveedorSSOSimulado proveedorSSO;
    private ServicioAutenticacion servicio;

    @BeforeEach
    void setUp() {
        reloj = new RelojMutable(Instant.parse("2026-11-19T10:00:00Z"));
        gestorContrasenas = new GestorContrasenas();
        repoUsuarios = RepositorioUsuariosMemoria.conUsuariosPorDefecto(gestorContrasenas);
        repoSesiones = new RepositorioSesionesMemoria();
        repoAuditoria = new RepositorioAuditoriaAccesoMemoria();
        proveedorSSO = new ProveedorSSOSimulado();
        servicio = new ServicioAutenticacion(
                repoUsuarios,
                repoSesiones,
                repoAuditoria,
                gestorContrasenas,
                new GeneradorTokens(),
                proveedorSSO,
                reloj,
                3,
                Duration.ofMinutes(15),
                Duration.ofHours(2)
        );
    }

    @Test
    void iniciarSesionExitosoParaTodosLosActores() {
        assertLoginCorrecto("paciente1", "paciente123", Rol.PACIENTE);
        assertLoginCorrecto("medico1", "medico123", Rol.MEDICO);
        assertLoginCorrecto("admision1", "admision123", Rol.ADMISION);
        assertLoginCorrecto("admin1", "admin123", Rol.ADMINISTRADOR);
        assertEquals(4, repoAuditoria.obtenerTodos().size());
    }

    @Test
    void rechazaCredencialesVaciasONulas() {
        ResultadoAutenticacion r1 = servicio.iniciarSesion("", "paciente123");
        ResultadoAutenticacion r2 = servicio.iniciarSesion("paciente1", "   ");
        ResultadoAutenticacion r3 = servicio.iniciarSesion(null, null);

        assertFalse(r1.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS), r1.getCodigoError());
        assertFalse(r2.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS), r2.getCodigoError());
        assertFalse(r3.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CREDENCIALES_VACIAS), r3.getCodigoError());
    }

    @Test
    void rechazaUsuarioInexistenteOContrasenaIncorrecta() {
        ResultadoAutenticacion inexistente = servicio.iniciarSesion("desconocido", "clave123");
        ResultadoAutenticacion claveMal = servicio.iniciarSesion("medico1", "incorrecta");

        assertFalse(inexistente.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS), inexistente.getCodigoError());
        assertFalse(claveMal.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CREDENCIALES_INVALIDAS), claveMal.getCodigoError());
    }

    @Test
    void bloqueaCuentaTrasSuperarIntentosFallidosYDesbloqueaAlExpirar() {
        assertFalse(servicio.iniciarSesion("medico1", "mal1").isExito());
        assertFalse(servicio.iniciarSesion("medico1", "mal2").isExito());
        ResultadoAutenticacion tercerFallo = servicio.iniciarSesion("medico1", "mal3");

        assertFalse(tercerFallo.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA), tercerFallo.getCodigoError());

        // Incluso con la contraseña correcta sigue bloqueada durante el periodo de bloqueo
        ResultadoAutenticacion duranteBloqueo = servicio.iniciarSesion("medico1", "medico123");
        assertFalse(duranteBloqueo.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CUENTA_BLOQUEADA), duranteBloqueo.getCodigoError());

        // Avanzamos el reloj más allá de los 15 minutos de bloqueo
        reloj.avanzar(Duration.ofMinutes(16));
        ResultadoAutenticacion trasDesbloqueo = servicio.iniciarSesion("medico1", "medico123");
        assertTrue(trasDesbloqueo.isExito());
    }

    @Test
    void rechazaCuentaInactiva() {
        repoUsuarios.registrarUsuario("USR-099", "inactivo1", "Usuario Inactivo",
                "clave123", Rol.PACIENTE, false, gestorContrasenas);

        ResultadoAutenticacion resultado = servicio.iniciarSesion("inactivo1", "clave123");
        assertFalse(resultado.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_CUENTA_INACTIVA), resultado.getCodigoError());
    }

    @Test
    void iniciarSesionMedianteSSOCorporativo() {
        ResultadoAutenticacion ok = servicio.iniciarSesionSSO("sso-sescam-medico1");
        assertTrue(ok.isExito());
        Sesion sesion = ok.getSesion().orElseThrow();
        assertEquals("medico1", sesion.getUsername());
        assertEquals(Rol.MEDICO, sesion.getRol());
        assertEquals(Sesion.ORIGEN_SSO, sesion.getOrigenAutenticacion());

        ResultadoAutenticacion invalido = servicio.iniciarSesionSSO("token-falso");
        assertFalse(invalido.isExito());
        assertEquals(Optional.of(ResultadoAutenticacion.ERROR_SSO_INVALIDO), invalido.getCodigoError());
    }

    @Test
    void validaExpiracionYCierreDeSesion() {
        Sesion sesion = servicio.iniciarSesion("admision1", "admision123").getSesion().orElseThrow();
        assertTrue(servicio.validarSesion(sesion.getToken()).isPresent());

        assertTrue(servicio.cerrarSesion(sesion.getToken()));
        assertTrue(servicio.validarSesion(sesion.getToken()).isEmpty());

        // Comprobar expiración por tiempo
        Sesion sesion2 = servicio.iniciarSesion("admision1", "admision123").getSesion().orElseThrow();
        reloj.avanzar(Duration.ofHours(3));
        assertTrue(servicio.validarSesion(sesion2.getToken()).isEmpty());
    }

    private void assertLoginCorrecto(String username, String password, Rol rolEsperado) {
        ResultadoAutenticacion res = servicio.iniciarSesion(username, password);
        assertTrue(res.isExito());
        assertTrue(res.getSesion().isPresent());
        Sesion sesion = res.getSesion().get();
        assertEquals(username, sesion.getUsername());
        assertEquals(rolEsperado, sesion.getRol());
        assertEquals(Sesion.ORIGEN_LOCAL, sesion.getOrigenAutenticacion());
        assertNotNull(sesion.getToken());
        assertFalse(sesion.getToken().isBlank());
    }

    private static final class RelojMutable extends Clock {
        private Instant actual;

        RelojMutable(Instant inicial) {
            this.actual = inicial;
        }

        void avanzar(Duration duracion) {
            this.actual = this.actual.plus(duracion);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return actual;
        }
    }
}
