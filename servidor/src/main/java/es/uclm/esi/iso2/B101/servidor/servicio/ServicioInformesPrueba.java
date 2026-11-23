package es.uclm.esi.iso2.B101.servidor.servicio;

import es.uclm.esi.iso2.B101.servidor.dominio.InformePrueba;
import es.uclm.esi.iso2.B101.servidor.dominio.PruebaRealizada;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioHistorialPruebas;
import es.uclm.esi.iso2.B101.servidor.persistencia.RepositorioInformesPrueba;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

public final class ServicioInformesPrueba {
    public static final int TAMANO_MAXIMO_IMAGEN_BYTES = 5 * 1024 * 1024;
    private final RepositorioHistorialPruebas repositorioPruebas;
    private final RepositorioInformesPrueba repositorioInformes;
    private final Clock reloj;

    public ServicioInformesPrueba(
            RepositorioHistorialPruebas repositorioPruebas,
            RepositorioInformesPrueba repositorioInformes) {
        this(repositorioPruebas, repositorioInformes, Clock.systemDefaultZone());
    }

    public ServicioInformesPrueba(
            RepositorioHistorialPruebas repositorioPruebas,
            RepositorioInformesPrueba repositorioInformes,
            Clock reloj) {
        this.repositorioPruebas = Objects.requireNonNull(repositorioPruebas, "repositorioPruebas no puede ser nulo");
        this.repositorioInformes = Objects.requireNonNull(repositorioInformes, "repositorioInformes no puede ser nulo");
        this.reloj = Objects.requireNonNull(reloj, "reloj no puede ser nulo");
    }

    public InformePrueba crear(String medicoId, String pruebaId, String hallazgos, String conclusion) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(pruebaId, "pruebaId");
        validarObligatorio(hallazgos, "hallazgos");
        validarObligatorio(conclusion, "conclusion");
        PruebaRealizada prueba = repositorioPruebas.buscarPorId(pruebaId)
                .orElseThrow(() -> new NoSuchElementException("Prueba realizada no encontrada"));
        InformePrueba informe = new InformePrueba(
                UUID.randomUUID().toString(),
                prueba.getId(),
                prueba.getPacienteId(),
                medicoId,
                hallazgos,
                conclusion,
                LocalDateTime.now(reloj),
                null,
                null);
        repositorioInformes.guardar(informe);
        return informe;
    }

    public List<InformePrueba> consultarPorPaciente(String pacienteId) {
        validarObligatorio(pacienteId, "pacienteId");
        return repositorioInformes.buscarPorPacienteId(pacienteId).stream()
                .sorted(java.util.Comparator.comparing(InformePrueba::getFechaCreacion).reversed())
                .toList();
    }

    public List<InformePrueba> consultarPorMedico(String medicoId) {
        validarObligatorio(medicoId, "medicoId");
        return repositorioInformes.buscarPorMedicoId(medicoId).stream()
                .sorted(java.util.Comparator.comparing(InformePrueba::getFechaCreacion).reversed())
                .toList();
    }

    public InformePrueba consultar(String informeId, String pacienteId, String medicoId) {
        validarObligatorio(informeId, "informeId");
        InformePrueba informe = repositorioInformes.buscarPorId(informeId)
                .orElseThrow(() -> new NoSuchElementException("Informe no encontrado"));
        boolean esPacientePropietario = pacienteId != null && pacienteId.equals(informe.getPacienteId());
        boolean esMedicoAutor = medicoId != null && medicoId.equals(informe.getMedicoId());
        if (!esPacientePropietario && !esMedicoAutor) {
            throw new NoSuchElementException("Informe no encontrado");
        }
        return informe;
    }

    public void adjuntarImagen(String medicoId, String informeId, String tipoImagen, String imagenBase64) {
        validarObligatorio(medicoId, "medicoId");
        validarObligatorio(informeId, "informeId");
        validarObligatorio(tipoImagen, "tipoImagen");
        validarObligatorio(imagenBase64, "imagenBase64");
        byte[] imagen;
        try {
            imagen = Base64.getDecoder().decode(imagenBase64);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("imagenBase64 no es válida", error);
        }
        if (imagen.length == 0 || imagen.length > TAMANO_MAXIMO_IMAGEN_BYTES) {
            throw new IllegalArgumentException("La imagen debe ocupar entre 1 byte y 5 MiB");
        }
        validarTipoYContenido(tipoImagen, imagen);

        InformePrueba informe = repositorioInformes.buscarPorId(informeId)
                .orElseThrow(() -> new NoSuchElementException("Informe no encontrado"));
        if (!medicoId.equals(informe.getMedicoId())) {
            throw new NoSuchElementException("Informe no encontrado");
        }
        repositorioInformes.adjuntarImagen(informeId, tipoImagen, imagen);
    }

    private void validarTipoYContenido(String tipoImagen, byte[] imagen) {
        boolean png = "image/png".equals(tipoImagen)
                && imagen.length >= 8
                && (imagen[0] & 0xff) == 0x89
                && imagen[1] == 'P' && imagen[2] == 'N' && imagen[3] == 'G'
                && imagen[4] == 0x0d && imagen[5] == 0x0a && imagen[6] == 0x1a && imagen[7] == 0x0a;
        boolean jpeg = "image/jpeg".equals(tipoImagen)
                && imagen.length >= 3
                && (imagen[0] & 0xff) == 0xff && (imagen[1] & 0xff) == 0xd8 && (imagen[2] & 0xff) == 0xff;
        if (!png && !jpeg) {
            throw new IllegalArgumentException("Solo se admiten imágenes PNG o JPEG válidas");
        }
    }

    private static void validarObligatorio(String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(nombre + " no puede ser nulo ni estar vacío");
        }
    }
}
