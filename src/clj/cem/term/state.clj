(ns cem.term.state 
  (:require [clojure.core.async :refer [chan]])
  (:import [cem.term Rect TermRenderState]))

(def *term-dim (atom {:rows nil :cols nil}))
(defonce *initial-termios (atom nil))

(defonce *string-caps (atom {}))
(defonce *esc-timeout-ms (atom 250))

(defonce request-stop-rendering-ch (chan 1))
(defonce stop-rendering-done-ch (chan 1))


(def ^Rect drawn-root-rect
  "This is the root rect that has been drawn.
   It represents the current state of the terminal."
  (new Rect))
(def ^Rect root-rect
  "This is the root rect that is pending to be/currently is being drawn.
   It represents the state of the terminal that we want to achieve."
  (new Rect))

(def ^TermRenderState term-render-state (new TermRenderState))
