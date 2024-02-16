package cem.platform.linux;

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

  public static final int TCSANOW = 0;
  public static final int TCSADRAIN = 1;
  public static final int TCSAFLUSH = 2;

  // c_iflag flag constants:
  public static final int IGNBRK = 0x00000001;
  public static final int BRKINT = 0x00000002;
  public static final int IGNPAR = 0x00000004;
  public static final int PARMRK = 0x00000008;
  public static final int INPCK = 0x00000010;
  public static final int ISTRIP = 0x00000020;
  public static final int INLCR = 0x00000040;
  public static final int IGNCR = 0x00000080;
  public static final int ICRNL = 0x00000100;
  public static final int IXON = 0x00000200;
  public static final int IXOFF = 0x00000400;
  public static final int IXANY = 0x00000800;
  public static final int IMAXBEL = 0x00002000;
  public static final int IUTF8 = 0x00004000; // not in POSIX

  // c_oflag flag constants:
  public static final int OPOST = 01;
  public static final int OLCUC = 02; // not in POSIX
  public static final int ONLCR = 04;
  public static final int OCRNL = 010;
  public static final int ONOCR = 020;
  public static final int ONLRET = 040;
  public static final int OFILL = 0100;
  public static final int OFDEL = 0200;

  // c_lflag flag constants:
  public static final int ISIG = 01;
  public static final int ICANON = 02;
  public static final int ECHO = 010;
  public static final int ECHOE = 020;
  public static final int ECHOK = 040;
  public static final int ECHONL = 0100;
  public static final int NOFLSH = 0200;
  public static final int TOSTOP = 0400;

  // c_cflag flag constants:
  public static final int CSIZE = 060;
  public static final int CS5 = 0;
  public static final int CS6 = 020;
  public static final int CS7 = 040;
  public static final int CS8 = 060;
  public static final int CSTOPB = 0100;
  public static final int CREAD = 0200;
  public static final int PARENB = 0400;
  public static final int PARODD = 01000;
  public static final int HUPCL = 02000;
  public static final int CLOCAL = 04000;

  // c_cc character constants:
  public static final int VINTR = 0;
  public static final int VQUIT = 1;
  public static final int VERASE = 2;
  public static final int VKILL = 3;
  public static final int VEOF = 4;
  public static final int VTIME = 5;
  public static final int VMIN = 6;
  public static final int VSWTC = 7;

  public static class Termios extends Structure {
    public int c_iflag;
    public int c_oflag;
    public int c_cflag;
    public int c_lflag;
    public int c_line;
    public byte[] c_cc;
    public int c_ispeed;
    public int c_ospeed;

    public Termios() {
      c_cc = new byte[32];
      allocateMemory();
    }

    @Override
    protected List<String> getFieldOrder() {
      return Arrays.asList("c_iflag", "c_oflag", "c_cflag", "c_lflag", "c_line", "c_cc", "c_ispeed", "c_ospeed");
    }
  }

  public static native int tcgetattr(int fd, Termios termios);
  public static native int tcsetattr(int fd, int optional_actions, Termios termios);
  public static native void cfmakeraw(Termios termios);

  public static final int TIOCGWINSZ = 0x5413;

  public static native int ioctl(int fd, int request, Structure args);
}
