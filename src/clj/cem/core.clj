(ns cem.core
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.core.async :as async :refer [<!!]]
            [cem.macros :refer [bb]]
            [cem.term :as term]
            [nrepl.server :as nrepl-server])
  (:import [java.lang System]
           [sun.misc Signal SignalHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [& _args]
  (bb
  ;;  (System/setProperty "clojure.core.async.go-checking" "true")
   x (System/getenv "COLORTERM")
   :return-when (not x) (throw (new Exception "COLORTERM not set"))
   (when (and (not= x "truecolor") (not= x "24bit"))
     (throw (new Exception "COLORTERM must be set to 'truecolor' or '24bit'"))))

  (Signal/handle (new Signal "INT") (reify SignalHandler
                                      (handle [_this _sig]
                                        (term/teardown!)
                                        (System/exit 0))))
  (nrepl-server/start-server :port 6888 :handler cider-nrepl-handler)
  (term/init!)
  (term/setup)
  (term/render-loop!)
  (<!! (term/stdin-read-loop!))
  (term/die-properly!))