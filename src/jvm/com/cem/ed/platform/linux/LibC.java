package com.cem.ed.platform.linux;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.Structure;

public class LibC {
  static {
    Native.register("c");
  }
  public static native int open(String path, int flags);
  
  public static class Winsize extends Structure {
    public short ws_row;
    public short ws_col;
    public short ws_xpixel; /* unused */
    public short ws_ypixel; /* unused */
    
    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("ws_row", "ws_col", "ws_xpixel", "ws_ypixel");
    }
  }
  public static native int ioctl(int fd, int request, Structure args);
}
