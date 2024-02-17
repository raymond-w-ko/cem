(ns cem.term.atoms)

(def *term-dim (atom {:rows nil :cols nil}))
(defonce *initial-termios (atom nil))

(defonce *string-caps (atom {}))
(defonce *esc-timeout-ms (atom 250))
