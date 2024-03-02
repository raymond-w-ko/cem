(ns cem.term.renderer
  (:require [cem.macros :refer [bb]]
            [cem.term.ops :refer [disable-italic disable-underline
                                  enable-blink enable-bold
                                  enable-classic-underline enable-dim
                                  enable-italic move-cursor
                                  reset-display-attributes set-bg-color
                                  set-fg-color]])
  (:import [cem.term Rect TermRenderState]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^Rect drawn-root-rect
  "This is the root rect that has been drawn.
   It represents the current state of the terminal."
  (new Rect))
(def ^Rect pending-root-rect
  "This is the root rect that is pending to be drawn.
   It represents the state of the terminal that we want to achieve."
  (new Rect))

(defn render! []
  (bb
   num-rows (.-numRows pending-root-rect)
   num-cols (.-numCols pending-root-rect)
   n (* num-rows num-cols)
   (.resize drawn-root-rect num-rows num-cols)
   f (fn [trs i]
       (bb
        row (quot i num-cols)
        col (mod i num-cols)
        sb (.-sb trs)

        cell (.getCellIfDifferent pending-root-rect row col drawn-root-rect)
        (when (and cell (<= 0 (.codePoint cell)))
          ;; move cursor if necessary
          (when-not (and (= row (.-lastRow trs)) (= col (inc (.-lastCol trs))))
            (.append sb (move-cursor col row)))
          ;; detect if we need to reset general display attributes
          (when (or (not= (.-lastDim trs) (.-dim cell))
                    (not= (.-lastBold trs) (.-bold cell))
                    (not= (.-lastBlink trs) (.-blink cell)))
            (.append sb (reset-display-attributes)))
          ;; detect if we need to enable dim
          (when (.-dim cell)
            (.append sb (enable-dim))
            (set! (.-lastDim trs) (.-dim cell)))
          ;; detect if we need to enable bold
          (when (.-bold cell)
            (.append sb (enable-bold))
            (set! (.-lastBold trs) (.-bold cell)))
          ;; detect if we need to enable blink
          (when (.-blink cell)
            (.append sb (enable-blink))
            (set! (.-lastBlink trs) (.-blink cell)))
          (.updateBackingStore pending-root-rect row col drawn-root-rect)
          ;; italic is special, we need to detect if we need to enable or disable it
          ;; unlike the above it's not a shared reset and just enable
          (when (not= (.-lastItalic trs) (.-italic cell))
            (if (.-italic cell)
              (.append sb (enable-italic))
              (.append sb (disable-italic)))
            (set! (.-lastItalic trs) (.-italic cell)))
          ;; same for underline
          (when (not= (.-lastUnderline trs) (.-underline cell))
            (if (.-underline cell)
              (.append sb (enable-classic-underline))
              (.append sb (disable-underline)))
            (set! (.-lastUnderline trs) (.-underline cell)))
          ;; set background color if necessary
          (when (not= (.-lastBgColor trs) (.-bgColor cell))
            (.append sb (set-bg-color (.-bg_r cell) (.-bg_g cell) (.-bg_b cell)))
            (set! (.-lastBgColor trs) (.-bgColor cell)))
          ;; set foreground color if necessary
          (when (not= (.-lastFgColor trs) (.-fgColor cell))
            (.append sb (set-fg-color (.-fg_r cell) (.-fg_g cell) (.-fg_b cell)))
            (set! (.-lastFgColor trs) (.-fgColor cell)))
          nil)
        trs))
   trs (new TermRenderState)
   (reduce f trs (range n))))
