"""Kai Soundgood Monitor Audio Bridge (LAN-only, mono PCM16 48 kHz).
Install: py -m pip install sounddevice
List: py monitor_bridge.py --list
Run: py monitor_bridge.py --device 3 --bind 0.0.0.0 --port 18777
Connect M32R Monitor Out L to EVO4 Input 1 (line, phantom OFF).
Never expose port to the internet. No encryption or authentication in v1.
"""
import argparse
import socket
import sounddevice as sd

RATE = 48000
BLOCK = 1024

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--list", action="store_true")
    p.add_argument("--device", type=int, default=None)
    p.add_argument("--bind", default="0.0.0.0")
    p.add_argument("--port", type=int, default=18777)
    args = p.parse_args()
    if args.list:
        print(sd.query_devices())
        return
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((args.bind, args.port))
        server.listen(1)
        print(f"MONITOR BRIDGE listening on {args.bind}:{args.port}; 48k PCM16 mono")
        with sd.RawInputStream(samplerate=RATE, blocksize=BLOCK, device=args.device,
                               channels=1, dtype="int16") as stream:
            while True:
                conn, address = server.accept()
                print("Client connected:", address)
                with conn:
                    try:
                        while True:
                            samples, overflow = stream.read(BLOCK)
                            if overflow:
                                print("Warning: audio input overflow")
                            conn.sendall(samples)
                    except (BrokenPipeError, ConnectionResetError, OSError):
                        print("Client disconnected")

if __name__ == "__main__":
    main()
