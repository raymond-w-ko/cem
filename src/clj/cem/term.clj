(ns cem.term
  (:refer-clojure :exclude [*in* *out* *err*])
  (:require [cem.macros :refer [->hash bb disable-obj-bitfield-option!]]
            [cem.term.atoms :refer [*esc-timeout-ms *initial-termios
                                    *string-caps *term-dim]]
            [cem.term.constants :refer [*in* *out* code-point-widths
                                        code-point-widths-file
                                        code-point-widths-file-path csi
                                        num-code-points resources-path
                                        stdin-fd stdout-fd]]
            [cem.term.kitty :as kitty :refer [begin-kitty-keyboard-protocol!
                                              end-kitty-keyboard-protocol!]]
            [cem.utf8 :refer [channel+first-byte->key-event]]
            [cem.utils :refer [get-timestamp rand-int-between]]
            [clojure.core.async :as async :refer [<! >! alts! buffer chan
                                                  close! go]]
            [clojure.java.io :as io])
  (:import [cem.platform.linux
            LibC
            LibC$Termios
            LibC$Winsize
            Ncurses]
           [com.sun.jna Pointer]
           [java.lang System]
           [sun.misc Signal SignalHandler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update-terminal-size! []
  (let [ws (new LibC$Winsize)
        ret (LibC/ioctl 0 LibC/TIOCGWINSZ ws)]
    (if (zero? ret)
      (let [rows (.-ws_row ws)
            cols (.-ws_col ws)
            new-dim (->hash rows cols)]
        (reset! *term-dim new-dim))
      (throw (new Exception "failed to get terminal size with ioctl()")))))

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

(defn load-code-point-widths! []
  (if-let [res (io/resource code-point-widths-file)]
    (let [x (io/input-stream res)]
      (with-open [f (new java.io.DataInputStream x)]
        (.readFully f code-point-widths)))
    (when (.isDirectory (io/file resources-path))
      (dorun (for [i (range num-code-points)]
               (aset code-point-widths i (byte (LibC/wcwidth i)))))
      (with-open [f (new java.io.FileOutputStream code-point-widths-file-path)]
        (.write f code-point-widths)))))

(defn init! []
  (Ncurses/setupterm nil stdout-fd nil)
  (load-string-caps!)
  (load-code-point-widths!))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn out! [^String s]
  (bb
   bites (.getBytes s "UTF-8")
   (.write *out* bites)))

(defn flush! [] (.flush *out*))
(defn clear! [] (out! (get @*string-caps "clear")))
(defn alternate-screen! [] (out! (get @*string-caps "smcup")))
(defn normal-screen! [] (out! (get @*string-caps "rmcup")))
(defn disable-line-wrap! [] (out! (get @*string-caps "rmam")))
(defn enable-line-wrap! [] (out! (get @*string-caps "smam")))
(defn reset-color-output! [] (out! csi) (out! "0m"))
(defn set-bg-color! [r g b] (out! csi) (out! (str "48;2;" r ";" g ";" b "m")))
(defn set-fg-color! [r g b] (out! csi) (out! (str "38;2;" r ";" g ";" b "m")))
(defn move-cursor! [x y] (out! (str csi (inc y) ";" (inc x) "H")))
(defn reset-display-attributes! [] (out! (get @*string-caps "sgr0")))
(defn enable-dim! [] (out! (get @*string-caps "dim")))
(defn enable-bold! [] (out! (get @*string-caps "bold")))
(defn enable-blink! [] (out! (get @*string-caps "blink")))
(defn enable-italic! [] (out! (get @*string-caps "sitm")))
(defn disable-italic! [] (out! (get @*string-caps "ritm")))
(defn enable-classic-underline! [] (out! (get @*string-caps "smul")))
(defn disable-underline! [] (out! (get @*string-caps "rmul")))

(defn setup-termios! []
  (bb
   termios (new LibC$Termios)
   (reset! *initial-termios termios)
   (LibC/tcgetattr stdin-fd termios)
  ;; termios_p->c_iflag &= ~(IGNBRK | BRKINT | PARMRK | ISTRIP
  ;;                                | INLCR | IGNCR | ICRNL | IXON);
  ;; termios_p->c_oflag &= ~OPOST;
  ;; termios_p->c_lflag &= ~(ECHO | ECHONL | ICANON | ISIG | IEXTEN);
  ;; termios_p->c_cflag &= ~(CSIZE | PARENB);
  ;; termios_p->c_cflag |= CS8;
   ed-mode-termios (new LibC$Termios)
   (LibC/tcgetattr stdin-fd ed-mode-termios)
   (disable-obj-bitfield-option! ed-mode-termios -c_iflag LibC/IXON)
   (disable-obj-bitfield-option! ed-mode-termios -c_iflag LibC/ICRNL)

   (disable-obj-bitfield-option! ed-mode-termios -c_lflag LibC/ICANON)
   (disable-obj-bitfield-option! ed-mode-termios -c_lflag LibC/ECHO)
   (disable-obj-bitfield-option! ed-mode-termios -c_lflag LibC/ECHOE)
  ;;  (disable-obj-bitfield-option! ed-mode-termios -c_lflag LibC/ISIG)

   cc (.-c_cc ed-mode-termios)
   (aset cc LibC/VTIME (byte 0))
   (aset cc LibC/VMIN (byte 1))

   (LibC/tcsetattr stdin-fd LibC/TCSANOW ed-mode-termios)))

(defn restore-termios! []
  (LibC/tcsetattr stdin-fd LibC/TCSANOW @*initial-termios))

(defn setup!
  "Use this to set up the terminal before running your program."
  []
  (setup-termios!)
  (update-terminal-size!)
  (Signal/handle (new Signal "WINCH") (reify SignalHandler
                                        (handle [_this _sig]
                                          (update-terminal-size!))))
  (alternate-screen!)
  (disable-line-wrap!)
  (begin-kitty-keyboard-protocol! out!)
  (clear!)
  (println "terminal size: " @*term-dim)
  (println @*initial-termios)
  ;;(println "This is the alternate screen!")
  (flush!)
  nil)

(defn teardown!
  "Use this to clean up the terminal before exiting."
  []
  (end-kitty-keyboard-protocol! out!)
  (enable-line-wrap!)
  (reset-color-output!)
  (normal-screen!)
  (restore-termios!)
  (flush!)
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce request-stop-rendering-ch (chan 1))
(defonce stop-rendering-done-ch (chan 1))

(defn render-loop! []
  (let [*last-x (atom -1)
        *last-y (atom -1)]
    (go
      (loop [x 0
             y 0]
        (let [[_v ch] (alts! [(async/timeout 1) request-stop-rendering-ch])]
          (if (= ch request-stop-rendering-ch)
            (close! stop-rendering-done-ch)
            (let [{:keys [rows cols]} @*term-dim
                  new-x (inc x)
                  new-y (if (>= new-x cols) (inc y) y)
                  new-x (mod new-x cols)
                  new-y (mod new-y rows)]
              (when-not (and (= new-y @*last-y))
                (move-cursor! new-x new-y)
                (reset! *last-x new-x)
                (reset! *last-y new-y))
              (set-bg-color! (rand-int 256) (rand-int 256) (rand-int 256))
              (set-fg-color! (rand-int 256) (rand-int 256) (rand-int 256))
              (out! (new String (int-array [(rand-int-between 0x20 0x7e)]) 0 1))
              (recur new-x new-y))))))))


(defn die-properly! []
  (go
    (>! request-stop-rendering-ch true)
    (alts! [(async/timeout 1) stop-rendering-done-ch])
    (teardown!)
    (System/exit 0)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn handle-key-event! [[[mods k] t]]
  (cond
    (and (mods :ctrl) (= k "c"))
    (die-properly!)
    :else
    (out! (str "key: " k " mods: " mods " t: " t  "\n"))))


(defn stdin-read-loop! []
  (let [ch (chan (buffer 16))]
    (go
      (loop []
        ;; read a byte as an int
        (let [x (.read *in*)]
          ;; (out! (format "x: 0x%02x\n" x))
          (if (= -1 x)
            (close! ch)
            (do
              (>! ch [x (get-timestamp)])
              (recur))))))

    (go
      (loop [x (<! ch)]
        (when x
          (let [[b t] x]
            (case b
              0x1b (when-let [key-event (kitty/read-escape-code! ch @*esc-timeout-ms x)]
                     (handle-key-event! key-event))
              0x09 (handle-key-event! [[#{} "tab"] t])
              (0x08 0x7f) (handle-key-event! [[#{} "backspace"] t])
              0x0d (handle-key-event! [[#{} "enter"] t])
              (handle-key-event! (channel+first-byte->key-event ch x)))
            (recur (<! ch))))))))
