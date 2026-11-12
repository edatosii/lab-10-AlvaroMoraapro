package co.umariana.modelo;

import java.util.ArrayList;
import java.util.List;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías. Puede faltar el padre
 * de alguien, o el hijo 1.1 aunque exista el 1.2.
 *
 * INSTRUCCIONES PARA EL ESTUDIANTE (Etapa 2)
 * ------------------------------------------
 * 1. Implemente el árbol con sus PROPIOS NODOS: una clase interna "Nodo" con la Persona, el nodo del
 *    padre, el nodo de la madre y la lista de hijos. NO se permite usar Map ni Set (las pruebas lo
 *    revisan): para buscar una posición recorra el árbol siguiendo su camino
 *      - Ahnentafel: los bits de n después del primer 1 dicen el camino (0 = padre, 1 = madre).
 *        Ej.: 5 = 101 -> desde el sujeto: padre (0), luego madre (1).
 *      - D'Aboville: cada número de la posición dice a qué hijo bajar. Ej.: 1.2.3 -> hijo 2, luego su hijo 3.
 *    Se permite usar List/ArrayList para la lista de hijos.
 * 2. Todos los errores de las reglas se informan con {@link ArbolInvalidoException} (un mensaje claro).
 *    Los datos inválidos de una Persona los detecta Persona.validar() (IllegalArgumentException).
 * 3. NO modifique las pruebas (src/test).
 *
 * REGLAS DEL ÁRBOL
 *   - La posición "1" (el sujeto) existe siempre y no se puede eliminar.
 *   - Una posición no puede repetirse.
 *   - Un ascendiente n solo se puede agregar si ya existe su hijo n / 2, y debe haber NACIDO ANTES
 *     (año de nacimiento menor) que ese hijo.
 *   - Un descendiente solo se puede agregar si ya existe su progenitor, y debe haber NACIDO DESPUÉS
 *     (año de nacimiento mayor) que su progenitor.
 */
public class ArbolGenealogico {

   // 1. Clase interna Nodo (sin Map ni Set)
    private class Nodo {
        Persona persona;
        Nodo padre;
        Nodo madre;
        List<Nodo> hijos;

        public Nodo(Persona persona) {
            this.persona = persona;
            this.hijos = new ArrayList<>();
        }
    }

    private Nodo raiz;
    private int tamano;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new IllegalArgumentException("El sujeto no puede ser null");
        }
        sujeto.validar();

        if (!"1".equals(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("El sujeto debe estar en la posición 1");
        }
        this.raiz = new Nodo(sujeto);
        this.tamano = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz.persona;
    }

    /** @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo tiene 1) */
    public int tamano() {
        return tamano;
    }

    // --- MÉTODOS PRIVADOS AUXILIARES PARA NAVEGAR EL ÁRBOL ---

    private Nodo buscarNodo(String pos) {
        if (pos == null || pos.isEmpty()) return null;
        if (pos.equals("1")) return raiz;

        if (!pos.contains(".")) {
            // Ahnentafel (Ascendientes) -> navegación binaria por bits
            try {
                long n = Long.parseLong(pos);
                if (n < 1) return null;
                String bin = Long.toBinaryString(n);
                Nodo actual = raiz;
                // Ignoramos el primer '1' de la izquierda. 0 = padre, 1 = madre
                for (int i = 1; i < bin.length(); i++) {
                    if (actual == null) return null;
                    if (bin.charAt(i) == '0') {
                        actual = actual.padre;
                    } else {
                        actual = actual.madre;
                    }
                }
                return actual;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            // d'Aboville (Descendientes) -> navegación general
            if (!pos.startsWith("1.")) return null;
            String[] partes = pos.split("\\.");
            Nodo actual = raiz;
            for (int i = 1; i < partes.length; i++) {
                if (actual == null) return null;
                try {
                    int numHijo = Integer.parseInt(partes[i]);
                    Nodo siguiente = null;
                    for (Nodo hijo : actual.hijos) {
                        String[] hPartes = hijo.persona.getPosicion().split("\\.");
                        if (Integer.parseInt(hPartes[hPartes.length - 1]) == numHijo) {
                            siguiente = hijo;
                            break;
                        }
                    }
                    actual = siguiente;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return actual;
        }
    }

    private int contarNodos(Nodo nodo) {
        if (nodo == null) return 0;
        int count = 1;
        count += contarNodos(nodo.padre);
        count += contarNodos(nodo.madre);
        for (Nodo hijo : nodo.hijos) {
            count += contarNodos(hijo);
        }
        return count;
    }

    // --- FIN MÉTODOS AUXILIARES ---

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        return (nodo != null) ? nodo.persona : null;
    }

    /** @return true si hay alguien en esa posición (false también si la posición no es válida) */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        if (persona == null) throw new IllegalArgumentException("La persona no puede ser null");
        
        persona.validar();
        String posStr = persona.getPosicion();

        if (posStr.equals("1")) {
            throw new ArbolInvalidoException("No se puede agregar otro sujeto en la posición 1");
        }
        if (contiene(posStr)) {
            throw new ArbolInvalidoException("La posición ya existe en el árbol: " + posStr);
        }

        if (!posStr.contains(".")) {
            // Reglas para ascendientes
            long n = Long.parseLong(posStr);
            long nHijo = n / 2;
            Nodo nodoHijo = buscarNodo(String.valueOf(nHijo));
            
            if (nodoHijo == null) {
                throw new ArbolInvalidoException("Falta el hijo del cual depende el ascendiente " + n);
            }
            if (persona.getNacimiento() >= nodoHijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("Un ascendiente debe haber nacido antes que su hijo");
            }
            
            Nodo nuevo = new Nodo(persona);
            if (n % 2 == 0) nodoHijo.padre = nuevo;
            else nodoHijo.madre = nuevo;
            
        } else {
            // Reglas para descendientes
            int lastDot = posStr.lastIndexOf('.');
            String parentStr = posStr.substring(0, lastDot);
            Nodo padre = buscarNodo(parentStr);
            
            if (padre == null) {
                throw new ArbolInvalidoException("Falta el progenitor del cual depende el descendiente " + posStr);
            }
            if (persona.getNacimiento() <= padre.persona.getNacimiento()) {
                throw new ArbolInvalidoException("Un descendiente debe haber nacido después de su progenitor");
            }
            
            Nodo nuevo = new Nodo(persona);
            padre.hijos.add(nuevo);
            
            // Mantener la lista de hijos ordenada por número para los recorridos
            padre.hijos.sort((n1, n2) -> {
                String[] p1 = n1.persona.getPosicion().split("\\.");
                String[] p2 = n2.persona.getPosicion().split("\\.");
                return Integer.compare(Integer.parseInt(p1[p1.length - 1]), Integer.parseInt(p2[p2.length - 1]));
            });
        }
        
        tamano++;
        return persona;
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        if (!contiene(posicion)) throw new ArbolInvalidoException("La posición no existe");
        if (posicion.contains(".")) throw new ArbolInvalidoException("No se pueden agregar padres a descendientes");
        
        long n = Long.parseLong(posicion);
        String nuevaPos = String.valueOf(n * 2);
        Persona nueva = new Persona(nuevaPos, nombre, nacimiento, fallecimiento);
        return agregar(nueva);
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
       if (!contiene(posicion)) throw new ArbolInvalidoException("La posición no existe");
        if (posicion.contains(".")) throw new ArbolInvalidoException("No se pueden agregar madres a descendientes");
        
        long n = Long.parseLong(posicion);
        String nuevaPos = String.valueOf(n * 2 + 1);
        Persona nueva = new Persona(nuevaPos, nombre, nacimiento, fallecimiento);
        return agregar(nueva);
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        if (!contiene(posicion)) throw new ArbolInvalidoException("El progenitor no existe");
        if (!posicion.equals("1") && !posicion.contains(".")) {
            throw new ArbolInvalidoException("Solo se pueden agregar hijos al sujeto y a sus descendientes");
        }
        
        Nodo padre = buscarNodo(posicion);
        int maxNum = 0;
        for (Nodo hijo : padre.hijos) {
            String[] partes = hijo.persona.getPosicion().split("\\.");
            int num = Integer.parseInt(partes[partes.length - 1]);
            if (num > maxNum) maxNum = num;
        }
        
        String nuevaPos = posicion + "." + (maxNum + 1);
        Persona nueva = new Persona(nuevaPos, nombre, nacimiento, fallecimiento);
        return agregar(nueva);
    }

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        if (posicion == null || posicion.isEmpty()) throw new ArbolInvalidoException("Posición inválida");
        if (posicion.equals("1")) throw new ArbolInvalidoException("El sujeto no se puede eliminar");
        
        Nodo actual = buscarNodo(posicion);
        if (actual == null) throw new ArbolInvalidoException("La posición a eliminar no existe");
        
        int borrados = contarNodos(actual);
        
        // Desconectar del árbol principal
        if (!posicion.contains(".")) {
            long n = Long.parseLong(posicion);
            Nodo hijo = buscarNodo(String.valueOf(n / 2));
            if (hijo != null) {
                if (n % 2 == 0) hijo.padre = null;
                else hijo.madre = null;
            }
        } else {
            int lastDot = posicion.lastIndexOf('.');
            String parentStr = posicion.substring(0, lastDot);
            Nodo padre = buscarNodo(parentStr);
            if (padre != null) {
                padre.hijos.remove(actual);
            }
        }
        
        tamano -= borrados;
        return borrados;
    }

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     *         Este orden garantiza que cada persona aparece DESPUÉS de la que necesita (útil para guardar y cargar).
     */
    public List<Persona> miembros() {
        List<Persona> lista = new ArrayList<>();
        lista.add(raiz.persona);
        lista.addAll(ascendientes());
        lista.addAll(descendientes());
        return lista;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
        List<Persona> lista = new ArrayList<>();
        recolectarAscendientes(raiz.padre, lista);
        recolectarAscendientes(raiz.madre, lista);
        
        // Ordenar numéricamente por Ahnentafel
        lista.sort((p1, p2) -> Long.compare(
            Long.parseLong(p1.getPosicion()), 
            Long.parseLong(p2.getPosicion())
        ));
        return lista;
    }
    private void recolectarAscendientes(Nodo nodo, List<Persona> lista) {
        if (nodo == null) return;
        lista.add(nodo.persona);
        recolectarAscendientes(nodo.padre, lista);
        recolectarAscendientes(nodo.madre, lista);
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();
        for (Nodo hijo : raiz.hijos) {
            recolectarDescendientes(hijo, lista);
        }
        return lista;
    }

    private void recolectarDescendientes(Nodo nodo, List<Persona> lista) {
        if (nodo == null) return;
        lista.add(nodo.persona);
        for (Nodo hijo : nodo.hijos) {
            recolectarDescendientes(hijo, lista);
        }
    }
}