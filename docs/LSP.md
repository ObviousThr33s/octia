# Java language server — one server, two clients

The editor and Claude Code drive the **same** JDT.LS build, so they cannot
disagree about what compiles.

## What was wired

| Piece | Where |
|---|---|
| Claude Code plugin | `jdtls-lsp@claude-plugins-official`, user scope |
| Launcher | `~/.local/bin/jdtls.cmd` (+ a `.bat` forwarder) |
| Server binaries | bundled inside the VS Code extension `redhat.java` |
| Java runtime | the JRE 21 bundled with that same extension |
| Per-project state | `%LOCALAPPDATA%\jdtls-workspaces\<flattened-project-path>\` |

Eclipse JDT.LS ships a Python launcher and no Windows executable, and the
Claude Code plugin simply runs a command called `jdtls`. `redhat.java` already
carries a full JDT.LS plus a matching JRE, so the wrapper drives that copy
rather than installing a second server. One server on the machine, one set of
diagnostics.

`~/.local/bin` was already on the User PATH, so nothing about the environment
was changed to make `jdtls` resolvable.

## Two details the wrapper gets right

**Nothing is hardcoded.** The extension directory, the JRE, and the Equinox
launcher jar all carry version numbers that change on every update. The script
resolves each at run time, so a `redhat.java` update does not silently break it.

**Configuration is copied, not shared.** JDT.LS writes into its
`-configuration` directory. Pointing it at the extension's own `config_win`
would fail on a read-only install and be wiped on the next extension update, so
each project gets a private copy alongside its workspace. Workspaces are keyed
by project path for the same reason — JDT.LS keeps an index and build state per
workspace, and sharing one directory across projects corrupts it.

## Verifying it

Speak LSP to it directly — this is what confirmed the wiring:

```powershell
python -c "import subprocess,json; p=subprocess.Popen(['jdtls.cmd'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,shell=True,cwd=r'D:\Serenity\octia'); m=json.dumps({'jsonrpc':'2.0','id':1,'method':'initialize','params':{'processId':None,'rootUri':'file:///D:/Serenity/octia','capabilities':{}}}).encode(); p.stdin.write(b'Content-Length: '+str(len(m)).encode()+b'\r\n\r\n'+m); p.stdin.flush(); print(p.stdout.read(400))"
```

A healthy server answers with an `InitializeResult` naming
`JDT Language Server (Standard)`.

## Restart required

Claude Code loads LSP servers from enabled plugins at session start. The plugin
is installed and enabled, but `.java` intelligence only appears in a **new
session**. Until then, `gradlew build` is the source of truth here — it
disagrees with JDT.LS about nothing that matters.

## The editor side

`redhat.java` is already installed. Opening `D:\Serenity\octia` in VS Code
imports the Gradle project through Buildship and starts its own JDT.LS instance
from the same binaries. Separate processes, identical server build and
identical classpath — which is the part that matters.
