(ns cem.term
  (:refer-clojure :exclude [*in* *out* *err*])
  (:require [cem.macros :refer [->hash bb disable-obj-bitfield-option!]]
            [cem.term.constants :refer [*in* flush-stdout! out! stdin-fd]]
            [cem.term.kitty :as kitty :refer [kitty-keyboard-protocol-begin-code
                                              kitty-keyboard-protocol-end-code]]
            [cem.term.ops :refer [alternate-screen clear disable-line-wrap
                                  enable-line-wrap normal-screen
                                  reset-color-output]]
            [cem.term.state :as state :refer [*esc-timeout-ms *initial-termios
                                              *term-dim]]
            [cem.utf8 :refer [channel+first-byte->key-event]]
            [cem.utils :refer [get-timestamp]]
            [cem.term.renderer]
            [clojure.core.async :as async :refer [<! >! buffer chan close! go]])
  (:import [cem.platform.linux LibC LibC$Termios LibC$Winsize]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn setup-termios!
  "This mutates terminal state and requires a teardown."
  []
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

(defn teardown!
  "Use this to clean up the terminal before exiting."
  []
  (out! kitty-keyboard-protocol-end-code)
  (out! (enable-line-wrap))
  (out! (reset-color-output))
  (out! (normal-screen))
  (restore-termios!)
  (flush-stdout!)
  :teardown-done)


(defn setup!
  "Use this to set up the terminal before running your program."
  []
  (setup-termios!)
  ;; TODO: is it safe if we get interrupted before setup is done?
  (Signal/handle (new Signal "INT") (reify SignalHandler
                                      (handle [_this _sig]
                                        (teardown!)
                                        (System/exit 0))))
  (update-terminal-size!)
  (let [{:keys [rows cols]} @*term-dim]
    (.resize state/root-rect rows cols))
  (Signal/handle (new Signal "WINCH") (reify SignalHandler
                                        (handle [_this _sig]
                                          (update-terminal-size!))))
  (out! (alternate-screen))
  (out! (disable-line-wrap))
  (out! kitty-keyboard-protocol-begin-code)
  (out! (clear))
  (flush-stdout!)
  :setup-done)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn die-properly! []
  (go
    ;; (>! request-stop-rendering-ch true)
    ;; (alts! [(async/timeout 1) stop-rendering-done-ch])
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
