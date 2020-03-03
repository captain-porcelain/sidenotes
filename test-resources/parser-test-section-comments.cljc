;; A collection of records and functions to work with points in two and three dimensions.
(ns somerville.geometry.point
  (:require
    [taoensso.timbre :as log]
    [somerville.geometry.commons :as c]))

(defn point
  "Create a point in either 2 or 3 dimensions."
  ([x y]
   (Point2. x y))
  ([x y z]
   (Point3. x y z)))

;; ====================================================================================================
;; Lines

(defn line
  "Create a line between two points."
  [p1 p2]
   (Line. p1 p2))

