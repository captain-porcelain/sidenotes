(ns sidenotes.renderer
  (:require
    [sidenotes.fs :as fs]
    [markdown.core :as md]
    [clojure.edn :as edn]
    [clostache.parser :as mustache]))

(def theme-base "themes/")

(defn find-theme
  "Find and the path to the used theme."
  [theme-name]
  (str theme-base theme-name))


(defn transform-dependency
  "Transform a dependency so mustache can handle it. ![alt](https://raw.githubusercontent.com/adam-p/markdown-here/master/src/common/images/icon48.png)"
  [dep]
  {:name (first dep)
   :is-mvn (not (nil? (:mvn/version (second dep))))
   :version (:mvn/version (second dep))
   :is-git (not (nil? (:git/url (second dep))))
   :url (:git/url (second dep))
   :sha (:sha (second dep))})

(defn transform-dependencies
  "Transform the list of dependencies for mustache."
  [deps]
  (map transform-dependency deps))

(defn transform-section
  "Transform the comments and parse contained markdown."
  [section]
  (assoc section :docstring (md/md-to-html-string (:docstring section))))

(defn transform-parsed-source
  "Transform the comments and parse contained markdown."
  [parsed-source]
  (assoc parsed-source :sections (map transform-section (:sections parsed-source))))

(defn toc-template-parameters
  "Build the parameters for the mustache templates."
  [parsed-sources project settings]
  {:settings settings
   :dependencies (transform-dependencies (:deps project))
   :sources parsed-sources})

(defn ns-template-parameters
  "Build the parameters for the mustache templates."
  [parsed-source project settings]
  {:settings settings
   :dependencies (transform-dependencies (:deps project))
   :source (transform-parsed-source parsed-source)})


(defn render-toc
  "Render the table of contents."
  [parsed-sources project settings theme]
  (let [toc-template (fs/slurp-resource (str theme "/toc.html"))
        filename (str (:output-to settings) "/toc.html")
        params (toc-template-parameters parsed-sources project settings)]
    (spit filename (mustache/render toc-template params))))

(defn render-ns
  "Render the page for one namespace."
  [parsed-source project settings theme]
  (let [toc-template (fs/slurp-resource (str theme "/ns.html"))
        filename (str (:output-to settings) "/" (:ns parsed-source) ".html")
        params (ns-template-parameters parsed-source project settings)]
    (spit filename (mustache/render toc-template params))))

(defn copy-resources
  "Copy resources from theme to docs folder."
  [settings theme]
  (dorun
    (map #(fs/spit-resource % theme (:output-to settings))
         (:resources
           (edn/read-string
             (fs/slurp-resource (str theme "/theme.edn")))))))

(defn render
  "Render the documentation."
  [parsed-sources project settings]
  (let [theme (find-theme (:theme settings))]
    (copy-resources settings theme)
    (render-toc parsed-sources project settings theme)
    (dorun (map #(render-ns % project settings theme) parsed-sources))))

