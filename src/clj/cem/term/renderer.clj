(ns cem.term.renderer
  (:require [cem.macros :refer [bb]]
            [cem.term.constants :refer [code-point-string-cache out!]]
            [cem.term.ops :refer [disable-italic disable-underline
                                  enable-blink enable-bold
                                  enable-classic-underline enable-dim
                                  enable-italic move-cursor
                                  reset-display-attributes set-bg-color
                                  set-fg-color]]
            [cem.term.state :refer [drawn-root-rect root-rect
                                    term-render-state]]
            [cem.utils :refer [rand-int-between]])
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmacro cwhen
  {:clj-kondo/lint-as 'clojure.core/when}
  [test & body]
  (let [gx (gensym 'test)]
    `(let [~gx ~test]
       (when (and (not= 0 ~gx) ~gx)
         ~@body))))

(defmacro cif
  {:clj-kondo/lint-as 'clojure.core/if}
  [test a b]
  (let [gx (gensym 'test)]
    `(let [~gx ~test]
       (if (and (not= 0 ~gx) ~gx)
         ~a
         ~b))))

(defn render! []
  (bb
   num-rows (.-numRows root-rect)
   num-cols (.-numCols root-rect)
   n (* num-rows num-cols)
   (.resize drawn-root-rect num-rows num-cols)
   f (fn [trs i]
       (bb
        row (quot i num-cols)
        col (mod i num-cols)
        sb (.-sb trs)

        cell (.getCellIfDifferent root-rect row col drawn-root-rect)
        (when (and cell (<= 0 (.-codePoint cell)))
          ;; move cursor if necessary
          (when-not (and (= row (.-lastRow trs)) (= col (inc (.-lastCol trs))))
            (.append sb (move-cursor col row)))
          ;; detect if we need to reset general display attributes
          (when (or (not= (.-lastDim trs) (.-dim cell))
                    (not= (.-lastBold trs) (.-bold cell))
                    (not= (.-lastBlink trs) (.-blink cell)))
            (.append sb (reset-display-attributes)))
          ;; dim
          (cwhen (.-dim cell) (.append sb (enable-dim)))
          (set! (.-lastDim trs) (.-dim cell))
          ;; bold
          (cwhen (.-bold cell) (.append sb (enable-bold)))
          (set! (.-lastBold trs) (.-bold cell))
          ;; blink
          (cwhen (.-blink cell) (.append sb (enable-blink)))
          (set! (.-lastBlink trs) (.-blink cell))
          ;; italic is special, we need to detect if we need to enable or disable it
          ;; unlike the above it's not a shared reset and just enable
          (when (not= (.-lastItalic trs) (.-italic cell))
            (.append sb (cif (.-italic cell) (enable-italic) (disable-italic)))
            (set! (.-lastItalic trs) (.-italic cell)))
          ;; same for underline
          (when (not= (.-lastUnderline trs) (.-underline cell))
            (.append sb (cif (.-underline cell) (enable-classic-underline) (disable-underline)))
            (set! (.-lastUnderline trs) (.-underline cell)))
          ;; set background color if necessary
          (when (not= (.-lastBgColor trs) (.-bgColor cell))
            (.append sb (set-bg-color (.-bg_r cell) (.-bg_g cell) (.-bg_b cell)))
            (set! (.-lastBgColor trs) (.-bgColor cell)))
          ;; set foreground color if necessary
          (when (not= (.-lastFgColor trs) (.-fgColor cell))
            (.append sb (set-fg-color (.-fg_r cell) (.-fg_g cell) (.-fg_b cell)))
            (set! (.-lastFgColor trs) (.-fgColor cell)))
          ;; append the code point
          (let [s (.codePointToString code-point-string-cache (.-codePoint cell))]
            (.append sb s))
          (.updateBackingStore root-rect row col drawn-root-rect)
          nil)
        trs))
   (.reset term-render-state)
   (reduce f term-render-state (range n))
   sb (.-sb term-render-state)
   (out! (.toString sb))
   :render-done))

(defn randomize-some-cells! []
  (dorun
   (for [i (range 100)]
     (let [^ints data (.-data root-rect)
           j (* i 4)
           cp-index (+ j 0)
           fg-index (+ j 1)
           bg-index (+ j 2)
           misc-index (+ j 3)]
       (aset data cp-index (rand-int-between 0x20 0x7E))
       (aset data fg-index 0x00FF0000)
       (aset data bg-index 0)
       (aset data misc-index 0x00000000)
   nil))))

(comment (do
           (randomize-some-cells!)
           (render!))
         nil)
