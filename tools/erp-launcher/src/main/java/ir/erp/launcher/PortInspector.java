package ir.erp.launcher;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Read-only TCP probe: a bound port is a warning until its owner is identified. */
final class PortInspector {
  private PortInspector() { }
  static boolean listening(int port) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
