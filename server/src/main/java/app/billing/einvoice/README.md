# E-invoice JSON export

This package builds **schema version 1.1** JSON for B2B invoices and credit notes so you can bulk-upload on the
[NIC e-invoice portal](https://einvoice.gov.in/). It does **not** call the IRP API (GSP credentials and live
internet are out of scope).

**Before filing:** verify every field name and value rule against the **current** NIC e-invoice schema and portal
validation — GST rules change; this app only maps shop data to a best-effort JSON shape.
