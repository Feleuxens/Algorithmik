# Algorithm Engineering -- Assignment 4
#
#   make build                      compile both binaries
#   make run problem1 <n>           run Problem 1 with array size n   (default: $(N))
#   make run problem2 [datafile]    run Problem 2 search engine       (default: $(DATA))
#   make test                       run unit tests
#   make clean                      remove build artifacts
#
# The positional words after `run` are also accepted as variables, e.g.
#   make run problem1 N=100000000
#   make run problem2 DATA=movies.txt.bz2

CARGO ?= cargo
N     ?= 100000000          # default array size for Problem 1
DATA  ?= movies.txt.bz2     # default data file for Problem 2 (plain text OR bz2)

BIN1  := target/release/problem1
BIN2  := target/release/problem2

.PHONY: build run test clean help

build:
	$(CARGO) build --release

test:
	$(CARGO) test --release

clean:
	$(CARGO) clean

help:
	@echo "make build"
	@echo "make run problem1 <n>         (n defaults to $(N))"
	@echo "make run problem2 [datafile]  (defaults to $(DATA))"

# --- support `make run problem1 <n>` / `make run problem2 [datafile]` --------
# Make reads the words after `run` as additional goals, so capture them and
# define each as a no-op target to keep Make from erroring on them.
ifeq (run,$(firstword $(MAKECMDGOALS)))
  RUN_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
  $(foreach a,$(RUN_ARGS),$(eval $(a):;@:))
endif

WHICH := $(word 1,$(RUN_ARGS))
EXTRA := $(wordlist 2,$(words $(RUN_ARGS)),$(RUN_ARGS))

run: build
ifeq ($(WHICH),problem1)
	$(BIN1) $(or $(EXTRA),$(N))
else ifeq ($(WHICH),problem2)
	$(BIN2) $(or $(EXTRA),$(DATA))
else
	@echo "usage: make run problem1 <n> | make run problem2 [datafile]"; exit 2
endif