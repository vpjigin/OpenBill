package app.billing.print;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "printer")
public class Printer extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterKind kind;

    @Column(name = "system_name")
    private String systemName;

    private String host;

    @Column(nullable = false)
    private int port = 9100;

    @Column(name = "paper_mm", nullable = false)
    private int paperMm = 80;

    @Column(name = "chars_per_line")
    private Integer charsPerLine;

    @Column(name = "open_drawer", nullable = false)
    private boolean openDrawer;

    @Column(nullable = false)
    private boolean cut = true;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPrinter;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PrinterKind getKind() { return kind; }
    public void setKind(PrinterKind kind) { this.kind = kind; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public int getPaperMm() { return paperMm; }
    public void setPaperMm(int paperMm) { this.paperMm = paperMm; }
    public Integer getCharsPerLine() { return charsPerLine; }
    public void setCharsPerLine(Integer charsPerLine) { this.charsPerLine = charsPerLine; }
    public boolean isOpenDrawer() { return openDrawer; }
    public void setOpenDrawer(boolean openDrawer) { this.openDrawer = openDrawer; }
    public boolean isCut() { return cut; }
    public void setCut(boolean cut) { this.cut = cut; }
    public boolean isDefaultPrinter() { return defaultPrinter; }
    public void setDefaultPrinter(boolean defaultPrinter) { this.defaultPrinter = defaultPrinter; }

    public int effectiveCharsPerLine() {
        if (charsPerLine != null && charsPerLine > 0) {
            return charsPerLine;
        }
        return paperMm <= 58 ? 32 : 48;
    }
}
