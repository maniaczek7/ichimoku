"""
Serves the inject.js file for the bookmarklet.
Run:  py server.py
Then go to auta.ch and click the bookmarklet.
"""

import http.server
import os
import socket

PORT = 8777
STATIC_DIR = os.path.dirname(os.path.abspath(__file__))


def get_lan_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "<?>"


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=STATIC_DIR, **kwargs)

    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        super().end_headers()

    def log_message(self, format, *args):
        if ".js" in str(args[0]) or ".html" in str(args[0]):
            super().log_message(format, *args)


if __name__ == "__main__":
    lan_ip = get_lan_ip()
    with http.server.HTTPServer(("", PORT), Handler) as srv:
        print(f"  auta.ch filter tool")
        print(f"    Local:   http://localhost:{PORT}")
        print(f"    LAN:     http://{lan_ip}:{PORT}")
        print()
        print("  HOW TO USE:")
        print("  1. Open https://auta.ch/auctions  in your browser")
        print("  2. Click the bookmarklet (or paste this in console):")
        print()
        print(f"     localhost:    javascript:void(document.body.appendChild(document.createElement('script')).src='http://localhost:{PORT}/inject.js?'+Date.now())")
        print(f"     LAN:         javascript:void(document.body.appendChild(document.createElement('script')).src='http://{lan_ip}:{PORT}/inject.js?'+Date.now())")
        print()
        print("  Press Ctrl+C to stop")
        print()
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            print("\nStopped.")
