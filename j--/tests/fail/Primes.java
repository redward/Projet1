// Primes.java fail test
// Authors : Creciun Eduard, Lambert Andrew

package fail;

/**
 * Primes Class.
 * @author Groupe 20
 */
public final class Primes {
    /**
     * Le constructeur private par defaut.
     */
    private Primes() {
    }

    /**
     * La methode principale.
     * @param args Les arguments entres en parametres
     */
    public static void main(final String[] args) {
        final Primes p = new Primes();
        p.primes(2147483647); // MAX_VALUE
        p.primes('a'); 
        System.out.println(p.primes(0));
    }
}