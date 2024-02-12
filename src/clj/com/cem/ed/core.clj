(ns com.cem.ed.core
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [com.cem.ed.macros :refer [bb]]
            [com.cem.ed.term :as term]
            [nrepl.server :as nrepl-server])
  (:import [java.lang System]
           [sun.misc Signal SignalHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (Signal/handle (new Signal "INT") (reify SignalHandler
                                      (handle [_this _sig]
                                        (term/teardown!)
                                        (System/exit 0))))
  (nrepl-server/start-server :port 6888 :handler cider-nrepl-handler)
  (term/init!)
  (term/setup)
  ;; (System/exit 0)
  nil)
