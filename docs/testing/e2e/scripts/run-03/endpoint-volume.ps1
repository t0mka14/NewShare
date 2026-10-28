# List render endpoints with their master volume / mute; optionally set the scalar volume of one.
# Usage: .endpoint-volume.ps1 [-Name "CABLE Input"] [-Set 1.0] [-Flow 0 render | 1 capture]
param([string]$Name = "CABLE Input", [double]$Set = -1, [int]$Flow = 0)
Add-Type -TypeDefinition @'
using System; using System.Runtime.InteropServices;
[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDeviceEnumerator { int EnumAudioEndpoints(int flow,int mask,out IMMDeviceCollection c); }
[Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDeviceCollection { int GetCount(out int n); int Item(int i,out IMMDevice d); }
[Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IMMDevice { int Activate(ref Guid iid,int ctx,IntPtr p,[MarshalAs(UnmanagedType.IUnknown)] out object o); int OpenPropertyStore(int a,out IPropertyStore s); int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id); }
[Guid("886d8eeb-8cf2-4446-8d02-cdba1dbdcf99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IPropertyStore { int GetCount(out int c); int GetAt(int i,out PropKey k); int GetValue(ref PropKey k,out PropVariant v); }
[StructLayout(LayoutKind.Sequential)] struct PropKey { public Guid fmtid; public int pid; }
[StructLayout(LayoutKind.Explicit)] struct PropVariant { [FieldOffset(0)] public short vt; [FieldOffset(8)] public IntPtr p; }
[Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IAudioEndpointVolume { int a(); int b(); int c(out uint n); int d(float v,ref Guid g); int SetMasterVolumeLevelScalar(float v,ref Guid g); int e(); int GetMasterVolumeLevelScalar(out float v); int f(); int g(); int h(); int i(bool m,ref Guid g); int GetMute(out bool m); }
public static class Ep {
  public static string[] Run(string name, double set, int flow) {
    var t = Type.GetTypeFromCLSID(new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E"));
    var en = (IMMDeviceEnumerator)Activator.CreateInstance(t);
    IMMDeviceCollection col; en.EnumAudioEndpoints(flow,1,out col); int n; col.GetCount(out n);
    var res = new System.Collections.Generic.List<string>();
    for (int i=0;i<n;i++){ IMMDevice d; col.Item(i,out d);
      IPropertyStore ps; d.OpenPropertyStore(0,out ps);
      var k = new PropKey{ fmtid=new Guid("a45c254e-df1c-4efd-8020-67d146a850e0"), pid=14 };
      PropVariant v; ps.GetValue(ref k,out v); string fn = Marshal.PtrToStringUni(v.p);
      var iid = new Guid("5CDF2C82-841E-4546-9722-0CF74078229A"); object o; d.Activate(ref iid,23,IntPtr.Zero,out o);
      var ev = (IAudioEndpointVolume)o; var g = Guid.Empty;
      if (set >= 0 && fn.Contains(name)) ev.SetMasterVolumeLevelScalar((float)set, ref g);
      float vol; ev.GetMasterVolumeLevelScalar(out vol); bool m; ev.GetMute(out m);
      res.Add(fn + " | volume=" + vol + " | muted=" + m);
    }
    return res.ToArray();
  }
}
'@
[Ep]::Run($Name, $Set, $Flow)
