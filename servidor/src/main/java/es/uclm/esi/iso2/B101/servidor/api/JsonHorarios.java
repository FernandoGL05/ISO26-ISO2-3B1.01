package es.uclm.esi.iso2.B101.servidor.api;

import es.uclm.esi.iso2.B101.servidor.dominio.HorarioOperativo;
import es.uclm.esi.iso2.B101.servidor.dominio.TipoRecursoHorario;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serialización y deserialización JSON para horarios operativos (CU22).
 */
final class JsonHorarios {

    private JsonHorarios() {
    }

    static DatosHorario parsear(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        TipoRecursoHorario tipoRecurso = TipoRecursoHorario.valueOf(
                HttpAdminSupport.requerirCadena(campos, "tipoRecurso").trim().toUpperCase(Locale.ROOT));
        DayOfWeek diaSemana = DayOfWeek.valueOf(
                HttpAdminSupport.requerirCadena(campos, "diaSemana").trim().toUpperCase(Locale.ROOT));
        LocalTime horaInicio = LocalTime.parse(HttpAdminSupport.requerirCadena(campos, "horaInicio").trim());
        LocalTime horaFin = LocalTime.parse(HttpAdminSupport.requerirCadena(campos, "horaFin").trim());
        return new DatosHorario(
                HttpAdminSupport.requerirCadena(campos, "recursoId"),
                tipoRecurso,
                diaSemana,
                horaInicio,
                horaFin,
                HttpAdminSupport.requerirEntero(campos, "duracionHuecoMinutos"));
    }

    static boolean parsearEstado(String cuerpo) {
        Map<String, Object> campos = HttpAdminSupport.parsearObjetoJson(cuerpo);
        return HttpAdminSupport.requerirBooleano(campos, "activo");
    }

    static String serializar(HorarioOperativo horario) {
        return "{\"id\":" + HttpAdminSupport.cadenaJson(horario.getId())
                + ",\"recursoId\":" + HttpAdminSupport.cadenaJson(horario.getRecursoId())
                + ",\"tipoRecurso\":" + HttpAdminSupport.cadenaJson(horario.getTipoRecurso().name())
                + ",\"diaSemana\":" + HttpAdminSupport.cadenaJson(horario.getDiaSemana().name())
                + ",\"horaInicio\":" + HttpAdminSupport.cadenaJson(horario.getHoraInicio().toString())
                + ",\"horaFin\":" + HttpAdminSupport.cadenaJson(horario.getHoraFin().toString())
                + ",\"duracionHuecoMinutos\":" + horario.getDuracionHuecoMinutos()
                + ",\"activo\":" + horario.isActivo()
                + '}';
    }

    static String serializarLista(List<HorarioOperativo> horarios) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < horarios.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(serializar(horarios.get(i)));
        }
        return sb.append(']').toString();
    }

    record DatosHorario(
            String recursoId,
            TipoRecursoHorario tipoRecurso,
            DayOfWeek diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            int duracionHuecoMinutos) {
    }
}
