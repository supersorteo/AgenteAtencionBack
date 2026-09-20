INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'inmobiliaria-demo',
    'Inmobiliaria Premium',
    'Eres el asistente virtual de Inmobiliaria Premium, una inmobiliaria en Montevideo, Uruguay.

Ayudás a los clientes a encontrar propiedades en venta o alquiler y a agendar visitas.

INSTRUCCIONES:
- Respondé siempre en español, de forma amable y profesional
- Cuando el cliente mencione que busca una propiedad, usá buscarPropiedades con los criterios que mencione. Si no especifica algún criterio pasalo como null o 0
- Cuando el cliente quiera visitar una propiedad, pedí nombre completo, teléfono, fecha y hora preferida, luego usá agendarVisita
- Siempre mostrá el ID de cada propiedad para que el cliente pueda referenciarla
- Para consultas sobre precios, ubicaciones o financiación indicá que un asesor los contactará
- Horario de atención: Lunes a Viernes 9:00-18:00 | Sábados 10:00-14:00
- Teléfono: 2900-5678 | Dirección: Av. Brasil 2345, Montevideo',
    true
) ON CONFLICT (id) DO UPDATE SET contexto = EXCLUDED.contexto;

INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'clinica-demo',
    'Clínica Bienestar',
    'Eres el asistente virtual de Clínica Bienestar, una clínica médica en Montevideo, Uruguay.

HORARIOS: Lunes a Viernes 8:00-20:00 | Sábados 9:00-14:00 | Domingos cerrado
CONTACTO: Av. 18 de Julio 1234, Montevideo | Tel: 2901-1234

INSTRUCCIONES:
- Respondé siempre en español, de forma amable y profesional
- Cuando el usuario pregunte por servicios o precios, llamá la herramienta buscarServicios para obtener la información actualizada
- Podés consultar disponibilidad y reservar turnos usando tus herramientas
- Pedí nombre completo y servicio deseado antes de reservar un turno
- Para cancelaciones indicá que llamen al teléfono',
    true
) ON CONFLICT (id) DO UPDATE SET contexto = EXCLUDED.contexto;

INSERT INTO propiedades (tenant_id, tipo, operacion, zona, precio, moneda, habitaciones, banos, metros_cuadrados, descripcion, disponible)
SELECT * FROM (VALUES
    ('inmobiliaria-demo', 'apartamento', 'alquiler', 'Pocitos', 1200.0, 'USD', 2, 1, 65.0, 'Luminoso apto con vista al mar, cocina equipada, edificio con portero 24h', true),
    ('inmobiliaria-demo', 'apartamento', 'venta',    'Pocitos', 185000.0, 'USD', 3, 2, 110.0, 'A estrenar, terminaciones de lujo, garaje incluido, a 2 cuadras de la rambla', true),
    ('inmobiliaria-demo', 'casa',        'venta',    'Carrasco', 450000.0, 'USD', 4, 3, 280.0, 'Casa con jardín y piscina, zona residencial tranquila, doble garaje', true),
    ('inmobiliaria-demo', 'apartamento', 'alquiler', 'Cordón', 750.0, 'USD', 1, 1, 45.0, 'Monoambiente moderno, totalmente amoblado, ideal para profesional', true),
    ('inmobiliaria-demo', 'local',       'alquiler', 'Centro', 2500.0, 'USD', null, null, 180.0, 'Local comercial en planta baja sobre avenida de alto tránsito', true),
    ('inmobiliaria-demo', 'casa',        'alquiler', 'Punta Carretas', 2800.0, 'USD', 3, 2, 200.0, 'Casa con jardín, garaje para 2 autos, muy tranquila', true)
) AS v(tenant_id, tipo, operacion, zona, precio, moneda, habitaciones, banos, metros_cuadrados, descripcion, disponible)
WHERE NOT EXISTS (SELECT 1 FROM propiedades WHERE tenant_id = 'inmobiliaria-demo');

INSERT INTO tenants (id, nombre, contexto, activo) VALUES (
    'barberia-demo',
    'Barbería El Corte',
    'Eres el asistente virtual de Barbería El Corte, una barbería moderna en Montevideo, Uruguay.

HORARIOS: Martes a Sábado 9:00-20:00 | Domingos 10:00-15:00 | Lunes cerrado
CONTACTO: Av. General Rivera 2500, Montevideo | Tel: 2708-3456 | Instagram: @elcortemvd

HERRAMIENTAS DISPONIBLES:
- obtenerFechaActual: usá SIEMPRE que el cliente diga "mañana", "pasado", "el lunes" u otra referencia relativa. Resolvé la fecha real antes de llamar a las demás herramientas
- buscarServicios: usá cuando pregunten por servicios, precios o duración
- buscarBarberos: usá cuando pregunten qué barberos trabajan o quién está disponible
- consultarDisponibilidad(fecha, servicio, nombreBarbero): usá para ver turnos libres. nombreBarbero es opcional
- reservarTurno(paciente, servicio, fecha, hora, telefono, nombreBarbero): telefono y nombreBarbero son opcionales

FLUJO DE RESERVA:
1. Preguntá qué servicio desea
2. Preguntá la fecha preferida (si dice "mañana" u otra relativa, llamá obtenerFechaActual primero)
3. Llamá consultarDisponibilidad con la fecha real en formato YYYY-MM-DD y el servicio
4. Mostrá los horarios disponibles con el barbero asignado
5. Pedí nombre completo. Pedí también el teléfono para contacto por WhatsApp (no es obligatorio, aclaralo)
6. Confirmá con reservarTurno

INSTRUCCIONES:
- Respondé siempre en español, de forma amigable y con onda
- No aceptes fechas pasadas: si el cliente pide una fecha anterior a hoy, explicale que no es posible
- Si el cliente menciona un barbero específico, incluyelo en la consulta y reserva
- Para cancelaciones indicá que llamen al teléfono o escriban por Instagram
- Si no hay turnos disponibles, sugerí otra fecha',
    true
) ON CONFLICT (id) DO UPDATE SET contexto = EXCLUDED.contexto;

INSERT INTO servicios (tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
SELECT * FROM (VALUES
    ('barberia-demo', 'Corte de cabello', 'Corte personalizado con tijera o máquina, lavado incluido', 450.0, 30, '✂️', true),
    ('barberia-demo', 'Corte + barba',    'Corte de cabello más perfilado y arreglo de barba', 650.0, 45, '🪒', true),
    ('barberia-demo', 'Afeitado clásico', 'Afeitado tradicional con navaja, toalla caliente y aftershave', 350.0, 30, '🔥', true),
    ('barberia-demo', 'Corte niños',      'Corte para menores de 12 años, ambiente amigable', 350.0, 25, '👦', true),
    ('barberia-demo', 'Tratamiento capilar', 'Hidratación profunda, masaje capilar y nutrición del cabello', 800.0, 60, '💆', true)
) AS v(tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
WHERE NOT EXISTS (SELECT 1 FROM servicios WHERE tenant_id = 'barberia-demo');

-- Barbero demo para barberia-demo
INSERT INTO barberos (tenant_id, nombre, especialidad, foto, activo)
SELECT 'barberia-demo', 'Juan García', 'Cortes modernos y arreglo de barba', null, true
WHERE NOT EXISTS (SELECT 1 FROM barberos WHERE tenant_id = 'barberia-demo' AND nombre = 'Juan García');

INSERT INTO horarios_barbero (barbero_id, dia_semana, hora_inicio, hora_fin)
SELECT b.id, v.dia, v.inicio, v.fin
FROM barberos b
CROSS JOIN (VALUES (2,'09:00','20:00'),(3,'09:00','20:00'),(4,'09:00','20:00'),(5,'09:00','20:00'),(6,'09:00','20:00'),(7,'10:00','15:00')) AS v(dia, inicio, fin)
WHERE b.tenant_id = 'barberia-demo' AND b.nombre = 'Juan García'
AND NOT EXISTS (SELECT 1 FROM horarios_barbero WHERE barbero_id = b.id);

-- Galería demo para barberia-demo
INSERT INTO galeria (tenant_id, titulo, descripcion, imagen_url, categoria, activo)
SELECT * FROM (VALUES
    ('barberia-demo', 'Corte fade degradado', 'Degradado prolijo con máquina y tijera', 'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=600', 'cortes', true),
    ('barberia-demo', 'Barba perfilada', 'Arreglo y perfilado de barba con navaja', 'https://images.unsplash.com/photo-1621605815971-fbc98d665033?w=600', 'barba', true),
    ('barberia-demo', 'Afeitado clásico', 'Afeitado tradicional con toalla caliente', 'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=600', 'afeitado', true),
    ('barberia-demo', 'Corte texturizado', 'Corte moderno con textura y volumen', 'https://images.unsplash.com/photo-1599351431202-1e0f0137899a?w=600', 'cortes', true)
) AS v(tenant_id, titulo, descripcion, imagen_url, categoria, activo)
WHERE NOT EXISTS (SELECT 1 FROM galeria WHERE tenant_id = 'barberia-demo');

INSERT INTO servicios (tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
SELECT * FROM (VALUES
    ('clinica-demo', 'Consulta médica general', 'Atención por médico general, diagnóstico y derivaciones', 800.0, 30, '🩺', true),
    ('clinica-demo', 'Consulta pediatría',      'Atención pediátrica para niños de 0 a 14 años', 900.0, 30, '👶', true),
    ('clinica-demo', 'Consulta ginecología',    'Consulta ginecológica y controles preventivos', 1000.0, 30, '🌸', true),
    ('clinica-demo', 'Análisis de sangre',      'Extracción y análisis completo de laboratorio', 600.0, 20, '🔬', true),
    ('clinica-demo', 'Electrocardiograma',      'ECG de reposo con interpretación médica', 1200.0, 20, '❤️', true)
) AS v(tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, activo)
WHERE NOT EXISTS (SELECT 1 FROM servicios WHERE tenant_id = 'clinica-demo');
