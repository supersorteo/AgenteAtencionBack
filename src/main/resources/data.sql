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
) ON CONFLICT DO NOTHING;

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
) ON CONFLICT DO NOTHING;

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

CONTACTO: Av. General Rivera 2500, Montevideo | Tel: 2708-3456 | Instagram: @elcortemvd
IMPORTANTE: Los horarios y disponibilidad reales los proveen las herramientas — no asumas días ni horas desde este texto.

HERRAMIENTAS DISPONIBLES:
- obtenerFechaActual: usá SIEMPRE que el cliente diga "mañana", "pasado", "el lunes" u otra referencia relativa. Resolvé la fecha real antes de llamar a las demás herramientas
- buscarServicios: usá cuando pregunten por servicios, precios o duración
- buscarBarberos: usá cuando pregunten qué barberos trabajan o quién está disponible
- consultarDisponibilidad(fecha, servicio, nombreBarbero): usá para ver turnos libres. nombreBarbero es opcional. Usá esta herramienta también cuando pregunten por los horarios del negocio
- reservarTurno(paciente, servicio, fecha, hora, telefono, nombreBarbero): los SEIS datos son obligatorios. Guarda el turno en la agenda del negocio y del barbero seleccionado

FLUJO DE RESERVA:
1. Reuní obligatoriamente nombre del cliente, teléfono, servicio, barbero, fecha y hora. Preguntá solo lo que falte y conservá los datos que el cliente ya dio; nunca los inventes
2. Consultá buscarServicios para explicar opciones, precios y duración y obtener el nombre exacto del servicio elegido. Si pide algo ambiguo como "un corte", ayudalo a elegir del catálogo
3. Consultá buscarBarberos y ofrecé los nombres y especialidades reales. Si dice que le da igual, proponé un barbero con disponibilidad y pedile que confirme esa elección; nunca asignes uno en silencio
4. Preguntá la fecha preferida. Llamá obtenerFechaActual para resolver fechas relativas y aclarar fechas ambiguas; usá la fecha local de Montevideo. No inventes fechas ni horarios
5. Llamá consultarDisponibilidad con el servicio y la fecha elegidos y el barbero si ya lo eligió. Mostrá algunas opciones reales con fecha, hora de inicio, hora de fin y barbero. Si no hay lugar, ofrecé consultar otra fecha o barbero, sin cambiarlos automáticamente
6. Pedí nombre y teléfono de contacto si faltan. El teléfono es obligatorio; no uses números ficticios. No se requiere cuenta ni iniciar sesión para reservar
7. Cuando tengas los seis datos, mostrá un resumen con nombre, teléfono, servicio, barbero, fecha y hora. Preguntá si confirma. Si cambia algún dato, actualizá el resumen y volvé a consultar disponibilidad cuando cambie servicio, barbero, fecha u hora
8. Solo después de la confirmación explícita del cliente llamá reservarTurno con los seis datos. No repitas la llamada si ya obtuviste una confirmación de esa reserva
9. Solo anunciá que quedó reservado si reservarTurno devuelve "Turno confirmado" con su número. Mostrá el número y los datos guardados. Si falla o el horario se ocupó, explicalo y pedí otra opción; jamás simules una reserva exitosa

INSTRUCCIONES:
- Respondé siempre en español, de forma amigable y con onda
- No aceptes fechas pasadas: si el cliente pide una fecha anterior a hoy, explicale que no es posible
- Si el cliente menciona un barbero específico, incluyelo en la consulta y reserva
- La disponibilidad real de las herramientas prevalece sobre cualquier horario que puedas asumir
- No consultes ni reveles reservas o datos personales de otros clientes. El cliente puede crear su turno sin autorización; la consulta de agendas es privada para el administrador y el barbero correspondiente
- No prometas enviar WhatsApp ni recordatorios automáticos: el teléfono se guarda como dato de contacto
- Para cancelaciones indicá que llamen al teléfono o escriban por Instagram
- Si no hay turnos disponibles, sugerí otra fecha',
    true
) ON CONFLICT DO NOTHING;

INSERT INTO negocio_config (
    tenant_id, nombre, tagline, hero_titulo1, hero_titulo2, hero_desc, footer_desc,
    direccion, telefono, email, whatsapp, instagram_handle,
    horario1, horario2, horario3,
    t1_nombre, t1_iniciales, t1_servicio, t1_texto,
    t2_nombre, t2_iniciales, t2_servicio, t2_texto,
    t3_nombre, t3_iniciales, t3_servicio, t3_texto
) VALUES (
    'barberia-demo',
    'El Corte',
    'Barbería Premium · Montevideo',
    'El arte del',
    'corte perfecto',
    'Reservá tu turno online en segundos. Sin llamadas, sin esperas. Tu barbero favorito, siempre disponible.',
    'Barbería moderna en el corazón de Montevideo. Más de 10 años dando el mejor corte de la ciudad.',
    'Av. General Rivera 2500, Mvd',
    '2708-3456',
    'contacto@elcortemvd.com',
    '59899000000',
    'elcortemvd',
    'Martes — Sábado: 9:00 — 20:00',
    'Domingo: 10:00 — 15:00',
    'Lunes: Cerrado',
    'Federico M.', 'FM', 'Corte + Barba',
    'El mejor barbero de Montevideo sin dudas. Ya llevo 2 años viniendo cada mes y el resultado siempre supera las expectativas. Lugar y atención impecables.',
    'Sebastián R.', 'SR', 'Perfilado de barba',
    'Ambiente premium, técnica profesional y atención de primer nivel. El único lugar donde confío mi barba. Imposible encontrar algo igual en la ciudad.',
    'Martín K.', 'MK', 'Corte clásico',
    'Reservé online en 2 minutos, puntualidad total y el corte exactamente como lo pedí. El sistema de turnos es brillante. 100% recomendado.'
) ON CONFLICT DO NOTHING;

INSERT INTO servicios (tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, categoria, imagen_url, imagen_url2, imagen_url3, activo)
SELECT * FROM (VALUES
    -- CORTE
    ('barberia-demo','Corte Clásico','Corte tradicional con máquina y tijera. Incluye lavado y secado.',400.0,30,'✂️','Corte',
     'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',
     'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',
     'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',true),
    ('barberia-demo','Fade / Degradado','Degradado moderno con máquina, desde piel o bajo. Diseño de línea incluido.',480.0,45,'✂️','Corte',
     'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',
     'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',
     'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',true),
    ('barberia-demo','Corte + Diseño','Corte personalizado con diseño geométrico o iniciales a cargo del maestro barbero.',550.0,50,'✂️','Corte',
     'https://images.unsplash.com/photo-1635273051937-a0ddef9573b6?w=800&q=80',
     'https://images.unsplash.com/photo-1605497788044-5a32c7078486?w=800&q=80',
     'https://images.unsplash.com/photo-1657105052497-f996284ffff8?w=800&q=80',true),
    -- BARBA
    ('barberia-demo','Arreglo de Barba','Perfilado y recorte de barba con tijera y navaja. Hidratación incluida.',300.0,20,'🪒','Barba',
     'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',
     'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',
     'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',true),
    ('barberia-demo','Afeitado Clásico','Afeitado completo con navaja, toalla caliente, espuma artesanal y aftershave.',380.0,30,'🪒','Barba',
     'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',
     'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',
     'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',true),
    ('barberia-demo','Diseño de Barba','Modelado y diseño de barba a medida según la morfología del rostro. Acabado premium.',450.0,35,'🪒','Barba',
     'https://images.unsplash.com/photo-1705976062088-5433328c2dcd?w=800&q=80',
     'https://images.unsplash.com/photo-1517832606299-7ae9b720a186?w=800&q=80',
     'https://images.unsplash.com/photo-1599011176306-4a96f1516d4d?w=800&q=80',true),
    -- COMBO
    ('barberia-demo','Combo Corte + Barba','El combo más popular. Corte a elección más arreglo de barba completo.',650.0,55,'💈','Combo',
     'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',
     'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',
     'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',true),
    ('barberia-demo','Combo Premium','Corte + arreglo de barba + lavado con champú profesional y secado con productos.',850.0,70,'💈','Combo',
     'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',
     'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',
     'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',true),
    ('barberia-demo','Combo VIP','Experiencia completa: corte + afeitado con navaja + tratamiento capilar + masaje de cuero cabelludo.',1200.0,90,'💈','Combo',
     'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=800&q=80',
     'https://images.unsplash.com/photo-1630827020718-3433092696e7?w=800&q=80',
     'https://images.unsplash.com/photo-1647140655214-e4a2d914971f?w=800&q=80',true),
    -- COLORACIÓN
    ('barberia-demo','Coloración Completa','Tintura completa de raíz a punta. Incluye consulta de tono, aplicación y sellado del color.',1400.0,90,'🎨','Coloración',
     'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',
     'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',
     'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',true),
    ('barberia-demo','Mechas y Reflejos','Técnica de luces parciales: balayage, babylights o mechas clásicas. Brillo y movimiento natural.',1800.0,120,'🎨','Coloración',
     'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',
     'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',
     'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',true),
    ('barberia-demo','Decoloración','Aclarado total con protección capilar intensiva. Ideal para cambios radicales de look.',2200.0,120,'🎨','Coloración',
     'https://images.unsplash.com/photo-1660144689256-c9a4a4ac116c?w=800&q=80',
     'https://images.unsplash.com/photo-1470259078422-826894b933aa?w=800&q=80',
     'https://images.unsplash.com/photo-1617391654484-2894196c2cc9?w=800&q=80',true)
) AS v(tenant_id, nombre, descripcion, precio, duracion_minutos, emoji, categoria, imagen_url, imagen_url2, imagen_url3, activo)
WHERE NOT EXISTS (SELECT 1 FROM servicios WHERE tenant_id = 'barberia-demo');


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
