(ns repl
  (:require
    [clojure.java.io :as io]
    [nrepl.server :as nrepl-server]
    [cider.nrepl :refer [cider-nrepl-handler]]
    [rebel-readline.main :as rebel]))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn -main
  [& args]
  (let [port 7888
        port (if (< 0 (count args)) (Integer/parseInt (first args)) 7888)]
    (do
      (dorun (println "Starting nrepl server on port " port))
      (nrepl-server/start-server :port port :handler (nrepl-handler))
      (spit ".nrepl-port" (str port))
      (rebel/-main)
      (io/delete-file ".nrepl-port")))
  (System/exit 0))
