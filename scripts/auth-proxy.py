#!/usr/bin/env python3
"""
Local auth proxy that forwards requests to an upstream authenticated proxy.
Solves the issue where Java's HttpURLConnection doesn't send Proxy-Authorization
headers for HTTPS CONNECT requests.

Usage:
    python3 auth-proxy.py [local_port]

The proxy reads HTTP_PROXY/HTTPS_PROXY from environment and extracts credentials.
"""

import os
import socket
import threading
import sys
import base64
from urllib.parse import urlparse

# Default local port
LOCAL_PORT = 3128

def parse_proxy_url(url):
    """Parse proxy URL and extract host, port, username, password."""
    if not url:
        return None
    parsed = urlparse(url)
    return {
        'host': parsed.hostname,
        'port': parsed.port or 3128,
        'username': parsed.username,
        'password': parsed.password
    }

def create_auth_header(username, password):
    """Create Proxy-Authorization header value."""
    if not username or not password:
        return None
    credentials = f"{username}:{password}"
    encoded = base64.b64encode(credentials.encode()).decode()
    return f"Basic {encoded}"

def forward_data(source, dest, name=""):
    """Forward data from source to dest socket."""
    try:
        while True:
            data = source.recv(4096)
            if not data:
                break
            dest.sendall(data)
    except Exception as e:
        pass
    finally:
        try:
            source.close()
            dest.close()
        except:
            pass

def handle_client(client_socket, upstream_proxy):
    """Handle a client connection."""
    try:
        # Read the initial request
        request = b""
        while b"\r\n\r\n" not in request:
            chunk = client_socket.recv(4096)
            if not chunk:
                client_socket.close()
                return
            request += chunk

        # Parse the request
        header_end = request.find(b"\r\n\r\n")
        headers = request[:header_end].decode('utf-8', errors='replace')
        body = request[header_end + 4:]

        lines = headers.split("\r\n")
        first_line = lines[0]

        # Connect to upstream proxy
        upstream = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        upstream.connect((upstream_proxy['host'], upstream_proxy['port']))

        # Create auth header
        auth_header = create_auth_header(upstream_proxy['username'], upstream_proxy['password'])

        if first_line.startswith("CONNECT"):
            # For HTTPS CONNECT requests, inject Proxy-Authorization
            new_headers = [first_line]
            auth_added = False

            for line in lines[1:]:
                if line.lower().startswith("proxy-authorization:"):
                    continue  # Skip existing auth, we'll add our own
                new_headers.append(line)

            if auth_header:
                new_headers.insert(1, f"Proxy-Authorization: {auth_header}")

            new_request = "\r\n".join(new_headers) + "\r\n\r\n"
            upstream.sendall(new_request.encode() + body)

            # Read response from upstream
            response = b""
            while b"\r\n\r\n" not in response:
                chunk = upstream.recv(4096)
                if not chunk:
                    break
                response += chunk

            # Forward response to client
            client_socket.sendall(response)

            # Check if connection was established
            if b"200" in response.split(b"\r\n")[0]:
                # Start bidirectional forwarding
                t1 = threading.Thread(target=forward_data, args=(client_socket, upstream, "client->upstream"))
                t2 = threading.Thread(target=forward_data, args=(upstream, client_socket, "upstream->client"))
                t1.start()
                t2.start()
                t1.join()
                t2.join()
            else:
                client_socket.close()
                upstream.close()
        else:
            # For HTTP requests, inject Proxy-Authorization
            new_headers = [first_line]

            for line in lines[1:]:
                if line.lower().startswith("proxy-authorization:"):
                    continue
                new_headers.append(line)

            if auth_header:
                new_headers.insert(1, f"Proxy-Authorization: {auth_header}")

            new_request = "\r\n".join(new_headers) + "\r\n\r\n"
            upstream.sendall(new_request.encode() + body)

            # Forward response
            while True:
                chunk = upstream.recv(4096)
                if not chunk:
                    break
                client_socket.sendall(chunk)

            client_socket.close()
            upstream.close()

    except Exception as e:
        print(f"Error handling client: {e}")
        try:
            client_socket.close()
        except:
            pass

def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else LOCAL_PORT

    # Get upstream proxy from environment
    proxy_url = os.environ.get('HTTPS_PROXY') or os.environ.get('HTTP_PROXY')
    if not proxy_url:
        print("ERROR: No HTTP_PROXY or HTTPS_PROXY environment variable set")
        sys.exit(1)

    upstream_proxy = parse_proxy_url(proxy_url)
    if not upstream_proxy:
        print(f"ERROR: Could not parse proxy URL: {proxy_url}")
        sys.exit(1)

    print(f"Upstream proxy: {upstream_proxy['host']}:{upstream_proxy['port']}")
    print(f"Auth: {'Yes' if upstream_proxy['username'] else 'No'}")

    # Start local proxy server
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('127.0.0.1', port))
    server.listen(50)

    print(f"Local auth proxy listening on 127.0.0.1:{port}")
    print(f"Set HTTP_PROXY=http://127.0.0.1:{port} for Java/Gradle")

    while True:
        try:
            client, addr = server.accept()
            thread = threading.Thread(target=handle_client, args=(client, upstream_proxy))
            thread.daemon = True
            thread.start()
        except KeyboardInterrupt:
            print("\nShutting down...")
            break
        except Exception as e:
            print(f"Error accepting connection: {e}")

if __name__ == "__main__":
    main()
