@echo off
rem ===========================================================================
rem  OCTIA - the door, from inside the repo.
rem
rem  ACT TWO / MILESTONE 2 / DESOP_[0.2.0.D.E.V.1]_door / SEEK PLAY |DEV|
rem
rem  Double-click this. It opens the front door, and the front door opens
rem  everything else.
rem
rem  Why this file exists: docs/FRONT_DOOR.md calls the door "the thing you
rem  double-click first" while the repo contained nothing to double-click. The
rem  only artifact was a .lnk that -Install writes to your desktop, outside the
rem  tree - so a fresh clone had no way in at all, and the one entry point was
rem  the one thing git could not carry.
rem
rem  Why .cmd and not a shortcut: a .lnk stores an absolute path and a machine's
rem  own JDK location, so it cannot be committed and mean anything on anybody
rem  else's disk. This resolves both at run time.
rem
rem  Why %~dp0 and not the working directory: Explorer, a terminal opened
rem  somewhere else, and a right-click "Run as administrator" all disagree about
rem  what the working directory is. %~dp0 is where THIS FILE is, which is the
rem  repo root by definition, and it carries its own trailing backslash.
rem
rem  There is nothing to build first, and since .gitignore was rooted to /build/
rem  there is nothing to compile either: tools/frontdoor/build/ now ships the jar
rem  and the icon, so a clone opens the door without a JDK at all. When the door's
rem  own sources change, tools/frontdoor.ps1 notices and recompiles - and the
rem  rebuilt jar has to be committed with them, or the next clone runs the old one.
rem ===========================================================================

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\frontdoor.ps1" %*

rem A double-clicked console window closes the instant the script returns, which
rem takes "no JDK found. The front door needs a JDK (17 or newer)" off the screen
rem before it can be read. Hold the window open on failure only - a successful
rem open should leave nothing behind, because the door is the window now.
if errorlevel 1 (
    echo.
    echo The door did not open. The reason is above.
    pause
)
