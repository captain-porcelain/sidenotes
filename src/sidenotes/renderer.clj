;; The renderer is responsible for converting parsed contents into html files.
;; It uses clostache templates from a selectable theme folder to create them.
(ns sidenotes.renderer
  (:require
    [sidenotes.fs :as fs]
    [markdown.core :as md]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [clostache.parser :as mustache]))

(def theme-base "themes/")

(defn find-theme
  "Find and the path to the used theme."
  [theme-name]
  (str theme-base theme-name))


(defn transform-dependency
  "Transform a dependency so mustache can handle it."
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

(defn error-md
  "Get markdown for an error."
  [section]
  (str "###ERROR\n" (:error section)))

(defn stacktrace-code
  "Convert stacktrace to code."
  [exception]
  (string/join "\n" (map str (.getStackTrace exception))))

(defn transform-section
  "Transform the comments and parse contained markdown."
  [section]
  (case (:type section)
    :error (assoc section :docstring (md/md-to-html-string (error-md section)) :raw (stacktrace-code (:exception section)))
    :code (assoc section :docstring (md/md-to-html-string (:docstring section)) :span false)
    :comment (assoc section :docstring (md/md-to-html-string (:raw section)) :raw "" :span true)))

(defn transform-parsed-source
  "Transform the comments and parse contained markdown."
  [parsed-source]
  (assoc parsed-source :sections (map transform-section (:sections parsed-source))))

(defn transform-readme
  "Check if a readme exists and contains markdown. If so convert it to html."
  [readme]
  (if (and (:has-readme readme) (= "md" (:type readme)))
    (assoc readme :html (md/md-to-html-string (:content readme)))
    readme))

(defn toc-template-parameters
  "Build the parameters for the mustache templates."
  [parsed-sources project settings readme]
  {:settings settings
   :dependencies (transform-dependencies (:deps project))
   :readme (transform-readme readme)
   :sources parsed-sources})

(defn ns-template-parameters
  "Build the parameters for the mustache templates."
  [parsed-source project settings]
  {:settings settings
   :dependencies (transform-dependencies (:deps project))
   :source (transform-parsed-source parsed-source)})


(defn render-toc
  "Render the table of contents."
  [parsed-sources project settings readme theme]
  (let [toc-template (fs/slurp-resource (str theme "/toc.html"))
        filename (str (:output-to settings) "/" (:toc-filename settings))
        tmp (dorun (println (str "Creating table of contents: " filename)))
        params (toc-template-parameters parsed-sources project settings readme)]
    (spit filename (mustache/render toc-template params))))

(defn render-ns
  "Render the page for one namespace."
  [parsed-source project settings theme]
  (let [toc-template (fs/slurp-resource (str theme "/ns.html"))
        filename (str (:output-to settings) "/" (:ns parsed-source) ".html")
        tmp (dorun (println (str " ... rendering to " filename)))
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
  [parsed-sources project settings readme]
  (let [theme (find-theme (:theme settings))]
    (copy-resources settings theme)
    (dorun (map #(render-ns % project settings theme) parsed-sources))
    (render-toc parsed-sources project settings readme theme)
    ))

