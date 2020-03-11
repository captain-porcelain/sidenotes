# Sidenotes

[![Clojars Project](https://img.shields.io/clojars/v/sidenotes.svg)](https://clojars.org/sidenotes)

A clojure documentation generator forked from [marginalia](https://github.com/gdeer81/marginalia/) that displays
code and comments side by side.

The documentation for sidenotes itself can be found [here](https://captain-porcelain.github.io/sidenotes/index.html)

## Features

It is limited to projects that use deps.edn.

Currently the following features are supported:
- Generating table of contents containing dependencies and namespaces
- One html file per namespace showing code and comments side by side
- Support for markdown in comments
- Disabling / Enabling adding comments to the documentation with `@SidenotesDisable` and `@SidenotesEnable` inside comments
- Choosing the filename for the table of contents
- Support for external themes

## Differences to Marginalia

- Limited to projects using deps.edn
- No Latex output
- No uberdoc output
- Javascript and CSS are not injected into html files but kept separate
- Uses clostache/mustache for templating
- A new theme `sidenotes` in addition to the classic `marginalia`
- Inclusion of the project readme in the table of contents page if it is written in markdown
- Support for external themes

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
read for the current folder. Other settings are available as well:

```clojure
{:projectname "Sidenotes"
 :description "Clojure documentation generator"
 :output-to "docs"
 :toc-filename "index.html"
 :include-readme true
 :theme "marginalia or sidenotes"}
```

| Setting | Description |
| ------- | ----------- |
| `:projectname` | Sets the name of the project in the table of contents page |
| `:description` | Sets the description of the project in the table of contents page |
| `:output-to` | The folder the documentation will be written to, defaults to `docs` |
| `:toc-filename` | The filename of the table of contents page. Defaults to `toc.html` |
| `:include-readme` | Include the readme (if it is markdown) in the table of contents page. Available only in `sidenotes` theme |
| `:theme` | The theme to use. Defaults to `marginalia` |
|  | Select `marginalia` if you want it similar to what [marginalia](https://github.com/gdeer81/marginalia/) generates |
|  | Select `sidenotes` if you want the new css grid based layout with new features |

## External Themes

Sidenotes is able to use your own theme in addition to the included ones.

If you'd like to create your own you need three files for it:

### `toc.html`

A mustache template that renders the table of contents. It is a good idea to start with the template in the `sidenotes` theme to
see which parameters it takes to build the table of contents.

### `ns.html`

A mustache template that is used for every namespace. Again it is wise to start with the existing one.

### `theme.edn`

In case your theme uses additional resources like css, JavaScript or images this file need to contain references to them, so
sidenotes knows to copy them into the output folder. It should look like this:

```clojure
{:resources
 ["css/sidenotes.css"
  "css/shCore.css"
  "css/shThemeSidenotes.css"
  "js/jquery-1.7.1.min.js"
  "js/xregexp-min.js"
  "js/shCore.js"
  "js/shBrushClojure.js"
  "js/app.js"
  "img/spacer.svg"
  "img/cljs-white.svg"
  "img/Clojure_logo.svg"]}
```

## Future Work

Currently I have no features in mind that need to be added to sidenotes. If you would like to see
something let me know about it or create a pull request for it.
