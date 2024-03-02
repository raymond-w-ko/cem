(ns cem.term.channels 
  (:require [clojure.core.async :refer [chan]]))

(defonce request-stop-rendering-ch (chan 1))
(defonce stop-rendering-done-ch (chan 1))
