# Windows GLFW runtime

This checkout includes a source-built GLFW 3.4 Windows x64 DLL with a window-ownership guard and LWJGL native extensions. Eclipse/Gradle client launches and `scripts/Invoke-PackagedClient.ps1` select it using LWJGL's `org.lwjgl.glfw.libname` property. The mod jar, world data, Java installation, original Minecraft natives and other projects are unchanged.

GLFW's Windows event loop normally uses the active window's `GLFW` property as a native pointer. Attached input queues can expose a window from another process. That property's pointer belongs to the other address space, so reading its modifier-key array can crash in `glfw.dll`. The guard finds the active HWND in the current GLFW instance's own window list before reading key state. It retains ordinary key-release repair for owned windows.

`runtime/windows-x86_64` contains the DLL, checksum, build provenance, source patches and GLFW license. Gradle launches, IDE preparation and the packaged-client helper verify the checksum. This fix is scoped to Windows x64; other platforms retain their normal GLFW runtime.

For a launcher outside this checkout, add this JVM argument, adjusting the absolute path for your copy:

```text
-Dorg.lwjgl.glfw.libname="D:\MinecraftMineralogy\skysleylines 121NEO\SkysLeyLines\runtime\windows-x86_64\glfw.dll"
```

The ordinary mod jar cannot select a replacement library before the loader's early window initializes, so copying the jar alone does not install the native-runtime fix. Keep this runtime folder and use the JVM argument. No original DLL needs replacing.

To reproduce the build, use `scripts/Build-GlfwRuntime.ps1` with Zig 0.13.0 and the pinned GLFW 3.4 source archive listed in `build-record.json`. The script verifies the archive, restores LWJGL's native extension ABI, applies the ownership guard and builds a new DLL; it refuses to overwrite a differing existing output. Fixed timestamps and stripped debug metadata make repeat builds byte-identical. `-Pristine -OutputDirectory <new-directory>` retains the LWJGL extensions but omits the ownership guard for the negative control.

The compatibility patch backports Windows changes from [LWJGL's source fork](https://github.com/LWJGL-CI/glfw/commit/03f63972b7238a179123f4cb3fa2c2539d5a6dea), including window attachment, EGL config access and native-library overrides. This is a marked local build from the released GLFW 3.4 base, not a reconstruction of the unavailable historical LWJGL 3.3.3 CI tree. It preserves every export of the original DLL.

The selected DLL's SHA-256 is `B768315F7ED9D171B77CBBFCAA0E52F681F5141F145FAC24EC619DC27739C29F`.

The native regression fixture under `scripts/glfw-repro` creates only its own hidden windows and a child fixture. It exercises a foreign active window under attached input queues, plus owned-window Shift-key press/release handling. It does not manipulate the user's applications. Detailed receipts and the original failures are retained under `agent-notes/validation/glfw-20261103`.

With the .NET 10 SDK installed, run `scripts/Test-GlfwRuntime.ps1` for the native regression. Optionally pass `-PristineDll <control-dll>` to verify that the unguarded control still exits with the expected access violation. Each run creates a fresh evidence directory. The fixture also checks that attaching and releasing a GLFW wrapper leaves an externally owned HWND alive.

Primary references: [GLFW Windows event processing](https://github.com/glfw/glfw/blob/3.4/src/win32_window.c), [Microsoft input-queue documentation](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-attachthreadinput), [LWJGL library selection](https://javadoc.lwjgl.org/org/lwjgl/system/Configuration.html#GLFW_LIBRARY_NAME).
