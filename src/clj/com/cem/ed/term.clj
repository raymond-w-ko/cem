(ns com.cem.ed.term
  (:require [com.cem.ed.macros :refer [->hash bb]])
  (:import [com.cem.ed.platform.linux LibC LibC$Winsize Ncurses]
           [java.lang System]
           [sun.misc Signal SignalHandler]
           [com.sun.jna NativeLong Pointer]))

(def *term-dim (atom {:rows nil :cols nil}))
(def TIOCGWINSZ (new NativeLong 0x5413))

(defn update-terminal-size! []
  (let [ws (new LibC$Winsize)
        ret (LibC/ioctl 0 TIOCGWINSZ ws)]
    (if (zero? ret)
      (let [rows (.-ws_row ws)
            cols (.-ws_col ws)
            new-dim (->hash rows cols)]
        (println "terminal size: " new-dim)
        (reset! *term-dim new-dim))
      (throw (new Exception "failed to get terminal size with ioctl()")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce *string-caps (atom {}))

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
   (Ncurses/setupterm nil 1 nil)
   (load-string-cap! "clear")
   (load-string-cap! "rmcup")
   (load-string-cap! "smcup"))
  nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def out System/out)

(defn out! [^String s]
  (bb
   bites (.getBytes s "UTF-8")
   (.write out bites)))

(defn flush! [] (.flush out))
(defn clear! [] (out! (get @*string-caps "clear")))
(defn alternate-screen! [] (out! (get @*string-caps "smcup")))
(defn normal-screen! [] (out! (get @*string-caps "rmcup")))

(defn setup
  "Use this to set up the terminal before running your program."
  []
  (alternate-screen!)
  (update-terminal-size!)
  (Signal/handle (new Signal "WINCH") (reify SignalHandler
                                        (handle [_this _sig]
                                          (update-terminal-size!))))
  (clear!)
  (println "This is the alternate screen!")
  (flush!))

(defn teardown!
  "Use this to clean up the terminal before exiting."
  []
  (normal-screen!)
  (flush!))
