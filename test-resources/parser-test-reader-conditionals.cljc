;; A collection of records and functions to work with points in two and three dimensions.
(ns somerville.geometry.point
  (:require
    [taoensso.timbre :as log]
    [somerville.geometry.commons :as c]))

;; define a two dimensional point
#?(:clj
   (defrecord Point2 [x y]
     java.lang.Comparable
     (java.lang.Comparable/compareTo
       [this other]
       (if
         (= (:x this) (:x other))
         (c/compareTo (:y this) (:y other))
         (c/compareTo (:x this) (:x other))))
     c/Printable
     (c/out [this i] (str (c/indent i) "Point (" x "," y ")"))
     (c/out [this] (c/out this 0))))

;; define a two dimensional point
#?(:cljs
   (defrecord Point2 [x y]
     IComparable
     (-compare
       [this other]
       (if
         (= (:x this) (:x other))
         (c/compareTo (:y this) (:y other))
         (c/compareTo (:x this) (:x other))))
     c/Printable
     (c/out [this i] (str (c/indent i) "Point (" x "," y ")"))
     (c/out [this] (c/out this 0))))

;; define a three dimensional point
#?(:clj
   (defrecord Point3 [x y z]
     java.lang.Comparable
     (java.lang.Comparable/compareTo
       [this other]
       (if
         (= (:x this) (:x other))
         (if
           (= (:y this) (:y other))
           (c/compareTo (:z this) (:z other))
           (c/compareTo (:y this) (:y other)))
         (c/compareTo (:x this) (:x other))))
     c/Printable
     (c/out [this i] (str (c/indent i) "Point (" x "," y "," z ")"))
     (c/out [this] (c/out this 0))))

;; define a three dimensional point
#?(:cljs
   (defrecord Point3 [x y z]
     IComparable
     (-compare
       [this other]
       (if
         (= (:x this) (:x other))
         (if
           (= (:y this) (:y other))
           (c/compareTo (:z this) (:z other))
           (c/compareTo (:y this) (:y other)))
         (c/compareTo (:x this) (:x other))))
     c/Printable
     (c/out [this i] (str (c/indent i) "Point (" x "," y "," z ")"))
     (c/out [this] (c/out this 0))))

(defn point
  "Create a point in either 2 or 3 dimensions."
  ([x y]
   (Point2. x y))
  ([x y z]
   (Point3. x y z)))

