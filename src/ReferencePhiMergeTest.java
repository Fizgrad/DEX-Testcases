// ReferencePhiMergeTest.java
// Stresses null merges + subtype merges in control-flow.
 

public final class ReferencePhiMergeTest {
  private static final TestSupport.Counter CTR = new TestSupport.Counter();

  private static final int DEFAULT_ITERS = 10000;
  private static final int DEFAULT_ROUNDS = 5;

  private ReferencePhiMergeTest() {}

  public static void main(String[] args) {
    CTR.reset();
    System.out.println("=== ReferencePhiMergeTest starting ===");
    int iters = DEFAULT_ITERS;
    int rounds = DEFAULT_ROUNDS;

    for (String s : args) {
      if (s.startsWith("--iters=")) {
        iters = parseInt(s.substring(s.indexOf('=') + 1), iters);
      } else if (s.startsWith("--rounds=")) {
        rounds = parseInt(s.substring(s.indexOf('=') + 1), rounds);
      } else if ("--short".equals(s)) {
        iters = 2000;
        rounds = 2;
      } else if ("--full".equals(s)) {
        iters = 40000;
        rounds = 8;
      }
    }

    if (iters < 1)
      iters = 1;
    if (rounds < 1)
      rounds = 1;

    int expectedNull = phiNullMergeRef(iters);
    int expectedSubtype = phiSubtypeMergeRef(iters);
    int expectedArray = phiArrayMergeRef(iters);

    JitSupport.requestJitCompilation(ReferencePhiMergeTest.class);
    warmUp(Math.max(1, iters / 4));

    for (int r = 0; r < rounds; r++) {
      int gotNull = phiNullMergeHot(iters);
      int gotSubtype = phiSubtypeMergeHot(iters);
      int gotArray = phiArrayMergeHot(iters);
      TestSupport.checkEq("phi.null.r" + r, gotNull, expectedNull, CTR);
      TestSupport.checkEq("phi.subtype.r" + r, gotSubtype, expectedSubtype, CTR);
      TestSupport.checkEq("phi.array.r" + r, gotArray, expectedArray, CTR);
    }

    TestSupport.summary("ReferencePhiMergeTest", CTR);
    if (CTR.getFail() != 0)
      System.exit(1);
  }

  private static void warmUp(int iters) {
    phiNullMergeHot(iters);
    phiSubtypeMergeHot(iters);
    phiArrayMergeHot(iters);
  }

  private static int phiNullMergeHot(int iters) {
    Object v = null;
    Object alt = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      if ((i & 1) == 0) {
        v = "x" + i;
      } else {
        v = null;
      }
      if ((i & 2) != 0) {
        alt = new StringBuilder("b" + i);
      } else {
        alt = null;
      }
      Object merged = ((i & 4) == 0) ? v : alt;

      if (merged instanceof String) {
        String s = (String)merged;
        sum += s.length();
        sum ^= s.hashCode();
      } else if (merged instanceof StringBuilder) {
        StringBuilder sb = (StringBuilder)merged;
        sb.append('x');
        sum += sb.length();
        sum ^= sb.charAt(0);
      }
      if ((i & 7) == 0)
        sum ^= touchNullMerge(merged, alt, i);
    }
    sum ^= finalizeNullMerge(v, alt);
    return sum;
  }

  private static int phiNullMergeRef(int iters) {
    Object v = null;
    Object alt = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      int sel = i & 7;
      switch (sel) {
      case 0:
      case 2:
      case 4:
      case 6:
        v = "x" + i;
        break;
      default:
        v = null;
        break;
      }
      switch (sel) {
      case 2:
      case 3:
      case 6:
      case 7:
        alt = new StringBuilder("b" + i);
        break;
      default:
        alt = null;
        break;
      }
      Object merged = ((sel & 4) == 0) ? v : alt;

      if (merged instanceof String) {
        String s = (String)merged;
        sum += s.length();
        sum ^= s.hashCode();
      } else if (merged instanceof StringBuilder) {
        StringBuilder sb = (StringBuilder)merged;
        sb.append('x');
        sum += sb.length();
        sum ^= sb.charAt(0);
      }
      if ((i & 7) == 0)
        sum ^= touchNullMerge(merged, alt, i);
    }
    sum ^= finalizeNullMerge(v, alt);
    return sum;
  }

  private static int phiSubtypeMergeHot(int iters) {
    CharSequence cs = null;
    Object obj = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      int mod = i & 3;
      if (mod == 0) {
        cs = new StringBuilder("sb" + i);
      } else if (mod == 1) {
        cs = new StringBuffer("buf" + i);
      } else if (mod == 2) {
        cs = new String("str" + i);
      } else {
        cs = null;
      }

      if ((i & 1) == 0) {
        obj = cs;
      } else {
        obj = (cs == null) ? new StringBuilder("alt" + i) : cs.toString();
      }

      if (cs != null) {
        sum += cs.length();
        sum ^= cs.charAt(0);
      }

      if (obj instanceof StringBuilder) {
        StringBuilder sb = (StringBuilder)obj;
        sb.append('x');
        sum += sb.length();
      } else if (obj instanceof StringBuffer) {
        StringBuffer sb = (StringBuffer)obj;
        sb.append('y');
        sum += sb.length();
      } else if (obj instanceof String) {
        String s = (String)obj;
        sum += s.length();
        sum ^= s.charAt(0);
      }

      if ((i & 7) == 0)
        sum ^= touchSubtype(cs, obj, i);
    }
    if (cs != null)
      sum += cs.length();
    else
      sum ^= 0x33;
    return sum;
  }

  private static int phiSubtypeMergeRef(int iters) {
    CharSequence cs = null;
    Object obj = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      switch (i & 3) {
      case 0:
        cs = new StringBuilder("sb" + i);
        break;
      case 1:
        cs = new StringBuffer("buf" + i);
        break;
      case 2:
        cs = new String("str" + i);
        break;
      default:
        cs = null;
        break;
      }

      switch (i & 1) {
      case 0:
        obj = cs;
        break;
      default:
        obj = (cs == null) ? new StringBuilder("alt" + i) : cs.toString();
        break;
      }

      if (cs != null) {
        sum += cs.length();
        sum ^= cs.charAt(0);
      }

      if (obj instanceof StringBuilder) {
        StringBuilder sb = (StringBuilder)obj;
        sb.append('x');
        sum += sb.length();
      } else if (obj instanceof StringBuffer) {
        StringBuffer sb = (StringBuffer)obj;
        sb.append('y');
        sum += sb.length();
      } else if (obj instanceof String) {
        String s = (String)obj;
        sum += s.length();
        sum ^= s.charAt(0);
      }

      if ((i & 7) == 0)
        sum ^= touchSubtype(cs, obj, i);
    }
    if (cs != null)
      sum += cs.length();
    else
      sum ^= 0x33;
    return sum;
  }

  private static int phiArrayMergeHot(int iters) {
    Object arr = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      int mod = i & 3;
      if (mod == 0) {
        arr = new String[] {"s" + i, "t" + i};
      } else if (mod == 1) {
        arr = new Object[] {Integer.valueOf(i), "x" + i};
      } else if (mod == 2) {
        arr = new CharSequence[] {new StringBuilder("q" + i), "r" + i};
      } else {
        arr = null;
      }

      if (arr != null && (i & 1) == 0) {
        Object[] oa = (Object[])arr;
        sum += oa.length;
        Object first = oa[0];
        if (first instanceof String) {
          sum += ((String)first).length();
        } else if (first instanceof Integer) {
          sum += ((Integer)first).intValue();
        } else if (first instanceof CharSequence) {
          sum += ((CharSequence)first).length();
        }
      }

      if (arr instanceof String[]) {
        String[] sa = (String[])arr;
        sum ^= sa[0].length();
      } else if (arr instanceof CharSequence[]) {
        CharSequence[] ca = (CharSequence[])arr;
        sum ^= ca[0].length();
      } else if (arr instanceof Object[]) {
        sum ^= ((Object[])arr).length;
      }

      if ((i & 7) == 0)
        sum ^= touchArray(arr, i);
    }
    if (arr != null)
      sum ^= ((Object[])arr).length;
    else
      sum ^= 0x1f1f;
    return sum;
  }

  private static int phiArrayMergeRef(int iters) {
    Object arr = null;
    int sum = 0;
    for (int i = 0; i < iters; i++) {
      switch (i & 3) {
      case 0:
        arr = new String[] {"s" + i, "t" + i};
        break;
      case 1:
        arr = new Object[] {Integer.valueOf(i), "x" + i};
        break;
      case 2:
        arr = new CharSequence[] {new StringBuilder("q" + i), "r" + i};
        break;
      default:
        arr = null;
        break;
      }

      if (arr != null && (i & 1) == 0) {
        Object[] oa = (Object[])arr;
        sum += oa.length;
        Object first = oa[0];
        if (first instanceof String) {
          sum += ((String)first).length();
        } else if (first instanceof Integer) {
          sum += ((Integer)first).intValue();
        } else if (first instanceof CharSequence) {
          sum += ((CharSequence)first).length();
        }
      }

      if (arr instanceof String[]) {
        String[] sa = (String[])arr;
        sum ^= sa[0].length();
      } else if (arr instanceof CharSequence[]) {
        CharSequence[] ca = (CharSequence[])arr;
        sum ^= ca[0].length();
      } else if (arr instanceof Object[]) {
        sum ^= ((Object[])arr).length;
      }

      if ((i & 7) == 0)
        sum ^= touchArray(arr, i);
    }
    if (arr != null)
      sum ^= ((Object[])arr).length;
    else
      sum ^= 0x1f1f;
    return sum;
  }

  private static int touchNullMerge(Object merged, Object alt, int iter) {
    int acc = 0;
    try {
      acc ^= stableTag(merged, 7);
      acc ^= stableTag(alt, 0x11) << 1;
    } finally {
      acc ^= iter * 31;
    }
    return acc;
  }

  private static int finalizeNullMerge(Object v, Object alt) {
    int acc = 0;
    if (v instanceof String)
      acc ^= ((String)v).length();
    if (alt instanceof StringBuilder)
      acc ^= ((StringBuilder)alt).length();
    if (v == null && alt == null)
      acc ^= 0x5a5a;
    return acc;
  }

  private static int touchSubtype(CharSequence cs, Object obj, int iter) {
    int acc = 0;
    try {
      acc ^= stableCharSeq(cs, 0x13);
      acc ^= stableTag(obj, 0x17);
      if (obj instanceof CharSequence)
        acc ^= stableCharSeq((CharSequence)obj, 0x19) << 1;
    } finally {
      acc ^= iter * 17;
    }
    return acc;
  }

  private static int touchArray(Object arr, int iter) {
    int acc = 0;
    try {
      if (arr instanceof Object[]) {
        Object[] oa = (Object[])arr;
        acc ^= oa.length;
        Object tail = oa[oa.length - 1];
        if (tail != null)
          acc ^= tail.hashCode();
      }
    } finally {
      acc ^= iter * 19;
    }
    return acc;
  }

  private static int stableTag(Object obj, int seed) {
    if (obj == null)
      return seed;
    if (obj instanceof CharSequence)
      return stableCharSeq((CharSequence)obj, seed);
    return seed ^ obj.getClass().getName().length();
  }

  private static int stableCharSeq(CharSequence cs, int seed) {
    if (cs == null)
      return seed ^ 0x5b;
    int len = cs.length();
    int first = (len == 0) ? 0 : cs.charAt(0);
    int last = (len == 0) ? 0 : cs.charAt(len - 1);
    return seed ^ (len << 1) ^ (first << 2) ^ last;
  }

  private static int parseInt(String raw, int fallback) {
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return fallback;
    }
  }
}
