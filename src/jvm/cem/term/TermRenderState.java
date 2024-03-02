package cem.term;

import java.lang.StringBuilder;

public final class TermRenderState {
  public int lastCol;
  public int lastRow;

  public byte lastDim;
  public byte lastBold;
  public byte lastBlink;

  public byte lastItalic;

  public byte lastUnderline;

  public int lastFgColor;
  public int lastBgColor;

  public StringBuilder sb;

  public void reset() {
    lastCol = Integer.MIN_VALUE;
    lastRow = Integer.MIN_VALUE;
    lastDim = -1;
    lastBold = -1;
    lastBlink = -1;
    lastItalic = -1;
    lastUnderline = -1;
    // surely no one would use these colors with 0 alpha
    lastFgColor = 0x11223300;
    lastBgColor = 0x11223300;

    if (sb == null) {
      sb = new StringBuilder();
    } else {
      sb.setLength(0);
    }
  }

  public TermRenderState() {
    reset();
  }
}
