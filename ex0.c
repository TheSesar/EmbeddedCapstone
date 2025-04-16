/*
 * Selim Saridede
 * 2270929
 * sesar@uw.edu
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>


double nilakantha_estimate(uint64_t n); // declaration for funtion nilakantha_estimate

/*
 * calculates the estimate for Pi for the first n terms using nilanktha estimate
 * takes an argumet as int,
 * if the argument is not an integer (i.e char or string)then program returns only the 0th term which is pi = 3.0
 * handles if the argument is too large (not in range for unsigned 64-bit integer)
 * If the argument is a floting number, then program casts it to int.
 */
int main(int argc, char* argv[]) {
    
    // checks if the user's input has exatly two arguments, fails otherwise 
    if (argc != 2) {
        printf("Error: The program should have two arguments!\n");
        return EXIT_FAILURE;
    }

    // converts string argument to an 64-bit unsgined integer
    // atoi handles conversion from string to integer value even if there is string after the value
    int64_t n = atoi(argv[1]); // takes value for n from the second argument

    // checks if n is within the desired range
    if ((uint64_t)n >= UINT64_MAX || n < 0){
        printf("Error: Second argument is either too large or too small!\n");
        return EXIT_FAILURE;
    }


    double estimate = nilakantha_estimate((uint64_t)n); // estimate of pi
    printf("Our estimate of Pi is %.20f\n", estimate); // prints out the estimation
    return EXIT_SUCCESS;
}

// finds the Nilakantha estimate for pi for the first n terms
double nilakantha_estimate(uint64_t n) {
    double estimate = 3.0; // result from the estimation of pi starting from the 0th term
    int64_t sign = 1; // sign for the nth term

    for (uint64_t i = 1; i <= n; i++) {
        estimate += sign * (4.0 / ((2.0 * i) * (2.0 * i + 1) * (2.0 * i + 2)));
        sign *= -1; // odd number terms have positive, even number terms have negative sign (0th term is positive)
    }

    return estimate;
}

