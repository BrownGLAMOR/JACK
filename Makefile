# Simple makefile for building JACK

JAVAC = javac
JAVAFLAGS = -Xlint
SRCDIR = src
CLASSPATH = bin

# Get a list of all of the *.java files

JAVAFILES = $(shell find $(SRCDIR) -name *.java)

# Build the entire jack package every time a file is changed

all: $(JAVAFILES)
	mkdir -p $(CLASSPATH)
	cd $(SRCDIR); find . -name *.xsd | cpio -pdm ../$(CLASSPATH)
	$(JAVAC) $(JAVAFLAGS) -d $(CLASSPATH) -cp $(CLASSPATH) $^

# Remove all of the *.class files

clean:
	rm -rf $(CLASSPATH)
