# M32 Monitor RTA — Windows bridge v1

Signal: M32R Monitor Out L -> EVO4 Input 1 (LINE, phantom 48V OFF) -> Windows -> TCP 18777 -> Android RTA.
This is MONO 48 kHz signed 16-bit little-endian PCM, 1024 samples per chunk.
SOLO/PFL/AFL selection happens on the mixer; the bridge does NOT issue OSC SOLO commands.

## Windows
1. Install Python 3.11+ and run `py -m pip install sounddevice`.
2. Run `py monitor_bridge.py --list` and note EVO4 input device index.
3. Run `py monitor_bridge.py --device INDEX`.
4. Permit inbound TCP 18777 in Windows Firewall **only on Private/LAN**.
5. Find Windows LAN IPv4 address with `ipconfig`.

## Android
1. Connect phone to the same trusted Wi-Fi/LAN as Windows.
2. In the RTA input selector choose **M32 NETWORK (PC)**.
3. Enter the Windows LAN IPv4 address in PC IP, then START RTA.
4. Verify **NETWORK AUDIO ACTIVE** and that the RTA reacts to SOLO.

Do not expose port 18777 to the public internet; this prototype has no encryption or authentication.
Do not connect Monitor Out to a microphone-only input or enable phantom power.
This bridge is not M32 OSC audio. M32 OSC connectivity alone does not prove audio routing.
For no-PC use, connect EVO4 to Android USB-C and select MIXER USB.
