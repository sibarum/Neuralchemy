package com.raylib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RaylibLoader {

    private static final AtomicBoolean LOADED = new AtomicBoolean();

    private RaylibLoader() {
    }

    /**
     * Extracts the bundled raylib native library to a temp dir and loads it. Must be called once
     * before any class in {@code com.raylib.bindings} is touched: the generated bindings resolve
     * symbols against {@code SymbolLookup.loaderLookup().or(defaultLookup())}, so the DLL has to
     * already be in the process by the time the first holder class inits. Calling this any time
     * after that throws {@code UnsatisfiedLinkError} from inside the binding's static block.
     */
    public static void load() {
        if (!LOADED.compareAndSet(false, true)) return;
        String libName = System.mapLibraryName("raylib");
        String resource = "/native/" + platformDir() + "/" + libName;
        try (InputStream in = RaylibLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Bundled native library not found on classpath: " + resource);
            }
            Path tmpDir = Files.createTempDirectory("na-raylib-");
            tmpDir.toFile().deleteOnExit();
            Path target = tmpDir.resolve(libName);
            Files.copy(in, target);
            target.toFile().deleteOnExit();
            System.load(target.toAbsolutePath().toString());
        } catch (IOException e) {
            UnsatisfiedLinkError err = new UnsatisfiedLinkError("Failed to extract bundled raylib: " + e.getMessage());
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
