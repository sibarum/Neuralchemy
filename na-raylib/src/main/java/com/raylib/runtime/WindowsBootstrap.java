package com.raylib.runtime;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Shipping bootstrap for {@code /SUBSYSTEM:WINDOWS} executables: tees stdout/stderr to a log
 * file, inherits the parent terminal's console when launched from one, and pops a Win32
 * MessageBox on uncaught exceptions. Call {@link #init(String)} as the first line of main().
 *
 * <p>Each step is independently best-effort — any failure is swallowed so that a working app
 * never gets worse because the bootstrap had a problem. On non-Windows the whole thing is a
 * no-op.
 *
 * <p>Why each piece:
 * <ul>
 *   <li><b>File log</b> (always on) — without a console, {@code System.out.println} and
 *       uncaught-exception traces would otherwise vanish. Writes to
 *       {@code %LOCALAPPDATA%\<appName>\<appName>.log}, append mode.</li>
 *   <li><b>AttachConsole(ATTACH_PARENT_PROCESS)</b> — when launched from cmd/PowerShell,
 *       inherits the parent console so power users still see logs in-place. Silently fails
 *       (returns 0) when launched from Explorer, leaving the clean GUI experience intact.</li>
 *   <li><b>MessageBox on uncaught exception</b> — fatal exceptions otherwise die silently.
 *       Pops a MB_ICONERROR dialog with the exception summary and a pointer to the log path.</li>
 * </ul>
 *
 * <p>The two {@code *_DESC} constants are pre-registered for native-image by RaylibFeature;
 * if you add more Win32 calls here, register their descriptors there too.
 */
public final class WindowsBootstrap {

    public static final FunctionDescriptor ATTACH_CONSOLE_DESC =
            FunctionDescriptor.of(JAVA_INT, JAVA_INT);
    public static final FunctionDescriptor MESSAGE_BOX_A_DESC =
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT);

    private static final int ATTACH_PARENT_PROCESS = -1;
    private static final int MB_ICONERROR = 0x10;

    private WindowsBootstrap() {}

    public static void init(String appName) {
        if (!isWindows()) return;
        attachParentConsole();
        Path logPath = redirectStdoutErrToFile(appName);
        installCrashMessageBox(appName, logPath);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static void attachParentConsole() {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup k32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            MemorySegment fn = k32.find("AttachConsole").orElseThrow();
            MethodHandle h = linker.downcallHandle(fn, ATTACH_CONSOLE_DESC);
            int unused = (int) h.invokeExact(ATTACH_PARENT_PROCESS);
        } catch (Throwable ignored) {
            // No parent console (Explorer launch) or kernel32 not resolvable — fine.
        }
    }

    private static Path redirectStdoutErrToFile(String appName) {
        try {
            String localApp = System.getenv("LOCALAPPDATA");
            if (localApp == null || localApp.isEmpty()) return null;
            Path dir = Path.of(localApp, appName);
            Files.createDirectories(dir);
            Path log = dir.resolve(appName + ".log");
            OutputStream out = Files.newOutputStream(log,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            PrintStream ps = new PrintStream(out, true);
            System.setOut(ps);
            System.setErr(ps);
            ps.println("---- " + Instant.now() + " " + appName + " starting ----");
            return log;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void installCrashMessageBox(String appName, Path logPath) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { e.printStackTrace(); } catch (Throwable ignored) {}
            try {
                StringBuilder msg = new StringBuilder();
                msg.append(e.getClass().getSimpleName());
                if (e.getMessage() != null) msg.append(": ").append(e.getMessage());
                if (logPath != null) msg.append("\n\nFull log: ").append(logPath);
                showMessageBox(appName + " — fatal error", msg.toString());
            } catch (Throwable ignored) {}
        });
    }

    private static void showMessageBox(String title, String text) throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup u32 = SymbolLookup.libraryLookup("user32", Arena.global());
        MemorySegment fn = u32.find("MessageBoxA").orElseThrow();
        MethodHandle h = linker.downcallHandle(fn, MESSAGE_BOX_A_DESC);
        try (Arena tmp = Arena.ofConfined()) {
            int unused = (int) h.invokeExact(MemorySegment.NULL,
                    tmp.allocateFrom(text),
                    tmp.allocateFrom(title),
                    MB_ICONERROR);
        }
    }
}
