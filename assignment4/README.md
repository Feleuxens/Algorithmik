# Algorithm Engineering, Sheet 4

## Build

```
make build
```

Needs Rust 1.85+

## Problem 1

```
make run problem1 100000000
```

Builds A = 0..n-1 and, for B shrinking by halves, prints the running time of the
naive (merge), binary-search and galloping intersection.

## Problem 2

```
make run problem2 movies.txt.bz2
```

Input is one movie per line, `title<TAB>plot`. Plain text or bzip2 works (the
format is taken from the file contents, not the name; bzip2 needs `bzcat` on the
PATH). Type a query and you get the movies whose title and plot contain all the
words, ranked by inverse document frequency with extra weight on title hits.
Each query also reports the time for the hashmap index, the sorted-sequence
index and a plain naive scan.
