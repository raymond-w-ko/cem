package cem.term;

public final class CodePointStringCache {
  protected String[] cache;
  public static final int MAX_CODE_POINT = 0x10FFFF;
  public CodePointStringCache() {
    cache = new String[MAX_CODE_POINT + 1];
    for (int i = 0; i <= MAX_CODE_POINT; i++) {
      final String x = new String(new int[] {i}, 0, 1);
      final String s = x.intern();
      cache[i] = s;
    }
  }
  public String codePointToString(int codePoint) {
    return cache[codePoint];
  }
}
