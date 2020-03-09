# Sidenotes

A clojure documentation generator forked from [marginalia](https://github.com/gdeer81/marginalia/)

The documentation for sidenotes itself can be found [here](https://captain-porcelain.github.io/sidenotes/index.html)

## Features

It is limited to projects that use deps.edn.

In the current early version the following features are supported:
- Generating table of contents containing dependencies and namespaces
- One html file per namespace showing code and comments side by side
- Support for markdown in comments
- Disabling / Enabling adding comments to the documentation with @SidenotesDisable and @SidenotesEnable inside comments
- Choosing the filename for the table of contents

## Differences to Marginalia

- Limited to projects using deps.edn
- No Latex output
- No uberdoc output
- Javascript and CSS are not injected into html files but kept separate
- Uses clostache/mustache for templating
- A new theme `sidenotes` in addition to the classic `marginalia`
- Inclusion of the project readme in the table of contents page if it is written in markdown

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
in your project root. It can also be used to add a project name and description. The project name is otherwise
read for the current folder.

```clojure
{:projectname "Sidenotes"
 :description "Clojure documentation generator"
 :output-to "docs"
 :toc-filename "index.html"
 :theme "marginalia or sidenotes"}
```

## Future Work

- Enable external themes
- Add a setting for inclusion of the readme
