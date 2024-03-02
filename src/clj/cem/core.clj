(ns cem.core
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.core.async :as async :refer [<!!]]
            [cem.macros :refer [bb]]
            [cem.term :as term]
            [cem.term.init :as term-init]
            [nrepl.server :as nrepl-server])
  (:import [java.lang System]
           [sun.misc Signal SignalHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check-running-env! []
  (bb
   x (System/getenv "COLORTERM")
   :return-when (not x) (throw (new Exception "COLORTERM not set"))
   (when (and (not= x "truecolor") (not= x "24bit"))
     (throw (new Exception "COLORTERM must be set to 'truecolor' or '24bit'"))))
  :pass)

(defn -main
  [& _args]
  (bb
   (System/setProperty "clojure.core.async.go-checking" "true")

   (check-running-env!)
   (nrepl-server/start-server :port 6888 :handler cider-nrepl-handler)

   (term-init/init!)
   (term/setup!)
   (<!! (term/stdin-read-loop!))
   (term/die-properly!)))
