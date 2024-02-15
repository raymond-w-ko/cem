(ns com.cem.ed.utils
  (:import [java.time Instant]))

(defn get-timestamp []
  (let [now (Instant/now)]
    (.truncatedTo now (java.time.temporal.ChronoUnit/MILLIS))))
