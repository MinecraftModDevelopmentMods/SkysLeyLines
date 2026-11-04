using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text.Json;

// Native regression fixture. It creates/manipulates only its own hidden windows
// and one explicitly launched child fixture. It never enumerates user windows.
internal static class Program
{
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate int Init();
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void Hint(int name, int value);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate nint Create(int width, int height, [MarshalAs(UnmanagedType.LPStr)] string title, nint monitor, nint share);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate nint Handle(nint window);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate nint Version();
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void Poll();
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void Destroy(nint window);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void Wait(double seconds);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void KeyCallback(nint window, int key, int scan, int action, int mods);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate nint SetKeyCallback(nint window, KeyCallback callback);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate int GetKey(nint window, int key);
    [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate nint Attach(nint hwnd, nint share);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern nint CreateWindowExW(uint exStyle, string cls, string title, uint style, int x, int y, int width, int height, nint parent, nint menu, nint instance, nint param);
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)] private static extern bool SetPropW(nint window, string name, nint value);
    [DllImport("user32.dll")] private static extern bool DestroyWindow(nint window);
    [DllImport("user32.dll")] private static extern bool IsWindow(nint window);
    [DllImport("user32.dll")] private static extern bool AttachThreadInput(uint from, uint to, bool attach);
    [DllImport("user32.dll")] private static extern nint SetActiveWindow(nint window);
    [DllImport("user32.dll")] private static extern nint GetActiveWindow();
    [DllImport("user32.dll")] private static extern nint SendMessageW(nint window, uint message, nuint wParam, nint lParam);
    [DllImport("user32.dll")] private static extern short GetKeyState(int key);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(nint window, out uint pid);
    [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
    [DllImport("kernel32.dll")] private static extern uint SetErrorMode(uint mode);
    [StructLayout(LayoutKind.Sequential)] private struct Message { public nint hwnd; public uint message; public nuint wParam; public nint lParam; public uint time; public int x, y; public uint privateData; }
    [DllImport("user32.dll")] private static extern bool PeekMessageW(out Message message, nint window, uint min, uint max, uint remove);
    [DllImport("user32.dll")] private static extern bool TranslateMessage(ref Message message);
    [DllImport("user32.dll")] private static extern nint DispatchMessageW(ref Message message);

    private static T Export<T>(nint library, string name) where T : Delegate => Marshal.GetDelegateForFunctionPointer<T>(NativeLibrary.GetExport(library, name));

    private static int Main(string[] args)
    {
        SetErrorMode(0x0001 | 0x0002); // Fixture-local: do not open a system crash dialog.
        if (args[0] == "child") return Child(int.Parse(args[1]));
        var dll = Path.GetFullPath(args[0]);
        var report = Path.GetFullPath(args[1]);
        nint library = NativeLibrary.Load(dll);
        Console.WriteLine("GLFW version: " + Marshal.PtrToStringUTF8(Export<Version>(library,"glfwGetVersionString")()));
        if (Export<Init>(library,"glfwInit")() == 0) throw new Exception("Fixture GLFW initialization failed");
        Export<Hint>(library,"glfwWindowHint")(0x00022001, 0); // GLFW_CLIENT_API = GLFW_NO_API
        Export<Hint>(library,"glfwWindowHint")(0x00020004, 0); // GLFW_VISIBLE = false
        nint own = Export<Create>(library,"glfwCreateWindow")(32,32,"Ley GLFW parent fixture",0,0);
        if (own == 0) throw new Exception("Fixture window creation failed");
        nint ownHandle = Export<Handle>(library,"glfwGetWin32Window")(own);
        if (args.Length > 2 && args[2] == "external")
        {
            // Preserve LWJGL's attachment extension and external HWND lifetime.
            for (int i=0;i<100;i++)
            {
                nint external = CreateWindowExW(0,"STATIC","Ley GLFW owned external fixture",0x80000000,0,0,32,32,0,0,0,0);
                nint wrapper = Export<Attach>(library,"glfwAttachWin32Window")(external,0);
                if (wrapper == 0 || Export<Handle>(library,"glfwGetWin32Window")(wrapper) != external)
                    throw new Exception("External attachment failed");
                SetActiveWindow(external);
                Export<Poll>(library,"glfwPollEvents")();
                Export<Destroy>(library,"glfwDestroyWindow")(wrapper);
                if (!IsWindow(external)) throw new Exception("GLFW destroyed the externally owned HWND");
                DestroyWindow(external);
            }
            File.WriteAllText(report,JsonSerializer.Serialize(new {mode="external",attachments=100,passed=true}));
            Export<Destroy>(library,"glfwDestroyWindow")(own);
            Export<Poll>(library,"glfwTerminate")();
            return 0;
        }
        if (args.Length > 2 && args[2] == "owned")
        {
            int presses = 0, releases = 0;
            KeyCallback callback = (w,key,scan,action,mods) => { if(key == 340) { if(action==1) presses++; if(action==0) releases++; } };
            Export<SetKeyCallback>(library,"glfwSetKeyCallback")(own,callback);
            // Make the original window a non-head list entry to exercise traversal.
            nint second = Export<Create>(library,"glfwCreateWindow")(32,32,"Ley GLFW second owned fixture",0,0);
            SetActiveWindow(ownHandle);
            if (GetActiveWindow() != ownHandle || GetKeyState(0xA0) < 0) throw new Exception("Owned fixture precondition failed (release physical Shift)");
            for(int i=0;i<1000;i++)
            {
                SendMessageW(ownHandle,0x100,0x10,(nint)0x002A0001); // Own hidden window only: WM_KEYDOWN / Shift.
                if(Export<GetKey>(library,"glfwGetKey")(own,340)!=1) throw new Exception("Owned Shift press was not delivered");
                Export<Poll>(library,"glfwPollEvents")();
                if(Export<GetKey>(library,"glfwGetKey")(own,340)!=0) throw new Exception("Owned modifier repair was lost");
            }
            File.WriteAllText(report,JsonSerializer.Serialize(new {mode="owned",presses,releases,passed=presses==1000 && releases==1000}));
            Export<Destroy>(library,"glfwDestroyWindow")(second);
            Export<Destroy>(library,"glfwDestroyWindow")(own);
            Export<Poll>(library,"glfwTerminate")();
            GC.KeepAlive(callback);
            return presses==1000 && releases==1000 ? 0 : 1;
        }
        var start = new ProcessStartInfo(Environment.ProcessPath!) { UseShellExecute=false, RedirectStandardOutput=true, RedirectStandardInput=true, CreateNoWindow=true, WindowStyle=ProcessWindowStyle.Hidden };
        if (Path.GetFileNameWithoutExtension(Environment.ProcessPath).Equals("dotnet",StringComparison.OrdinalIgnoreCase)) start.ArgumentList.Add(typeof(Program).Assembly.Location);
        start.ArgumentList.Add("child"); start.ArgumentList.Add(Environment.ProcessId.ToString());
        using var child = Process.Start(start)!;
        var details = JsonSerializer.Deserialize<ChildWindow>(child.StandardOutput.ReadLine()!)!;
        if (details.Pid != child.Id) throw new Exception("Child fixture identity mismatch");
        uint thread = GetCurrentThreadId();
        bool attached = AttachThreadInput(thread, details.Tid, true);
        if (!attached) throw new Exception("Fixture input queues could not attach");
        try
        {
            SetActiveWindow((nint)details.Hwnd);
            nint active = GetActiveWindow();
            GetWindowThreadProcessId(active, out uint activePid);
            bool foreign = active == (nint)details.Hwnd && activePid == child.Id;
            File.WriteAllText(report, JsonSerializer.Serialize(new { phase="before_poll", dll, ownHandle=(long)ownHandle, activeHandle=(long)active, activePid, childPid=child.Id, foreignActiveWindow=foreign },new JsonSerializerOptions {WriteIndented=true}));
            if (!foreign) throw new Exception("Precondition failed: active window did not belong to child fixture");
            int count = args.Length > 2 ? int.Parse(args[2]) : 1;
            var poll = Export<Poll>(library,"glfwPollEvents");
            var wait = Export<Wait>(library,"glfwWaitEventsTimeout");
            for(int i=0;i<count;i++) { poll(); wait(0.000001); }
            File.AppendAllText(report,$"\nPOLL_AND_WAIT_RETURNED iterations={count}\n");
            return 0;
        }
        finally
        {
            SetActiveWindow(ownHandle);
            AttachThreadInput(thread, details.Tid, false);
            child.StandardInput.WriteLine("stop"); child.StandardInput.Flush();
            if (!child.WaitForExit(5000)) child.Kill();
            Export<Destroy>(library,"glfwDestroyWindow")(own);
            Export<Poll>(library,"glfwTerminate")();
        }
    }

    private record ChildWindow(long Hwnd,uint Tid,int Pid);
    private static int Child(int parentPid)
    {
        using var parent = Process.GetProcessById(parentPid);
        nint window = CreateWindowExW(0,"STATIC","Ley GLFW foreign fixture",0x80000000,0,0,32,32,0,0,0,0);
        if (window == 0 || !SetPropW(window,"GLFW",1)) throw new Exception("Foreign fixture creation failed");
        Console.WriteLine(JsonSerializer.Serialize(new ChildWindow((long)window,GetCurrentThreadId(),Environment.ProcessId)));
        Console.Out.Flush();
        var stop = Task.Run(() => Console.ReadLine());
        while (!parent.HasExited && !stop.IsCompleted)
        {
            while(PeekMessageW(out var message,0,0,0,1)) { TranslateMessage(ref message); DispatchMessageW(ref message); }
            Thread.Sleep(1);
        }
        DestroyWindow(window);
        return 0;
    }
}
