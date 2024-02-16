(ns com.cem.ed.term.kitty
  (:require [clojure.core.async :as async :refer [<!! alts!! timeout]]
            [com.cem.ed.macros :refer [bb]]
            [com.cem.ed.term.constants :refer [csi]])
  (:import [java.util ArrayList]))

(def kitty-keyboard-protocol-begin-code "Kitty Keyboard Protocol Begin" (str csi ">1u"))
(def kitty-keyboard-protocol-end-code "Kitty Keyboard Protocol End" (str csi "<u"))

;; CSI number ; modifiers [u~]
;; CSI 1; modifiers [ABCDEFHPQS]
(def escape-code-terminators
  #{0x75 ;; u
    0x7e ;; ~
    0x41 ;; A
    0x42 ;; B
    0x43 ;; C
    0x44 ;; D
    0x45 ;; E
    0x46 ;; F
    0x48 ;; H
    0x50 ;; P
    0x51 ;; Q
    0x53 ;; S
    })

(def func-key-lookup-table
  {["27" "u"] "escape"
   ["13" "u"] "enter"
   ["9" "u"] "tab"
   ["127" "u"] "backspace"
   ["2" "~"] "insert"
   ["3" "~"] "delete"
   ["1" "D"] "left"
   ["1" "C"] "right"
   ["1" "A"] "up"
   ["1" "B"] "down"
   ["5" "~"] "page-up"
   ["6" "~"] "page-down"

   ["1" "H"] "home"
   ["1" "F"] "end"
   ["7" "~"] "home"
   ["8" "~"] "end"

   ["1" "P"] "f1"
   ["1" "Q"] "f2"
   ["1" "R"] "f4"
   ["1" "S"] "f5"

   ["11" "~"] "f1"
   ["12" "~"] "f2"
   ["13" "~"] "f3"
   ["14" "~"] "f4"
   ["15" "~"] "f5"
   ["17" "~"] "f6"
   ["18" "~"] "f7"
   ["19" "~"] "f8"
   ["20" "~"] "f9"
   ["21" "~"] "f10"
   ["23" "~"] "f11"
   ["24" "~"] "f12"})

(def escape-code-regex #"(\d+);(\d+)*([u~ABCDEFHPQS])")


(defn begin-kitty-keyboard-protocol! [out!] (out! kitty-keyboard-protocol-begin-code))
(defn end-kitty-keyboard-protocol! [out!] (out! kitty-keyboard-protocol-end-code))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-mods
  "Returns a set of modifier keys from a kitty key even modifier number string."
  [s]
  (bb
   n (Integer/parseInt s)
   n (- n 1)
   (cond-> #{}
     (bit-test n 0) (conj :shift)
     (bit-test n 1) (conj :alt)
     (bit-test n 2) (conj :ctrl)
     (bit-test n 3) (conj :super)
     (bit-test n 4) (conj :hyper)
     (bit-test n 5) (conj :meta)
     (bit-test n 6) (conj :caps-lock)
     (bit-test n 7) (conj :num-lock))))

(defn read-escape-code! [ch esc-timeout-ms [_esc-b esc-t]]
  (bb
   [x _] (alts!! [ch (timeout esc-timeout-ms)])
   :return-when (nil? x) [[#{} "escape"] esc-t]
   ;; left bracket, first char of []
   [b _t] x
   :return-when (not= b 0x5b) nil

   buf (new ArrayList 16)
   *t (atom esc-t)
   (loop []
     (when-let [[b t] (<!! ch)]
       (reset! *t t)
       (.add buf b)
       (when-not (contains? escape-code-terminators b)
         (recur))))
   esc-code-str (new String (int-array buf) 0 (.size buf))
  ;;  (println esc-code-str)
   m (re-find (re-matcher escape-code-regex esc-code-str))
   :return-when (nil? m) nil
   [_ num-str mods-num-str suffix] m
   mods (parse-mods mods-num-str)
   k (get func-key-lookup-table [num-str suffix])
   :return-when k [[mods k] @*t]
   n (Integer/parseInt num-str)
   k (new String (int-array [n]) 0 1)
   [[mods k] @*t]))
