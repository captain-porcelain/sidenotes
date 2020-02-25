build: repl

clean:
		rm -rf target/

package:
		clojure -A:cambada -m cambada.uberjar -m sidenotes.core -a sidenotes.core

tests:
		clojure -A:test -m kaocha.runner

test-watch:
		clojure -A:test -m kaocha.runner --watch

repl:
		clojure -A:repl -m repl 7878

outdated:
		clojure -A:outdated

publish:
		clojure -A:publish
