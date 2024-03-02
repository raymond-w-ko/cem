(ns cem.term.init
  (:refer-clojure :exclude [*in* *out* *err*])
  (:require [cem.macros :refer [bb]]
            [cem.term.state :refer [*string-caps]]
            [cem.term.constants :refer [code-point-widths
                                        code-point-widths-file
                                        code-point-widths-file-path
                                        num-code-points resources-path
                                        stdout-fd]]
            [clojure.java.io :as io])
  (:import [cem.platform.linux LibC Ncurses]
           [com.sun.jna Pointer]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-string-cap! [^String capname]
  (bb
   p (Ncurses/tigetstr capname)
   nv (Pointer/nativeValue p)

   :return-when (= nv (long -1))
   (throw (new Exception (str "failed to load string capability: " capname)))

   cap (.getString p 0)
   (swap! *string-caps assoc capname cap)))

(defn load-string-caps! []
  (load-string-cap! "clear")
  (load-string-cap! "rmcup") ;; normal screen
  (load-string-cap! "smcup") ;; alternate screen
  (load-string-cap! "rmam") ;; disable line wrap
  (load-string-cap! "smam") ;; enable line wrap

  (load-string-cap! "sgr0") ;; turn off all attribute modes
  (load-string-cap! "dim") ;; enable dim (half-bright)
  (load-string-cap! "bold") ;; enable bold
  (load-string-cap! "blink") ;; enable blink

  (load-string-cap! "sitm") ;; enable italics
  (load-string-cap! "ritm") ;; disable italics

  (load-string-cap! "smul") ;; enable straight underline (classic)
  (load-string-cap! "rmul") ;; disable underline
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn load-code-point-widths! []
  (if-let [res (io/resource code-point-widths-file)]
    ;; this should run 100% of the time in production
    (let [x (io/input-stream res)]
      (with-open [f (new java.io.DataInputStream x)]
        (.readFully f code-point-widths)))
    ;; this should only be run in development to update the code-point-widths file
    (when (.isDirectory (io/file resources-path))
      (dorun (for [i (range num-code-points)]
               (aset code-point-widths i (byte (LibC/wcwidth i)))))
      (with-open [f (new java.io.FileOutputStream code-point-widths-file-path)]
        (.write f code-point-widths)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn init! []
  ;; low level setup
  (Ncurses/setupterm nil stdout-fd nil)
  ;; loading constants and lookup tables
  (load-string-caps!)
  (load-code-point-widths!)
  :term-init-done)
