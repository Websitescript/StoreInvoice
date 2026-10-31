# Store Invoice Generator V2 (Android)

Offline invoice app that reproduces the store's thermal-receipt invoice (216 x 842.5 pt, Courier-style 8.5 pt, 40 columns,
Code 128 barcodes, terms page) and fills only the dynamic fields.

## Build the APK (GitHub Actions)
1. Push this folder to a GitHub repo (or upload the zip contents). 2. Open **Actions -> Build APK -> Run workflow**.
3. Download the `StoreInvoiceGenerator-apk` artifact and install `app-debug.apk`.
Local build: `gradle assembleDebug` (JDK 17, Android SDK 34).

## Notes
* Font: Liberation Mono (metric-identical to Courier New, same 0.6 em advance) is bundled. To use real Courier New, copy your licensed
  `cour.ttf` to `app/src/main/assets/CourierNew.ttf` - it is picked up automatically.
* Page height is 842.5 pt; Android only supports whole-point pages, so pages are created at 843 pt and the MediaBox is patched back to 842.5.
* Data: `files/data.json` (atomic writes). Invoices are stored as full snapshots, so editing products/customers never changes old invoices.
* Saved PDFs: Android/data/com.storeinvoice.app/files/invoices/<OrderNo>.pdf
* Product-type HSN defaults (editable under Product types): 85102000 trimmer, 85183020 earphones/neckband, 85183010 headset - please verify with the client's CA.

## V2 changes
* Company line, store name, CIN, Regd. office (3 lines), website/brand name (e.g. falah.com) and support email are all editable in Store Settings and per invoice.
* Order number is editable (SOA + 12 digits). History has **Re-create**, which loads a saved invoice back into the form so a lost/deleted record can be rebuilt with the same date and order number.
* Barcodes now follow the invoice content directly (no blank gap); terms follow on the same page when they fit.
* Labels above every field. Same package id as V1 - export a JSON backup first; GitHub debug builds use a new signing key each run, so uninstall the old APK before installing a new build and re-import.
