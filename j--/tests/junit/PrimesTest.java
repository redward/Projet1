// Primes.java junit tester
// Authors : Creciun Eduard, Lambert Andrew

package junit;

import junit.framework.TestCase;
import pass.Primes;

/**
 * La classe Prime2Test.
 * @author Groupe 20
 */
public class PrimesTest extends TestCase {
    /**
     * Objet Primes au nom p.
     */
    private Primes p;

    /**
     * Le constructeur private de PrimesTest.
     */
    public PrimesTest() {
    }

    /**
     * Le setUp des tests.
     * @throws Exception au cas ou il ya des erreurs
     */
    protected final void setUp() throws Exception {
        super.setUp();
        this.p = new Primes();
    }

    /**
     * Cette methode permet de verifier le resultat attendu de la
     * methode primes de Primes.
     * @throws Exception Quand il ya des erreurs en entree
     */
   // @Test(expected=java.lang.NegativeArraySizeException.class)
    public final void testPrimes() throws Exception {
        final int[] septante = new int[]{2, 3, 5, 7, 11, 13,
        17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67};
        final int[] ints = new int[]{2, 5, 70};
        final int trois = 3;
        assertTrue(Primes.equals(this.p.primes(ints[0]), 
                new int[] {2}));
        assertTrue(Primes.equals(this.p.primes(ints[1]), 
                new int[] {2, trois, ints[1]}));
        assertTrue(Primes.equals(this.p.primes(0), new int[] {}));
        assertTrue(Primes.equals(this.p.primes(ints[2]), septante));
        assertFalse(Primes.equals(this.p.primes('5'), new int[] {}));
      //  assertFalse(Primes.equals(this.p.primes(2147483647), new int[] {}));
    }
    /**
     * Le tearDowm de la classe.
     * @throws Exception au cas ou il y a des erreurs
     */
    protected final  void tearDown() throws Exception {
        super.tearDown();
    }
}
