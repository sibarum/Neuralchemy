package com.raygui.svm;

import com.raygui.RayguiLoader;
import com.raygui.bindings.Raygui;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;

import java.lang.foreign.FunctionDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Mirrors {@code com.raylib.svm.RaylibFeature}: registers every raygui downcall stub with
 * native-image at build time so the ABI bridges are AOT-compiled.
 *
 * <p>Registration must run during {@code duringSetup} — by {@code beforeAnalysis} the
 * foreign-function registry is sealed and any further {@code registerForDowncall} call
 * throws "Registration of foreign functions was closed".
 */
public final class RayguiFeature implements Feature {

    @Override
    public String getDescription() {
        return "Pre-registers raygui downcall stubs for native-image";
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        RayguiLoader.load();

        int registered = 0;
        for (Class<?> nested : Raygui.class.getDeclaredClasses()) {
            FunctionDescriptor desc = readDesc(nested);
            if (desc == null) continue;
            RuntimeForeignAccess.registerForDowncall(desc);
            registered++;
        }
        System.out.println("[RayguiFeature] registered " + registered + " downcall stubs");
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
                    "Was RayguiLoader.load() called before reflecting?", e);
        }
    }
}
