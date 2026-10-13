package app.billing.print;

import org.springframework.stereotype.Component;

@Component
public class DelegatingRawPrintTransport implements RawPrintTransport {

    private final SystemPrintTransport system;
    private final NetworkPrintTransport network;

    public DelegatingRawPrintTransport(SystemPrintTransport system, NetworkPrintTransport network) {
        this.system = system;
        this.network = network;
    }

    @Override
    public void send(byte[] payload, Printer printer) throws PrintTransportException {
        switch (printer.getKind()) {
            case SYSTEM -> system.send(payload, printer);
            case NETWORK -> network.send(payload, printer);
            default -> throw new PrintTransportException("Unknown printer kind");
        }
    }
}
