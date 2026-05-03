package com.raygui;

import com.raylib.RaylibLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RayguiLoader {

    private static final AtomicBoolean LOADED = new AtomicBoolean();

    private RayguiLoader() {}

    /**
     * Loads raygui.dll into the process. Must be called before any class in
     * {@code com.raygui.bindings} is touched, for the same reason as
     * {@link RaylibLoader#load()}: the bindings resolve symbols against
     * {@code loaderLookup().or(defaultLookup())}, which requires the DLL to already be in
     * the process.
     *
     * <p>raygui.dll has an unresolved import on raylib.dll, so we call
     * {@link RaylibLoader#load()} first — that puts raylib.dll in the process module table,
     * and Windows's loader will satisfy raygui's import against the already-loaded module
     * (matching by basename, not path) when {@code System.load} runs the binding step.
     */
    public static void load() {
        if (!LOADED.compareAndSet(false, true)) return;
        RaylibLoader.load();
        String libName = System.mapLibraryName("raygui");
        String resource = "/native/" + platformDir() + "/" + libName;
        try (InputStream in = RayguiLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Bundled native library not found on classpath: " + resource);
            }
            Path tmpDir = Files.createTempDirectory("na-raygui-");
            tmpDir.toFile().deleteOnExit();
            Path target = tmpDir.resolve(libName);
            Files.copy(in, target);
            target.toFile().deleteOnExit();
            System.load(target.toAbsolutePath().toString());
        } catch (IOException e) {
            UnsatisfiedLinkError err = new UnsatisfiedLinkError("Failed to extract bundled raygui: " + e.getMessage());
            err.initCause(e);
            throw err;
        }
    }

    private static String platformDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osDir = os.contains("win") ? "win32"
                : os.contains("mac") || os.contains("darwin") ? "darwin"
                : "linux";
        String archDir = switch (arch) {
            case "amd64", "x86_64" -> "x86-64";
            case "aarch64", "arm64" -> "aarch64";
            default -> arch;
        };
        return osDir + "-" + archDir;
    }
}
