(ns sidenotes.renderer-test
  (:require
    [sidenotes.renderer :as renderer]
    [markdown.core :as md])
  (:use clojure.test))

(def hr-form
  {:form "****************************************************************************************************
Lines",
   :text [";; ****************************************************************************************************" ";; Lines"],
   :start 14,
   :end 15,
   :type :comment,
   :raw "****************************************************************************************************\nLines"})

(deftest markdown-creates-hr
  (is (= "" (md/md-to-html-string "---")))
  (is (= "<hr/>" (md/md-to-html-string "***")))
  )

(deftest section-spacer-comment-creates-hr
  (let [section (renderer/transform-section hr-form)]
    (is (= "<hr/>Lines" (:docstring section)))))

