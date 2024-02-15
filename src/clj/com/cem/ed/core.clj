(ns com.cem.ed.core
  (:require [cider.nrepl :refer [cider-nrepl-handler]]
            [clojure.core.async :as async :refer [<!!]]
            [com.cem.ed.term :as term]
            [nrepl.server :as nrepl-server])
  (:import [java.lang System]
           [sun.misc Signal SignalHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn -main
  [& _args]
  (Signal/handle (new Signal "INT") (reify SignalHandler
                                      (handle [_this _sig]
                                        (term/teardown!)
                                        (System/exit 0))))
  (nrepl-server/start-server :port 6888 :handler cider-nrepl-handler)
  (term/init!)
  (term/setup)
  (<!! (term/stdin-read-loop!))
  (term/die-properly!))
