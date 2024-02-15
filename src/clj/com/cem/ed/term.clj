(ns com.cem.ed.term
  (:refer-clojure :exclude [*in* *out* *err*])
  (:require [clojure.core.async :as async :refer [<! <!! >! alts!! buffer chan
                                                  close! go timeout]]
            [com.cem.ed.macros :refer [->hash bb disable-obj-bitfield-option!]]
            [com.cem.ed.utf8 :refer [channel+first-byte->key-event]]
            [com.cem.ed.utils :refer [get-timestamp]]
            [com.cem.ed.term.kitty :as kitty])
  (:import [com.cem.ed.platform.linux
            LibC
            LibC$Termios
            LibC$Winsize
            Ncurses]
           [com.sun.jna Pointer]
           [java.io FileDescriptor FileInputStream FileOutputStream]
           [java.lang System]
           [java.util ArrayList]
           [sun.misc Signal SignalHandler]))

(def stdin-fd 0)
(def stdout-fd 1)
(def stderr-fd 2)
(def ^:dynamic *in* (new FileInputStream (FileDescriptor/in)))
(def ^:dynamic *out* (new FileOutputStream (FileDescriptor/out)))
(def ^:dynamic *err* (new FileOutputStream (FileDescriptor/err)))
(def *term-dim (atom {:rows nil :cols nil}))
(defonce *initial-termios (atom nil))

(def csi "Control Sequence Introducer" "\u001B[")
(def kitty-keyboard-protocol-begin-code "Kitty Keyboard Protocol Begin" (str csi ">1u"))
(def kitty-keyboard-protocol-end-code "Kitty Keyboard Protocol End" (str csi "<u"))

(defonce *string-caps (atom {}))
(defonce *esc-timeout-ms (atom 250))

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

(defn init! []
  ;; TODO: does this need to be more specific?
  (bb
   (Ncurses/setupterm nil stdout-fd nil)
   (load-string-cap! "clear")
   (load-string-cap! "rmcup")
   (load-string-cap! "smcup")
   nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn out! [^String s]
  (bb
   bites (.getBytes s "UTF-8")
   (.write *out* bites)))

(defn flush! [] (.flush *out*))
(defn clear! [] (out! (get @*string-caps "clear")))
(defn alternate-screen! [] (out! (get @*string-caps "smcup")))
(defn normal-screen! [] (out! (get @*string-caps "rmcup")))

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

(defn begin-kitty-keyboard-protocol! [] (out! kitty-keyboard-protocol-begin-code))
(defn end-kitty-keyboard-protocol! [] (out! kitty-keyboard-protocol-end-code))

(defn setup
  "Use this to set up the terminal before running your program."
  []
  (setup-termios!)
  (update-terminal-size!)
  (Signal/handle (new Signal "WINCH") (reify SignalHandler
                                        (handle [_this _sig]
                                          (update-terminal-size!))))
  (alternate-screen!)
  (begin-kitty-keyboard-protocol!)
  (clear!)
  (println "terminal size: " @*term-dim)
  (println @*initial-termios)
  ;;(println "This is the alternate screen!")
  (flush!)
  nil)

(defn teardown!
  "Use this to clean up the terminal before exiting."
  []
  (end-kitty-keyboard-protocol!)
  (normal-screen!)
  (restore-termios!)
  (flush!)
  nil)

(defn die-properly! []
  (teardown!)
  (System/exit 0))

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
