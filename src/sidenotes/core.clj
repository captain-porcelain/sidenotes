(ns sidenotes.core
  (:require
    [sidenotes.fs :as fs]
    [sidenotes.parser :as parser]
    [sidenotes.renderer :as renderer]
    [clojure.edn :as edn]))

(defn read-deps
  "Read the given deps.edn file and return the contained dependencies and source paths."
  [file]
  (if (fs/file? file)
    (select-keys (edn/read-string (slurp file)) [:deps :paths])
    {:deps [] :paths []}))

(def default-settings
  {:output-to "docs"
   :theme "standard"})

(defn read-settings
  "Read the settings from a config file."
  [file]
  (if (fs/file? file)
    (edn/read-string (slurp file))
    {}))

(defn load-settings
  "Read the settings from a config file."
  [file]
  (let [settings (merge default-settings (read-settings file))
        tmp (fs/ensure-directory! (:output-to settings))]
    settings))

(defn parse-source
  "Parse one source file."
  [source]
  {:file source
   :sections (parser/parse-file source)
   :ns (parser/parse-ns source)})

(defn parse-sources
  "Create a map of source files to parsed contents."
  [sources]
  (map parse-source sources))

(defn -main
  "Generate the documentation."
  [& args]
  (try
    (let [settings (load-settings "sidenotes.edn")
          project (read-deps "deps.edn")
          sources (fs/find-sources (:paths project))
          parsed-sources (parse-sources sources)]
      (renderer/render parsed-sources project settings))
    (catch Exception e (.printStackTrace e))))
