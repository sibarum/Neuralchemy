package com.raylib.svm;

import com.raylib.RaylibLoader;
import com.raylib.bindings.Raylib;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;

import java.lang.foreign.FunctionDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Pre-registers every jextract-emitted downcall stub at image-build time so the ABI marshalling
 * trampolines are AOT-compiled into the image. At runtime the bindings still resolve symbol
 * addresses lazily (those can't be hoisted — they're load-address-dependent), but
 * {@code Linker.nativeLinker().downcallHandle(addr, desc)} just looks up a precompiled stub
 * instead of generating one on the fly.
 *
 * <p>jextract emits one nested holder class per function with a {@code public static final
 * FunctionDescriptor DESC}. We reflect over those, read the descriptors, and feed them to
 * {@link RuntimeForeignAccess#registerForDowncall}.
 *
 * <p>Reading {@code DESC} triggers the holder's static init, which in turn calls
 * {@code SYMBOL_LOOKUP.find(name)} — and our symbol lookup is
 * {@code loaderLookup().or(defaultLookup())}, which only sees raylib if it's already loaded
 * into <em>this</em> (build-host) JVM. So we call {@link RaylibLoader#load()} first, which
 * extracts the bundled DLL to a temp dir on the build host and {@code System.load}s it.
 *
 * <p>Marked with {@code provided}-scope svm dependency: the class compiles fine in regular
 * builds but is only ever instantiated by the {@code native-image} build process.
 */
public final class RaylibFeature implements Feature {

    @Override
    public String getDescription() {
        return "Pre-registers raylib downcall stubs for native-image";
    }

    /**
     * Registration must happen here, not in {@link #beforeAnalysis}: the foreign-function
     * registry seals between {@code duringSetup} and {@code beforeAnalysis}, and any later
     * {@code registerForDowncall} call throws {@code "Registration of foreign functions was closed"}.
     */
    @Override
    public void duringSetup(DuringSetupAccess access) {
        RaylibLoader.load();

        int registered = 0;
        boolean foundInitWindow = false;
        for (Class<?> nested : Raylib.class.getDeclaredClasses()) {
            FunctionDescriptor desc = readDesc(nested);
            if (desc == null) continue;
            RuntimeForeignAccess.registerForDowncall(desc);
            if (nested.getSimpleName().equals("InitWindow")) foundInitWindow = true;
            registered++;
        }
        if (!foundInitWindow) {
            throw new IllegalStateException(
                    "InitWindow holder not found in Raylib.class.getDeclaredClasses() — " +
                    "jextract output shape may have changed.");
        }

        // Hand-written Win32 bootstrap descriptors (file logging + AttachConsole + MessageBox).
        RuntimeForeignAccess.registerForDowncall(com.raylib.runtime.WindowsBootstrap.ATTACH_CONSOLE_DESC);
        RuntimeForeignAccess.registerForDowncall(com.raylib.runtime.WindowsBootstrap.MESSAGE_BOX_A_DESC);
        registered += 2;

        System.out.println("[RaylibFeature] registered " + registered + " downcall stubs");
    }

    private static FunctionDescriptor readDesc(Class<?> holder) {
        Field f;
        try {
            f = holder.getDeclaredField("DESC");
        } catch (NoSuchFieldException e) {
            return null;
        }
        if (f.getType() != FunctionDescriptor.class) return null;
        if (!Modifier.isStatic(f.getModifiers())) return null;
        try {
            f.setAccessible(true);
            return (FunctionDescriptor) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot read DESC from " + holder.getName(), e);
        } catch (ExceptionInInitializerError e) {
            throw new RuntimeException(
                    "Static init of " + holder.getName() + " failed — symbol not found in process. " +
                    "Was RaylibLoader.load() called before reflecting?", e);
        }
    }
}
