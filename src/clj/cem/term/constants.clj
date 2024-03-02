(ns cem.term.constants
  (:refer-clojure :exclude [*in* *out* *err*])
  (:require [cem.macros :refer [bb]]) 
  (:import [cem.term CodePointStringCache]
           [java.io FileDescriptor FileInputStream FileOutputStream]))

(def csi "Control Sequence Introducer" "\u001B[")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def stdin-fd 0)
(def stdout-fd 1)
;; (def stderr-fd 2)
(def ^:dynamic ^FileInputStream *in* (new FileInputStream (FileDescriptor/in)))
(def ^:dynamic ^FileOutputStream *out* (new FileOutputStream (FileDescriptor/out)))
;; (def ^:dynamic *err* (new FileOutputStream (FileDescriptor/err)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def resources-path "src/resources")

(def num-code-points 0x110000)
(def ^"[B" code-point-widths (byte-array 0x110000)) ;; ]
(def code-point-widths-file "code-point-widths.bin")
(def code-point-widths-file-path (str resources-path "/" code-point-widths-file))

(def ^CodePointStringCache code-point-string-cache (new CodePointStringCache))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn out!
  "Writes a String (which may contain control characters) to the standard output stream."
  [^String s]
  (bb
   ^"[B" bites (.getBytes s "UTF-8") ;; ]
   (.write *out* bites)))

(defn flush-stdout! []
  (.flush *out*))
