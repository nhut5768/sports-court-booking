# Windows Credential Manager. No credential files or command-line secrets.
if(-not ('SanNhaLocalVault' -as [type])) {
 Add-Type -TypeDefinition @"
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
public static class SanNhaLocalVault {
 [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
 struct Credential {
  public uint Flags, Type;
  public string TargetName, Comment;
  public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
  public uint CredentialBlobSize;
  public IntPtr CredentialBlob;
  public uint Persist, AttributeCount;
  public IntPtr Attributes;
  public string TargetAlias, UserName;
 }
 [DllImport("advapi32.dll", EntryPoint="CredReadW", CharSet=CharSet.Unicode, SetLastError=true)]
 static extern bool ReadNative(string target, uint type, uint flags, out IntPtr result);
 [DllImport("advapi32.dll", EntryPoint="CredWriteW", CharSet=CharSet.Unicode, SetLastError=true)]
 static extern bool WriteNative(ref Credential value, uint flags);
 [DllImport("advapi32.dll", EntryPoint="CredDeleteW", CharSet=CharSet.Unicode, SetLastError=true)]
 static extern bool DeleteNative(string target, uint type, uint flags);
 [DllImport("advapi32.dll")] static extern void CredFree(IntPtr value);
 public static string Read(string target) {
  IntPtr pointer;
  if(!ReadNative(target,1,0,out pointer)) {
   int error=Marshal.GetLastWin32Error(); if(error==1168)return null;
   throw new Win32Exception(error,"Cannot read local credential vault.");
  }
  try { var value=(Credential)Marshal.PtrToStructure(pointer,typeof(Credential));
   return Marshal.PtrToStringUni(value.CredentialBlob,(int)value.CredentialBlobSize/2);
  } finally {CredFree(pointer);}
 }
 public static void Write(string target,string secret) {
  int size=Encoding.Unicode.GetByteCount(secret);
  if(size>2560)throw new InvalidOperationException("Local credential exceeds Windows vault size limit.");
  IntPtr blob=Marshal.StringToCoTaskMemUni(secret);
  try {
   var value=new Credential {Type=1,TargetName=target,UserName=Environment.UserName,
    CredentialBlobSize=(uint)size,CredentialBlob=blob,Persist=2};
   if(!WriteNative(ref value,0))throw new Win32Exception(Marshal.GetLastWin32Error(),"Cannot save local credential vault.");
  } finally {Marshal.ZeroFreeCoTaskMemUnicode(blob);}
 }
 public static void Delete(string target) {
  if(!DeleteNative(target,1,0)&&Marshal.GetLastWin32Error()!=1168)
   throw new Win32Exception(Marshal.GetLastWin32Error(),"Cannot delete local credential vault.");
 }
}
"@
}
