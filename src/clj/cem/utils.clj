(ns cem.utils
  (:import [java.time Instant]))

(defn get-timestamp []
  (let [now (Instant/now)]
    (.truncatedTo now (java.time.temporal.ChronoUnit/MILLIS))))

(defn rand-int-between
  "Returns a random integer between a and b, inclusive."
  [a b]
  (+ a (rand-int (- b a))))
