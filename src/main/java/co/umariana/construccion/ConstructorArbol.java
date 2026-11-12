package co.umariana.construccion;

import java.util.ArrayList;
import java.util.List;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 3)
 * ------------------------------------------
 * Reemplace el "throw new UnsupportedOperationException" por su implementación. Esta clase usa lo que ya
 * hizo en las etapas anteriores (Posicion, Persona y ArbolGenealogico). NO modifique las pruebas (src/test).
 *
 * REGLAS
 *   1. Si el texto es null o no tiene ninguna línea con contenido: ArbolInvalidoException (mensaje: "El texto está vacío...").
 *   2. Las líneas pueden terminar en \n o en \r\n. Las líneas en blanco se ignoran, pero los números de línea
 *      que se informan cuentan TODAS las líneas del texto (la primera es la línea 1).
 *   3. Cada línea tiene 4 campos separados por ';' (el cuarto, el fallecimiento, puede estar vacío). Se acepta
 *      también la línea con solo 3 campos y sin el último ';'. Los campos se recortan (trim).
 *      Si hay menos de 3 o más de 4 campos: error.
 *   4. El nacimiento y el fallecimiento deben ser números enteros (el fallecimiento vacío es "no tiene").
 *   5. Cada persona debe ser válida (Persona.validar) y no puede haber dos líneas con la misma posición.
 *   6. Debe existir la persona "1" (el sujeto), pero puede estar en cualquier línea.
 *   7. Las líneas pueden venir en CUALQUIER orden. El constructor debe agregarlas al árbol en un orden en el
 *      que cada persona ya tenga a la que necesita (por ejemplo, primero los ascendientes por número Ahnentafel
 *      ascendente y los descendientes por generación). Las reglas del árbol (ver ArbolGenealogico) las valida el árbol.
 *   8. Todos los errores asociados a una línea se informan con ArbolInvalidoException y un mensaje que EMPIEZA con
 *      "Línea N: " (N es el número de línea de la persona con el problema), por ejemplo:
 *          "Línea 3: la posición 2 está repetida."
 *      Los errores que no son de una línea (texto vacío, falta el sujeto) no llevan ese prefijo.
 *   9. Solo se muestran los miembros que están en el texto: no se crean personas vacías para los que faltan.
 */
public final class ConstructorArbol {

    private ConstructorArbol() {
    }

    // Clase auxiliar privada para recordar en qué línea de texto original venía cada persona
    private static class LineaParseada {
        int numeroLinea;
        Persona persona;

        LineaParseada(int numeroLinea, Persona persona) {
            this.numeroLinea = numeroLinea;
            this.persona = persona;
        }
    }
    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        // Regla 1: Texto nulo o vacío
        if (texto == null || texto.trim().isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío o es nulo.");
        }

        // Dividir por saltos de línea (el "-1" asegura contar líneas vacías al final)
        String[] lineas = texto.split("\r?\n", -1);
        List<LineaParseada> todasLasLineas = new ArrayList<>();
        LineaParseada sujetoLinea = null;

        // Regla 2 y 3: Parsear líneas
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            int numeroLineaReal = i + 1; // La primera línea es la 1
            
            if (linea.isEmpty()) {
                continue; // Ignorar líneas en blanco
            }

            String[] partes = linea.split(";", -1); 

            if (partes.length < 3 || partes.length > 4) {
                throw new ArbolInvalidoException("Línea " + numeroLineaReal + ": Formato incorrecto, debe tener 3 o 4 campos.");
            }

            String posicionStr = partes[0].trim();
            String nombre = partes[1].trim();
            String nacimientoStr = partes[2].trim();
            String fallecimientoStr = (partes.length == 4) ? partes[3].trim() : "";

            try {
                // Regla 4: Números enteros
                int nacimiento = Integer.parseInt(nacimientoStr);
                Integer fallecimiento = fallecimientoStr.isEmpty() ? null : Integer.parseInt(fallecimientoStr);
                
                // Regla 5: Persona válida
                Persona p = new Persona(posicionStr, nombre, nacimiento, fallecimiento);
                p.validar(); 
                
                // Validar que la posición no esté repetida en el texto
                for (LineaParseada lp : todasLasLineas) {
                    if (lp.persona.getPosicion().equals(posicionStr)) {
                        throw new ArbolInvalidoException("La posición " + posicionStr + " está repetida.");
                    }
                }

                LineaParseada lp = new LineaParseada(numeroLineaReal, p);
                todasLasLineas.add(lp);

                // Regla 6: Identificar al sujeto
                if (posicionStr.equals("1")) {
                    sujetoLinea = lp;
                }
            } catch (NumberFormatException e) {
                // Regla 8: Prefijo de línea
                throw new ArbolInvalidoException("Línea " + numeroLineaReal + ": El año no es un número válido.");
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException("Línea " + numeroLineaReal + ": " + e.getMessage());
            }
        }

        // Falla si no existe el sujeto (no lleva prefijo de línea)
        if (sujetoLinea == null) {
            throw new ArbolInvalidoException("Falta el sujeto (posición 1).");
        }

        // Iniciar el árbol
        ArbolGenealogico arbol = new ArbolGenealogico(sujetoLinea.persona);
        todasLasLineas.remove(sujetoLinea);

        // Regla 7: Ordenar las líneas para insertarlas por número de generación
        todasLasLineas.sort((l1, l2) -> {
            int gen1 = Posicion.generacion(l1.persona.getPosicion());
            int gen2 = Posicion.generacion(l2.persona.getPosicion());
            return Integer.compare(gen1, gen2);
        });

        // Agregar todas las personas al árbol ordenadamente
        for (LineaParseada lp : todasLasLineas) {
            try {
                arbol.agregar(lp.persona);
            } catch (IllegalArgumentException e) {
                throw new ArbolInvalidoException("Línea " + lp.numeroLinea + ": " + e.getMessage());
            }
        }

        // Regla 9: Retornar árbol exacto (sin huecos)
        return arbol;
    }
}