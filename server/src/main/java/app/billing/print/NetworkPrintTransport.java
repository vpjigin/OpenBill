package app.billing.print;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import org.springframework.stereotype.Component;

@Component
public class NetworkPrintTransport {

    private static final int TIMEOUT_MS = 5000;

    public void send(byte[] payload, Printer printer) throws PrintTransportException {
        if (printer.getKind() != PrinterKind.NETWORK) {
            throw new PrintTransportException("Not a network printer");
        }
        String host = printer.getHost();
        if (host == null || host.isBlank()) {
            throw new PrintTransportException("Network printer host is missing");
        }
        int port = printer.getPort() > 0 ? printer.getPort() : 9100;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(payload);
            out.flush();
        } catch (Exception e) {
            throw new PrintTransportException("Network print failed: " + e.getMessage(), e);
        }
    }
}
