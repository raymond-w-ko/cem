(ns cem.term.ops
  (:refer-clojure :exclude [*in* *out* *err*]) 
  (:require [cem.term.state :refer [*string-caps]]
            [cem.term.constants :refer [csi]]))

(defn clear [] (get @*string-caps "clear"))
(defn alternate-screen [] (get @*string-caps "smcup"))
(defn normal-screen [] (get @*string-caps "rmcup"))
(defn disable-line-wrap [] (get @*string-caps "rmam"))
(defn enable-line-wrap [] (get @*string-caps "smam"))
(defn reset-color-output [] (str csi "0m"))
(defn set-bg-color [r g b] (str csi "48;2;" r ";" g ";" b "m"))
(defn set-fg-color [r g b] (str csi "38;2;" r ";" g ";" b "m"))
(defn move-cursor [col row] (str csi (inc row) ";" (inc col) "H"))
(defn reset-display-attributes [] (get @*string-caps "sgr0"))
(defn enable-dim [] (get @*string-caps "dim"))
(defn enable-bold [] (get @*string-caps "bold"))
(defn enable-blink [] (get @*string-caps "blink"))

(defn enable-italic [] (get @*string-caps "sitm"))
(defn disable-italic [] (get @*string-caps "ritm"))

(defn enable-classic-underline [] (get @*string-caps "smul"))
(defn disable-underline [] (get @*string-caps "rmul"))
