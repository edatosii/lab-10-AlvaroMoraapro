/*
 * Dibuja un árbol genealógico en un <canvas>. Esta página YA está hecha: no es necesario modificarla.
 *
 *   const lienzo = dibujarArbol(document.getElementById("canvas"), miembros, { alSeleccionar: m => ... });
 *   lienzo.seleccionar("1.2");
 *
 * "miembros" es la lista que genera el servidor (util/Json.java):
 *   [{ posicion: "1", nombre: "Ana", nacimiento: 2000, fallecimiento: null }, ...]
 *
 * SOLO se dibujan los miembros que existen: no hay cajas vacías para los que faltan. Los ascendientes
 * (Ahnentafel: 2, 3, 4...) se dibujan ARRIBA del sujeto y los descendientes (d'Aboville: 1.1, 1.2...) ABAJO.
 */
(function (global) {
    "use strict";

    // Dimensiones basadas en la escala de espaciado Apple (múltiplos de 4px y radio estricto de 8px)
    var ANCHO = 160, ALTO = 68, SEP_X = 24, SEP_Y = 56, MARGEN = 28, RADIO_CAJA = 8;

    // Tokens de color Apple aplicados al Canvas
    var TOKENS = {
        appleBlue:  "#0071e3",
        linkBlue:   "#0066cc",
        signalBlue: "#2997ff",
        carbon:     "#1d1d1f",
        frost:      "#f5f5f7",
        ice:        "#f4f8fb",
        graphite:   "#474747",
        ash:        "#707070",
        mist:       "#858585",
        hairline:   "#d2d2d7",
        pebble:     "#e2e2e5"
    };

    // Lavados de superficie sutiles (compatibles con .leyenda en estilos.css)
    var COLORES = {
        sujeto:       { fondo: "#fff8eb", borde: "#c8741a", pill: "#fff3d6", textoPill: "#9a550f" },
        ascendiente:  { fondo: "#f4f8fb", borde: "#2f6db5", pill: "#e3eefb", textoPill: "#1e4f8a" },
        descendiente: { fondo: "#f2fbf5", borde: "#2f8a4a", pill: "#e2f4e6", textoPill: "#1e6333" }
    };

    var FUENTE_BASE = "'SF Pro Text', 'Inter', system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";

    function tipo(posicion) {
        if (posicion === "1") return "sujeto";
        return posicion.indexOf(".") >= 0 ? "descendiente" : "ascendiente";
    }

    // Curva de desaceleración estilo Apple (easeOutQuart)
    function easeAppleOut(t) {
        return 1 - Math.pow(1 - t, 4);
    }

    // ---- 1. Construir los dos árboles a partir de las posiciones -------------------------------
    function construir(miembros) {
        var porPosicion = {};
        miembros.forEach(function (m) { porPosicion[m.posicion] = m; });

        function ascendiente(posicion) {
            var n = Number(posicion);
            var hijos = [];
            [String(2 * n), String(2 * n + 1)].forEach(function (p) {
                if (porPosicion[p]) hijos.push(ascendiente(p));
            });
            return { m: porPosicion[posicion], hijos: hijos };
        }

        function descendiente(posicion) {
            var hijos = miembros.filter(function (m) {
                return m.posicion.indexOf(posicion + ".") === 0 && m.posicion.indexOf(".", posicion.length + 1) < 0;
            }).sort(function (a, b) {
                return Number(a.posicion.split(".").pop()) - Number(b.posicion.split(".").pop());
            }).map(function (m) { return descendiente(m.posicion); });
            return { m: porPosicion[posicion], hijos: hijos };
        }

        var sujeto = porPosicion["1"];
        if (!sujeto) return null;
        var asc = ascendiente("1");
        var desc = descendiente("1");
        return { sujeto: sujeto, asc: asc, desc: desc };
    }

    // ---- 2. Calcular posiciones: cada persona queda centrada sobre sus hijos -------------------
    function medir(nodo) {
        var x = 0;
        nodo.hijos.forEach(function (h) {
            medir(h);
            h.desde = x;
            x += h.ancho + SEP_X;
        });
        if (nodo.hijos.length === 0) {
            nodo.ancho = ANCHO;
            nodo.cx = ANCHO / 2;
        } else {
            nodo.ancho = x - SEP_X;
            var primero = nodo.hijos[0], ultimo = nodo.hijos[nodo.hijos.length - 1];
            nodo.cx = ((primero.desde + primero.cx) + (ultimo.desde + ultimo.cx)) / 2;
        }
    }

    function profundidad(nodo) {
        var max = 0;
        nodo.hijos.forEach(function (h) { max = Math.max(max, 1 + profundidad(h)); });
        return max;
    }

    // Coloca cada nodo en (x, y) absolutas; "sentido" es -1 para ascendientes y +1 para descendientes.
    function colocar(nodo, desdeX, y, sentido, cajas, lineas, padre, nivel) {
        nodo.x = desdeX + nodo.cx;
        nodo.y = y;
        nodo.nivel = nivel || 0;
        cajas.push(nodo);
        if (padre) lineas.push({ de: padre, a: nodo, sentido: sentido, nivel: nodo.nivel });
        nodo.hijos.forEach(function (h) {
            colocar(h, desdeX + h.desde, y + sentido * (ALTO + SEP_Y), sentido, cajas, lineas, nodo, (nivel || 0) + 1);
        });
    }

    // ---- 3. Funciones de dibujo estilo Apple ---------------------------------------------------
    function recortar(ctx, texto, ancho) {
        if (ctx.measureText(texto).width <= ancho) return texto;
        while (texto.length > 1 && ctx.measureText(texto + "…").width > ancho) {
            texto = texto.slice(0, -1);
        }
        return texto + "…";
    }

    function anios(m) {
        return m.nacimiento + " – " + (m.fallecimiento == null ? "Presente" : m.fallecimiento);
    }

    function etiqueta(posicion) {
        if (posicion === "1") return "Sujeto · 1";
        return (posicion.indexOf(".") >= 0 ? "d'Aboville " : "Ahnentafel ") + posicion;
    }

    function rect(ctx, x, y, w, h, r) {
        var radio = Math.min(r, w / 2, h / 2);
        ctx.beginPath();
        ctx.moveTo(x + radio, y);
        ctx.arcTo(x + w, y, x + w, y + h, radio);
        ctx.arcTo(x + w, y + h, x, y + h, radio);
        ctx.arcTo(x, y + h, x, y, radio);
        ctx.arcTo(x, y, x + w, y, radio);
        ctx.closePath();
    }

    // Conector ortogonal con codos redondeados estilo Apple
    function dibujarConector(ctx, x1, y1, x2, y2, radioCodo) {
        var ym = (y1 + y2) / 2;
        var dx = x2 - x1;
        var dy = y2 - y1;
        var r = Math.min(radioCodo, Math.abs(dx) / 2, Math.abs(dy) / 4);

        ctx.beginPath();
        ctx.moveTo(x1, y1);
        if (Math.abs(dx) < 1) {
            ctx.lineTo(x2, y2);
        } else {
            var dirX = dx > 0 ? 1 : -1;
            var dirY = dy > 0 ? 1 : -1;
            ctx.lineTo(x1, ym - dirY * r);
            ctx.arcTo(x1, ym, x1 + dirX * r, ym, r);
            ctx.lineTo(x2 - dirX * r, ym);
            ctx.arcTo(x2, ym, x2, ym + dirY * r, r);
            ctx.lineTo(x2, y2);
        }
        ctx.stroke();
    }

    global.dibujarArbol = function (canvas, miembros, opciones) {
        opciones = opciones || {};
        var datos = construir(miembros);
        var ctx = canvas.getContext("2d");
        var cajas = [], lineas = [], seleccionada = null, hoverPosicion = null;

        if (!datos) {
            canvas.width = 340;
            canvas.height = 72;
            ctx.font = "400 14px " + FUENTE_BASE;
            ctx.fillStyle = TOKENS.ash;
            ctx.fillText("No hay miembros para dibujar.", 16, 40);
            return { seleccionar: function () {} };
        }

        medir(datos.asc);
        medir(datos.desc);
        var alturaAsc = profundidad(datos.asc);
        var alturaDesc = profundidad(datos.desc);
        var maxNivel = Math.max(alturaAsc, alturaDesc, 1);

        // El sujeto de los dos árboles es la misma persona: se alinean por su centro.
        var centro = Math.max(datos.asc.cx, datos.desc.cx);
        var ySujeto = MARGEN + alturaAsc * (ALTO + SEP_Y);

        colocar(datos.asc, MARGEN + centro - datos.asc.cx, ySujeto, -1, cajas, lineas, null, 0);

        // La raíz de los descendientes es el mismo sujeto: no se dibuja dos veces.
        var descendientes = [];
        colocar(datos.desc, MARGEN + centro - datos.desc.cx, ySujeto, 1, descendientes, lineas, null, 0);
        descendientes.slice(1).forEach(function (c) { cajas.push(c); });

        var ancho = Math.max(
            MARGEN + centro - datos.asc.cx + datos.asc.ancho,
            MARGEN + centro - datos.desc.cx + datos.desc.ancho
        ) + MARGEN;
        var alto = ySujeto + alturaDesc * (ALTO + SEP_Y) + ALTO + MARGEN;

        var escala = global.devicePixelRatio || 1;
        canvas.width = Math.ceil(ancho * escala);
        canvas.height = Math.ceil(alto * escala);
        canvas.style.width = ancho + "px";
        canvas.style.height = alto + "px";
        canvas.style.maxWidth = "none";

        var progresoEntrada = 1;
        var animFrameId = null;

        function pintar() {
            ctx.setTransform(escala, 0, 0, escala, 0, 0);
            ctx.clearRect(0, 0, ancho, alto);

            // 1. Dibujar conectores capilares
            lineas.forEach(function (l) {
                var retraso = (l.nivel - 1) * 0.12;
                var tLocal = Math.max(0, Math.min(1, (progresoEntrada - retraso) / Math.max(0.25, 1 - retraso)));
                var alpha = easeAppleOut(tLocal);
                if (alpha <= 0.01) return;

                var arriba = l.sentido === 1 ? l.de : l.a;
                var abajo = l.sentido === 1 ? l.a : l.de;
                var y1 = arriba.y + ALTO;
                var y2 = abajo.y;

                var esConexionActiva = seleccionada && (l.de.m.posicion === seleccionada || l.a.m.posicion === seleccionada);

                ctx.save();
                ctx.globalAlpha = alpha;
                ctx.lineWidth = esConexionActiva ? 1.75 : 1.25;
                ctx.strokeStyle = esConexionActiva ? TOKENS.signalBlue : TOKENS.hairline;
                dibujarConector(ctx, arriba.x, y1, abajo.x, y2, 8);
                ctx.restore();
            });

            // 2. Dibujar tarjetas de miembros
            cajas.forEach(function (c) {
                var retraso = c.nivel * 0.11;
                var tLocal = Math.max(0, Math.min(1, (progresoEntrada - retraso) / Math.max(0.3, 1 - retraso)));
                var curva = easeAppleOut(tLocal);
                if (curva <= 0.01) return;

                var col = COLORES[tipo(c.m.posicion)];
                var esSeleccionada = c.m.posicion === seleccionada;
                var esHover = c.m.posicion === hoverPosicion && !esSeleccionada;

                var offsetY = (1 - curva) * (c.y < ySujeto ? -10 : (c.y > ySujeto ? 10 : 6));
                var x = c.x - ANCHO / 2;
                var y = c.y + offsetY;

                ctx.save();
                ctx.globalAlpha = curva;

                // Superficie de la tarjeta (8px radius, sin sombras de elevación)
                rect(ctx, x, y, ANCHO, ALTO, RADIO_CAJA);
                if (esSeleccionada) {
                    // Single Blue Switch (#0071e3) para el estado seleccionado
                    ctx.fillStyle = TOKENS.appleBlue;
                } else if (esHover) {
                    ctx.fillStyle = "#ffffff";
                } else {
                    ctx.fillStyle = col.fondo;
                }
                ctx.fill();

                // Borde capilar de 1px (1.5px en hover/activo)
                ctx.lineWidth = (esSeleccionada || esHover) ? 1.5 : 1;
                ctx.strokeStyle = esSeleccionada ? TOKENS.appleBlue : (esHover ? TOKENS.signalBlue : col.borde);
                ctx.stroke();

                ctx.textAlign = "center";

                // Nombre de la persona (SF Pro Text Semibold 14px)
                ctx.font = "600 13.5px " + FUENTE_BASE;
                ctx.fillStyle = esSeleccionada ? TOKENS.ice : TOKENS.carbon;
                ctx.fillText(recortar(ctx, c.m.nombre, ANCHO - 18), c.x, y + 21);

                // Años de nacimiento y fallecimiento (SF Pro Text Regular 12px)
                ctx.font = "400 11.5px " + FUENTE_BASE;
                ctx.fillStyle = esSeleccionada ? "rgba(244, 248, 251, 0.88)" : TOKENS.graphite;
                ctx.fillText(anios(c.m), c.x, y + 38);

                // Etiqueta tipo Pill (980px radius) para la posición Ahnentafel / d'Aboville
                var textoEtiqueta = etiqueta(c.m.posicion);
                ctx.font = "600 10px " + FUENTE_BASE;
                var anchoPill = Math.min(ANCHO - 20, ctx.measureText(textoEtiqueta).width + 14);
                var altoPill = 16;
                var xPill = c.x - anchoPill / 2;
                var yPill = y + 45;

                rect(ctx, xPill, yPill, anchoPill, altoPill, 980);
                ctx.fillStyle = esSeleccionada ? "rgba(255, 255, 255, 0.2)" : col.pill;
                ctx.fill();

                ctx.fillStyle = esSeleccionada ? TOKENS.ice : col.textoPill;
                ctx.fillText(textoEtiqueta, c.x, yPill + 11.5);

                ctx.restore();
            });
        }

        // Animación coreografiada al construir el árbol
        function animarEntrada() {
            if ( global.matchMedia && global.matchMedia("(prefers-reduced-motion: reduce)").matches ) {
                progresoEntrada = 1;
                pintar();
                return;
            }
            var inicio = null;
            var duracion = Math.min(680, 360 + maxNivel * 75);
            progresoEntrada = 0;

            function paso(timestamp) {
                if (!inicio) inicio = timestamp;
                var transcurrido = timestamp - inicio;
                progresoEntrada = Math.min(1, transcurrido / duracion);
                pintar();
                if (progresoEntrada < 1) {
                    animFrameId = global.requestAnimationFrame(paso);
                }
            }
            if (animFrameId) global.cancelAnimationFrame(animFrameId);
            animFrameId = global.requestAnimationFrame(paso);
        }

        function buscarCajaEnCoordenadas(evento) {
            var r = canvas.getBoundingClientRect();
            var px = (evento.clientX - r.left) * (ancho / r.width);
            var py = (evento.clientY - r.top) * (alto / r.height);
            for (var i = 0; i < cajas.length; i++) {
                var c = cajas[i];
                if (px >= c.x - ANCHO / 2 && px <= c.x + ANCHO / 2 && py >= c.y && py <= c.y + ALTO) {
                    return c;
                }
            }
            return null;
        }

        function seleccionar(posicion) {
            var caja = cajas.filter(function (c) { return c.m.posicion === posicion; })[0];
            if (!caja) return;
            seleccionada = posicion;
            pintar();
            if (opciones.alSeleccionar) opciones.alSeleccionar(caja.m);
        }

        canvas.addEventListener("click", function (e) {
            var c = buscarCajaEnCoordenadas(e);
            if (c) {
                seleccionar(c.m.posicion);
            }
        });

        canvas.addEventListener("mousemove", function (e) {
            var c = buscarCajaEnCoordenadas(e);
            var nuevaHover = c ? c.m.posicion : null;
            canvas.style.cursor = c ? "pointer" : "default";
            if (nuevaHover !== hoverPosicion) {
                hoverPosicion = nuevaHover;
                pintar();
            }
        });

        canvas.addEventListener("mouseleave", function () {
            if (hoverPosicion !== null) {
                hoverPosicion = null;
                pintar();
            }
        });

        // Pintado inicial inmediato por compatibilidad síncrona + disparo de animación
        pintar();
        if (global.requestAnimationFrame) {
            animarEntrada();
        }

        return { seleccionar: seleccionar };
    };
})(window);