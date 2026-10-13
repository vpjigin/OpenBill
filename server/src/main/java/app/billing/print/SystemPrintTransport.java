package app.billing.print;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;

import org.springframework.stereotype.Component;

@Component
public class SystemPrintTransport {

    public void send(byte[] payload, Printer printer) throws PrintTransportException {
        if (printer.getKind() != PrinterKind.SYSTEM) {
            throw new PrintTransportException("Not a system printer");
        }
        String name = printer.getSystemName();
        if (name == null || name.isBlank()) {
            throw new PrintTransportException("System printer name is missing");
        }
        PrintService service = findService(name);
        if (service == null) {
            throw new PrintTransportException("OS printer not found: " + name);
        }
        Doc doc = new SimpleDoc(payload, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        try {
            job.print(doc, new HashPrintRequestAttributeSet());
        } catch (PrintException e) {
            throw new PrintTransportException("System print failed: " + e.getMessage(), e);
        }
    }

    static PrintService findService(String name) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService ps : services) {
            if (ps.getName().equals(name)) {
                return ps;
            }
        }
        return null;
    }

    public static String[] listSystemPrinterNames() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        String[] names = new String[services.length];
        for (int i = 0; i < services.length; i++) {
            names[i] = services[i].getName();
        }
        return names;
    }
}
