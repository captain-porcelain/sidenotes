;; This namespace contains all the functions we need to handle accessing the filesystem as well as
;; resources inside the project itself.
(ns sidenotes.fs
  (:require
    [clojure.string :as string]
    [clojure.java.io :as io]))

(defn file?
  "Check if the given path points to an existing file."
  [path]
  (let [file (java.io.File. path)]
    (and (.exists file) (.isFile file))))

(defn ls
  "Performs roughly the same task as the UNIX `ls`.  That is, returns a seq of the filenames
   at a given directory.  If a path to a file is supplied, then the seq contains only the
   original path given."
  [path]
  (let [file (java.io.File. path)]
    (if (.isDirectory file)
      (seq (.list file))
      (when (.exists file)
        [path]))))

(defn mkdir
  "Create a directory for the given path."
  [path]
  (.mkdirs (io/file path)))

(defn ensure-directory!
  "Ensure that the directory specified by `path` exists.  If not then make it so.
   Here is a snowman ☃"
  [path]
  (when-not (ls path)
    (mkdir path)))

(defn dir?
  "Many Marginalia fns use dir? to recursively search a filepath."
  [path]
  (.isDirectory (java.io.File. path)))

(defn shorten
  "Shorten full file path by removing current dir prefix."
  [path]
  (let [pwd (.getAbsolutePath (java.io.File. ""))]
    (if (.startsWith path pwd) (.substring path (inc (count pwd))) path)))

(defn file-extension
  "Returns a string containing the files extension."
  [filename]
  (second (re-find #"\.([^.]+)$" filename)))

(defn find-file-extension
  "Returns a string containing the files extension for a File instance."
  [^java.io.File file]
  (file-extension (.getName file)))

(defn processable-file?
  "Predicate. Returns true for \"normal\" files with a file extension which
  passes the provided predicate."
  [pred ^java.io.File file]
  (when (.isFile file)
    (-> file find-file-extension pred)))

(defn find-processable-file-paths
  "Returns a seq of processable file paths (strings) in alphabetical order by
  namespace."
  [dir pred]
  (->> (io/file dir)
       (file-seq)
       (filter (partial processable-file? pred))
       (sort)
       (map #(.getCanonicalPath %))))

(def ^:private file-extensions #{"clj" "cljs" "cljx" "cljc"})

(defn find-sources
  "Given a collection of filepaths, returns a lazy sequence of filepaths to all
   .clj, .cljs, .cljx, and .cljc files on those paths: directory paths will be searched
   recursively for files."
  [sources]
  (if (nil? sources)
    (find-processable-file-paths "./src" file-extensions)
    (->> sources
         (mapcat #(if (dir? %)
                    (find-processable-file-paths % file-extensions)
                    [(.getCanonicalPath (io/file %))])))))

(defn find-readme
  "Try to find the readme file."
  []
  (first (filter #(.startsWith (string/lower-case %) "readme") (ls "."))))

(defn load-readme
  "Load the readme file if possible."
  []
  (let [readme (find-readme)]
    (if (nil? readme)
      {:has-readme false}
      {:has-readme true
       :file readme
       :type (string/lower-case (find-file-extension (io/file readme)))
       :content (slurp readme)})))

(defn project-folder
  "Find the name of the project from current folder."
  []
  (last (clojure.string/split (str (io/as-url (io/file ""))) #"/")))

(defn slurp-resource
  "Stolen from leiningen"
  [resource-name]
  (try
    (-> (.getContextClassLoader (Thread/currentThread))
        (.getResourceAsStream resource-name)
        (java.io.InputStreamReader.)
        (slurp))
    (catch java.lang.NullPointerException npe
      (println (str "Could not locate resources at " resource-name))
      (println "    ... attempting to fix.")
      (let [resource-name resource-name]
        (try
          (-> (.getContextClassLoader (Thread/currentThread))
              (.getResourceAsStream resource-name)
              (java.io.InputStreamReader.)
              (slurp))
          (catch java.lang.NullPointerException npe
            (println (str "    STILL could not locate resources at " resource-name ". Giving up!"))))))))

(defn spit-resource
  "Load a resource and spit it to the output folder."
  [resource from to]
  (ensure-directory! (str to "/" (string/join "/" (butlast (string/split resource #"/")))))
  (dorun (println (str "\t" resource " to " to)))
  (spit (str to "/" resource) (slurp-resource (str from "/" resource))))

(defn copy-file
  "Copy a file to the output folder."
  [file from to]
  (ensure-directory! (str to "/" (string/join "/" (butlast (string/split file #"/")))))
  (dorun (println (str "\t" file " to " to)))
  (io/copy (io/file (str from "/" file)) (io/file (str to "/" file))))

