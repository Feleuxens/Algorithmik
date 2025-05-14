JAVAC=javac
JAVA=java
SRCDIR=src
BIN=bin

CLASSPATH=$(JUNIT):$(BIN)

make:
	mkdir -p $(BIN)
	$(JAVAC) -d $(BIN) $(SRCDIR)/*.java
	$(JAVA) -Xmx16G -cp $(BIN) Main
