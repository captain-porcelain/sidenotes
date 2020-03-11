;; The entry point for generating documentation with sidenotes.core
;;
;; Since all configuration is done with a sidenotes.edn file in the
;; project there is not much that needs to be done here.
;;
;; The actual usage is to call the main function inside the project root.
;; I expect this to be done via an alias in deps.edn as outlined in the
;; README.
(ns sidenotes.core
  (:require
    [sidenotes.fs :as fs]
    [sidenotes.parser :as parser]
    [sidenotes.renderer :as renderer]
    [clojure.edn :as edn])
  (:gen-class))

(defn read-deps
  "Read the given deps.edn file and return the contained dependencies and source paths."
  [file]
  (if (fs/file? file)
    (select-keys (edn/read-string (slurp file)) [:deps :paths])
    {:deps [] :paths []}))

;; The default settings write the documentation with the marginalia theme to
;; the docs folder. And prompts for project description too.
(def default-settings
  {:description "Add description in sidenotes.edn..."
   :output-to "docs"
   :toc-filename "toc.html"
   :include-readme true
   :theme "marginalia"})

(defn read-settings
  "Read the settings from a config file."
  [file]
  (if (fs/file? file)
    (edn/read-string (slurp file))
    {}))

(defn load-settings
  "Merge the settings from a config file with the defaults to fill in missing bits."
  [file]
  (let [fallback {:projectname (fs/project-folder)}
        settings (merge default-settings fallback (read-settings file))
        tmp (fs/ensure-directory! (:output-to settings))]
    settings))

(defn parse-source
  "Parse one source file."
  [source]
  (let [sections (parser/parse-file source)
        result-msg (if (nil? (:error (first sections))) " ... parsed" (str " ... error: " (:error (first sections))))
        tmp (dorun (print (str "\t" (fs/shorten source) result-msg)))
        ns (parser/parse-ns source)]
    {:file source
     :sections (parser/parse-file source)
     :type (fs/file-extension source)
     :ns (parser/parse-ns source)}))

(defn parse-sources
  "Create a list of parsed source files."
  [sources]
  (dorun (println "Generating docs for:"))
  (map parse-source sources))

(defn generate-documentation
  "Generate the documentation without further checks."
  [settings]
  (try
    (let [project (read-deps "deps.edn")
          readme (fs/load-readme)
          sources (fs/find-sources (:paths project))
          parsed-sources (parse-sources sources)]
      (renderer/render parsed-sources project settings readme))
    (catch Exception e (.printStackTrace e))))

(defn perform-checks
  "Check that the config is valid."
  [settings]
  (if (renderer/external-theme? (:theme settings))
    (if (renderer/external-theme-valid? (:theme settings))
      true
      (do
        (dorun (println renderer/message-theme-invalid))
        false))
    true))

(defn -main
  "Generate the documentation."
  [& args]
  (try
    (let [settings (load-settings "sidenotes.edn")]
      (when (perform-checks settings)
        (generate-documentation settings)))
    (catch Exception e (.printStackTrace e))))
