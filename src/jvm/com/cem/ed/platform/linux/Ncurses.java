package com.cem.ed.platform.linux;

import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class Ncurses {
  static {
    Native.register("ncursesw");
  }
  public static native int setupterm(String term, int fd, Pointer errret);
  public static native Pointer tigetstr(String name);
}
