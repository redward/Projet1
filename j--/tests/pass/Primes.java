// Authors : Creciun Eduard, Lambert Andrew

package pass;

import java.lang.System;
import java.lang.Integer;

public class Primes {

    // /**
    // * Permet de d�finir un tableau vide, il est utilis� dans un retour de
    // * fonction.
    // */
    private static int[] eMPTY = {}; // If there is no prime

    public Primes() {
    }
    // /**
    // * La m�thode statique permet de retourner un tableau avec des nombre
    // * premiers en ordre croissant.
    // * @param n
    // *            Le nombre Maximum pour un nombre premier
    // * @return un tableau d'entiers avec des nombres premiers
    // */
    public int[] primes(int n) {
        // Sieve of Eratosthenes algorithm
        int[] isPrime = new int[n + 1];
        int i = 2;
        while (i <= n) {
            isPrime[i] = 1;
            i = i + 1;
        }

        // mark non-primes <= N using Sieve of Eratosthenes
        i = 2;
        int nbPrimes = 0;
        while (i <= n) {
            // if i is prime, then mark multiples of i as nonprime
            // suffices to consider mutiples i, i+1, ..., N/i
            if (isPrime[i] == 1) {
                int j = i;
                while (i * j <= n) {
                    isPrime[i * j] = 0;
                    j = j + 1;
                    }
                nbPrimes = nbPrimes + 1;
                }
            i = i + 1;
        }

        // add each prime number to the result array
        int incrementIndex = 0;
        if (nbPrimes > 0) {
            int[] primes = new int[nbPrimes];
            i = 2;
            while (incrementIndex <= nbPrimes - 1 && i <= n) {
                if (isPrime[i] == 1) {
                    primes[incrementIndex] = i;
                    incrementIndex = incrementIndex + 1;
                }
                i = i + 1;
            }
            return primes;
        }
        return eMPTY;
    }

    // /**
    // * Cette m�thode transforme le tabeau d'entiers en un String.
    // * @param a
    // *            le tableau a transfomer
    // */
    public static String toString(int[] a) {
        int i = 0;
        String toShow = "";
        while (i <= a.length - 1) {
            toShow = toShow + a[i];
            i = i + 1;
            if (i <= a.length - 1) {
                toShow = toShow + " ";
            }
        }
        return toShow;
    }

    // /**
    // * Permet de comparer 2 tableaux.
    // * @param a Le premier tanleau
    // * @param b Le 2e tableau
    // * @return True si les 2 tableaus sont les memes
    // */
    public static boolean equals(int[] a, int[] b) {
        int i = 0;
        if (a.length == b.length) {
            if (a.length > 0 && b.length > 0) {
                while (i <= a.length - 1) {
                    if (b.length > i && a[i] == b[i]) {
                            i = i + 1;
                    } else {
                        return false;
                    } 
                }
                if (i == b.length) {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    // /**
    // * La m�thode principale de la classe Primes.
    // * @param args
    // *            Les arguments du progrmme
    // */
    public static void main(String[] args) {
        Primes p = new Primes();
        int n = Integer.parseInt(args[0]);
        System.out.println("Primes( " + n + " ) = " + Primes.toString(p.primes(n)));
    }

}