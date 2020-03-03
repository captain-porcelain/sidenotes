(ns sidenotes.parser-test
  (:require [sidenotes.parser :as parser])
  (:use clojure.test))


(def read-lines-text "line1\nline2
line3\r\nline4")

(deftest read-lines
  (let [lines (parser/read-lines read-lines-text)]
    (is (= 4 (count lines)))
    (is (= "line1" (nth lines 0)))
    (is (= "line2" (nth lines 1)))
    (is (= "line3" (nth lines 2)))
    (is (= "line4" (nth lines 3)))))


(def skip-spaces-and-comments-text
  ";; ignore me
  :keyword")

(deftest skipping-spaces-and-comments
  (binding [parser/*comments* (atom [])]
    (let [rdr (parser/line-numbering-reader skip-spaces-and-comments-text)
          tmp (parser/skip-spaces-and-comments rdr)]
      (is (= (char (.read rdr)) \:))
      (is (= (char (.read rdr)) \k))
      (is (= (char (.read rdr)) \e))
      (is (= (char (.read rdr)) \y))
      (is (= (char (.read rdr)) \w))
      (is (= (char (.read rdr)) \o))
      (is (= (char (.read rdr)) \r))
      (is (= (char (.read rdr)) \d)))))


(def keyword-text
  ":keyword")

(deftest reading-keyword
  (binding [parser/*comments* (atom [])]
    (let [rdr (parser/line-numbering-reader keyword-text)]
      (is (= :keyword (:content (parser/read-keyword rdr \:)))))))


(deftest reader-conditionals
  (let [source (slurp "./test-resources/parser-test-reader-conditionals.cljc")
        parsed (parser/parse source)
        ;tmp (dorun (map #(println (str " --->\n" % "\n\n")) parsed))
        sections (parser/parse-into-sections source)
        ;tmp (dorun (map #(println (str " --->\n" % "\n\n")) sections))
        ]
    (is (= 11 (count parsed)))
    (is (=  6 (count sections)))))


(deftest section-comments
  (let [source (slurp "./test-resources/parser-test-section-comments.cljc")
        parsed (parser/parse source)
        ;tmp (dorun (map #(println (str " --->\n" % "\n\n")) parsed))
        sections (parser/parse-into-sections source)
        tmp (dorun (map #(println (str " --->\n" % "\n\n")) sections))
        ]
    (is (= 6 (count parsed)))
    (is (= 4 (count sections)))))
