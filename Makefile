SHELL := /bin/bash
VERSION=$(shell cat version)

build: repl

clean:
		rm -rf pom.xml
		rm -rf target/
		rm -rf docs/*

package:
		clojure -A:cambada -m cambada.jar -m sidenotes.core --app-version $(VERSION)

tests:
		clojure -A:test -m kaocha.runner

test-watch:
		clojure -A:test -m kaocha.runner --watch --no-capture-output

repl:
		clojure -A:repl -m repl 7878

outdated:
		clojure -A:outdated

install:
		clojure -A:publish install target/sidenotes-$(VERSION).jar

publish:
		echo ${CLOJARS_USERNAME}
		clojure -A:publish deploy target/sidenotes-$(VERSION).jar

