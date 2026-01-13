// JitSupport.java
// Best-effort helpers to encourage JIT compilation on ART.
 

import java.lang.reflect.Method;

public final class JitSupport {
  private JitSupport() {}

  public static void requestJitCompilation(Class<?> cls) {
    try {
      Class<?> vmRuntime = Class.forName("dalvik.system.VMRuntime");
      Method getRuntime = vmRuntime.getDeclaredMethod("getRuntime");
      Method startJit = vmRuntime.getDeclaredMethod("startJitCompilation");
      getRuntime.setAccessible(true);
      startJit.setAccessible(true);
      Object rt = getRuntime.invoke(null);
      startJit.invoke(rt);
    } catch (Throwable ignored) {
      // ART-only; ignore on other VMs.
    }

    if (cls == null)
      return;
    try {
      Class<?> compiler = Class.forName("java.lang.Compiler");
      Method compileClass = compiler.getDeclaredMethod("compileClass",
                                                       Class.class);
      compileClass.setAccessible(true);
      compileClass.invoke(null, cls);
    } catch (Throwable ignored) {
      // java.lang.Compiler may be absent or disabled.
    }
  }
}
