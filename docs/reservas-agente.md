# Reservas por el agente

El agente de `barberia-demo` consulta el catálogo y la agenda reales. Reúne nombre,
teléfono, servicio, barbero, fecha y hora; muestra el resumen y pide confirmación
antes de llamar a `reservarTurno`. El teléfono y el barbero son obligatorios.

## Validación y guardado

- `ReservaAgenteService` valida los seis campos, el teléfono (8–15 dígitos), la fecha
  y hora, el servicio activo y el barbero activo del negocio. Acepta diferencias de
  mayúsculas y tildes, pero no sustituye nombres inexistentes o ambiguos.
- Comprueba un slot real para el servicio y el barbero seleccionados, incluyendo
  duración, jornada, reservas y bloqueos. Usa `America/Montevideo` por defecto
  (`app.negocio.time-zone` permite cambiarla).
- Guarda en `turnos`, con `tenantId`, `barberoId`, `horaFin` y estado `RESERVADO`.
  Devuelve una confirmación con el ID solo después de completar la transacción.
- El agente y el POST del formulario bloquean la fila del barbero mientras
  comprueban y guardan, para evitar reservas simultáneas superpuestas.
- Un turno cancelado deja de ocupar su intervalo. Los horarios pasados no se ofrecen.

La validación completa del formulario queda pendiente. Este cambio no modifica su
interfaz ni convierte sus campos en obligatorios. El agente sí los exige al guardar.

## Acceso a las agendas

- `POST /api/v1/chat` y `POST /api/v1/turnos/{tenantId}` siguen siendo públicos.
- `GET /api/v1/turnos/{tenantId}` requiere administrador del mismo negocio.
- `GET /api/v1/turnos/{tenantId}/mis-turnos` usa el barbero y negocio del JWT.
- Los paneles existentes consultan estos mismos registros. Recargar la lista o
  la página para ver nuevas reservas; revisar también el filtro de fecha.

## Prueba de conversación después del despliegue

Enviar cada mensaje a `/api/v1/chat` con `tenantId: barberia-demo` y un mismo
`sessionId` nuevo durante toda la conversación:

1. «Quiero reservar un turno. ¿Qué servicios ofrecen?»
2. Elegir un servicio del catálogo y preguntar qué barberos hay.
3. Elegir un barbero y pedir disponibilidad para una fecha futura.
4. Elegir un horario ofrecido y dar nombre y teléfono.
5. Comprobar que el agente muestra los seis datos y solicita confirmación.
6. Confirmar; debe responder con el número de reserva y los datos guardados.
7. Iniciar sesión en el panel de administrador y en la agenda del barbero elegido,
   filtrar por esa fecha y comprobar el mismo turno.

Casos negativos: omitir teléfono, pedir un barbero inexistente, una fecha pasada,
un horario bloqueado o intentar repetir el mismo turno. No deben crear una reserva.
Si el cliente cambia de servicio, barbero o fecha, el agente debe consultar de nuevo
los horarios y pedir confirmación del resumen actualizado.

## Pruebas automáticas

`mvn test` ejecuta las pruebas unitarias y una integración con H2 temporal.
La integración usa herramientas, repositorios y transacciones reales para probar
persistencia, permisos de lectura y concurrencia entre agente y formulario.
No contacta a Groq ni escribe en PostgreSQL o Railway. La conversación con el modelo
real debe verificarse después del despliegue con una sesión nueva; su memoria es
temporal y se pierde al reiniciar el backend.

El contexto de la barbería se actualiza mediante el `ON CONFLICT` existente de
`data.sql` al arrancar, porque el perfil de producción tiene SQL init habilitado.
