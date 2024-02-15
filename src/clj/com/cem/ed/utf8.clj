(ns com.cem.ed.utf8
  (:require [clojure.core.async :as async :refer [<!!]]
            [com.cem.ed.macros :refer [bb]]))

(defn utf8-4-bytes-to-code-point [ch b1]
  (bb
   [b2 _t] (<!! ch)
   [b3 _t] (<!! ch)
   [b4 t] (<!! ch)
   cp (bit-or (bit-shift-left (bit-and b1 2r00000111) 18)
              (bit-shift-left (bit-and b2 2r00111111) 12)
              (bit-shift-left (bit-and b3 2r00111111) 6)
              (bit-and b4 0x3f))
   [cp t]))

(defn utf8-3-bytes-to-code-point [ch b1]
  (bb
   [b2 _t] (<!! ch)
   [b3 t] (<!! ch)
   cp (bit-or (bit-shift-left (bit-and b1 2r00001111) 12)
              (bit-shift-left (bit-and b2 2r00111111) 6)
              (bit-and b3 2r00111111))
   [cp t]))

(defn utf8-2-bytes-to-code-point [ch b1]
  (bb
   [b2 t] (<!! ch)
   cp (bit-or (bit-shift-left (bit-and b1 2r00011111) 6)
              (bit-and b2 2r00111111))
   [cp t]))

(defn channel+first-byte->cp+t
  "Returns a [int code point, timestamp] pair from a channel of bytes and the first byte."
  [ch [first-byte first-t]]
  (cond
    (= 2r11110000 (bit-and first-byte 2r11110000)) (utf8-4-bytes-to-code-point ch first-byte)
    (= 2r11100000 (bit-and first-byte 2r11100000)) (utf8-3-bytes-to-code-point ch first-byte)
    (= 2r11000000 (bit-and first-byte 2r11000000)) (utf8-2-bytes-to-code-point ch first-byte)
    :else [first-byte first-t]))

(defn channel+first-byte->key-event
  "Returns a [int code point, timestamp] pair from a channel of bytes and the first byte."
  [ch [first-byte first-t]]
  (bb
   [cp t] (channel+first-byte->cp+t ch [first-byte first-t])
   k (new String (int-array [cp]) 0 1)
   [[#{} k] t]))
