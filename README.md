# Sidenodes

A clojure documentation generator forked from [marginalia](https://github.com/gdeer81/marginalia/).

The documentation for sidenotes itself can be found [here](https://captain-porcelain.github.io/sidenotes/toc.html)

## Features

It is limited to projects that use deps.edn.

In the current early version the following features are supported:
- Generating table of contents containing dependencies and namespaces
- One html file per namespace showing code and comments side by side
- Support for markdown in comments

## Differences to Marginalia

- Limited to projects using deps.edn
- No Latex output
- No uberdoc output
- Javascript and CSS are not injected into html files but kept separate
- Uses clostache/mustache for templating, with support for multiple themes, although only one is implemented yet.

## Usage and Configuration

Add an alias to your deps.edn file

```clojure
:sidenotes
{:extra-deps
{sidenotes/sidenotes {:mvn/version "RELEASE"}}
:main-opts  ["-m" "sidenotes.core"]}
```

and call it

```bash
clojure -A:sidenotes

```

This will create the documentation in a folder called docs. You can change this by creating a file sidenotes.edn
in your project root. It can also be used to add a project name and description.

```clojure
{:projectname "Sidenotes"
 :description "Clojure documentation generator"
 :output-to "docs"
 :theme "marginalia"}
```

## Future Work

- Fall back to project folder for project name when not set in sidenotes.edn
- Add second theme that is more colorful
- Enable external themes
