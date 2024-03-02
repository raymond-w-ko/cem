package cem.term;

import java.util.Arrays;

public final class Rect {
  public final class Cell {
    public static final int FLAG_BOLD = 1 << 0;
    public static final int FLAG_ITALIC = 1 << 1;
    public static final int FLAG_UNDERLINE = 1 << 2;
    public static final int FLAG_DIM = 1 << 3;
    public static final int FLAG_BLINK = 1 << 4;

    public int codePoint;
    public int fgColor;
    public short fg_r, fg_g, fg_b, fg_a;
    public int bgColor;
    public short bg_r, bg_g, bg_b, bg_a;
    public byte bold, italic, underline, dim, blink;
    public Cell() {
      // TODO: is this necessary?
    }
  }

  public static final short selectUnsignedByteOfInt(int i, int n) {
    return (short)((i >> (n * 8)) & 0xFF);
  }

  public static final void bytesToCell(int[] cellBytes, Cell cell) {
    cell.codePoint = cellBytes[0];
    final int fgColor = cellBytes[1];
    cell.fgColor = fgColor;
    cell.fg_r = selectUnsignedByteOfInt(fgColor, 3);
    cell.fg_g = selectUnsignedByteOfInt(fgColor, 2);
    cell.fg_b = selectUnsignedByteOfInt(fgColor, 1);
    cell.fg_a = selectUnsignedByteOfInt(fgColor, 0);
    final int bgColor = cellBytes[2];
    cell.bgColor = bgColor;
    cell.bg_r = selectUnsignedByteOfInt(bgColor, 3);
    cell.bg_g = selectUnsignedByteOfInt(bgColor, 2);
    cell.bg_b = selectUnsignedByteOfInt(bgColor, 1);
    cell.bg_a = selectUnsignedByteOfInt(bgColor, 0);
    final int flags = cellBytes[3];
    cell.bold = (byte) (flags & Cell.FLAG_BOLD);
    cell.italic = (byte) (flags & Cell.FLAG_ITALIC);
    cell.underline = (byte) (flags & Cell.FLAG_UNDERLINE);
    cell.dim = (byte) (flags & Cell.FLAG_DIM);
    cell.blink = (byte) (flags & Cell.FLAG_BLINK);
  }

  public int numRows, numCols, numCells;
  public int[] data;

  private int[] _tmpCellBytes;
  private Cell _tmpCell;

  public static final int getCellSizeAsNumInts() {
    return 4;
  }

  public Rect() {
    numRows = -1;
    numCols = -1;
    data = null;
    _tmpCellBytes = new int[getCellSizeAsNumInts()];
    _tmpCell = new Cell();
  }

  public final int rowColToDataIndex(final int row, final int col) {
    assert row >= 0;
    assert col >= 0;
    assert row < this.numRows;
    assert col < this.numCols;
    return (row * numCols + col) * getCellSizeAsNumInts();
  }

  public final void resize(final int _rows, final int _cols) {
    assert _rows > 0;
    assert _cols > 0;
    if (this.numRows == _rows && this.numCols == _cols) {
      return;
    }
    this.numRows = _rows;
    this.numCols = _cols;
    this.numCells = this.numRows * this.numCols;
    final int n = this.numRows * this.numCols * getCellSizeAsNumInts();
    data = new int[n];
    Arrays.fill(data, -1);
  }

  public final Cell getCellIfDifferent(final int row, final int col, Rect other) {
    final int i = rowColToDataIndex(row, col);
    // a copy is necessary because this may be used in a multi-threaded context
    this._tmpCellBytes[0] = this.data[i];
    this._tmpCellBytes[1] = this.data[i + 1];
    this._tmpCellBytes[2] = this.data[i + 2];
    this._tmpCellBytes[3] = this.data[i + 3];

    if (this._tmpCellBytes[0] == other.data[i] &&
        this._tmpCellBytes[1] == other.data[i + 1] &&
        this._tmpCellBytes[2] == other.data[i + 2] &&
        this._tmpCellBytes[3] == other.data[i + 3]) {
      return null;
    }

    bytesToCell(this._tmpCellBytes, this._tmpCell);
    return this._tmpCell;
  }

  public final void updateBackingStore(final int row, final int col, Rect store) {
    final int i = rowColToDataIndex(row, col);
    store.data[i] = this._tmpCellBytes[0];
    store.data[i + 1] = this._tmpCellBytes[1];
    store.data[i + 2] = this._tmpCellBytes[2];
    store.data[i + 3] = this._tmpCellBytes[3];
  }
}
