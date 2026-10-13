package app.billing.print;

/** Sends raw ESC/POS bytes to a configured printer (system spooler or network socket). */
public interface RawPrintTransport {

    void send(byte[] payload, Printer printer) throws PrintTransportException;
}
