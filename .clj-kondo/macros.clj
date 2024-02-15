(ns macros
  (:require [clj-kondo.hooks-api :as api]))

(defn args [& xs]
  (let [vars (-> xs next next)]
    `(clojure.core/assoc ~'args :dummy (clojure.core/vector ~@vars))))

(defmacro enable-obj-bitfield-option! [o field flag]
  `(let [x# (. ~o ~field)]
     (set! (. ~o ~field) (bit-or x# ~flag))))

(defmacro disable-obj-bitfield-option! [o field flag]
  `(let [x# (. ~o ~field)]
     (set! (. ~o ~field) (bit-and x# (bit-not ~flag)))))
