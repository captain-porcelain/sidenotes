;; The renderer is responsible for converting parsed contents into html files.
;; It uses clostache templates from a selectable theme folder to create them.
(ns sidenotes.renderer
  (:require
    [sidenotes.fs :as fs]
    [markdown.core :as md]
    [clojure.edn :as edn]
    [clojure.string :as string]
    [clostache.parser :as mustache]))

;; The folder where internal themes can be found.
(def theme-base "themes/")

;; The list of internal themes. It would be much nicer if this could be automatically read from the
;; resources of the jar, but so far I have no idea how that could be done.
(def internal-themes ["marginalia" "sidenotes"])

(defn in?
  "Check that itm is not in itms."
  [itm itms]
  (not (false? (nil? (some #(= itm %) itms)))))

(defn external-theme-valid?
  "Check if an external theme is sane. It needs to have theme.edn, toc.html and ns.html files."
  [theme-name]
  (and
    (fs/dir? theme-name)
    (fs/file? (str theme-name "/theme.edn"))
    (fs/file? (str theme-name "/toc.html"))
    (fs/file? (str theme-name "/ns.html"))))

(def message-theme-invalid
  "The theme you have provided is not valid. Make sure that it contains the following files:
  theme.edn
  toc.html
  ns.html")

(defn external-theme?
  "Check if the provided theme is external."
  [theme-name]
  (in? theme-name internal-themes))

(defn find-theme
  "Find the path to the used theme."
  [theme-name]
  (if (external-theme? theme-name)
    theme-name
    (str theme-base theme-name)))

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
  (assoc parsed-source
         :sections (map transform-section (:sections parsed-source))
         :is-cljs (= "cljs" (:type parsed-source))
         :is-cljc (= "cljc" (:type parsed-source))
         :is-clj  (= "clj" (:type parsed-source))))

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
   :sources (map transform-parsed-source parsed-sources)})

(defn ns-template-parameters
  "Build the parameters for the mustache templates."
  [parsed-source project settings]
  {:settings settings
   :dependencies (transform-dependencies (:deps project))
   :source (transform-parsed-source parsed-source)})


(defn render-toc
  "Render the table of contents."
  [parsed-sources project settings readme theme external]
  (let [toc-file (str theme "/toc.html")
        toc-template (if external (slurp toc-file) (fs/slurp-resource toc-file))
        filename (str (:output-to settings) "/" (:toc-filename settings))
        tmp (dorun (println (str "Creating table of contents: " filename)))
        params (toc-template-parameters parsed-sources project settings readme)]
    (spit filename (mustache/render toc-template params))))

(defn render-ns
  "Render the page for one namespace."
  [parsed-source project settings theme external]
  (let [ns-file (str theme "/ns.html")
        ns-template (if external (slurp ns-file) (fs/slurp-resource ns-file))
        filename (str (:output-to settings) "/" (:ns parsed-source) ".html")
        tmp (dorun (println (str " ... rendering to " filename)))
        params (ns-template-parameters parsed-source project settings)]
    (spit filename (mustache/render ns-template params))))

(defn copy-resources
  "Copy resources from theme to docs folder."
  [settings theme external]
  (dorun (println "Copying resources:"))
  (dorun
    (map #(fs/spit-resource % theme (:output-to settings))
         (:resources
           (edn/read-string
             (fs/slurp-resource (str theme "/theme.edn")))))))

(defn copy-files
  "Copy files from theme to docs folder."
  [settings theme external]
  (dorun (println "Copying resources:"))
  (dorun
    (map #(fs/copy-file % theme (:output-to settings))
         (:resources
           (edn/read-string
             (slurp (str theme "/theme.edn")))))))

(defn render
  "Render the documentation."
  [parsed-sources project settings readme]
  (let [theme (find-theme (:theme settings))
        external (external-theme? (:theme settings))]
    (dorun (map #(render-ns % project settings theme external) parsed-sources))
    (render-toc parsed-sources project settings readme theme external)
    (if external
      (copy-files settings theme external)
      (copy-resources settings theme external))))

