# KSeF official PDF visualization bundle

`ksef-fe-invoice-converter.umd.js` is a prebuilt, self-contained browser bundle of the official
Ministry of Finance invoice/UPO visualization library
[CIRFMF/ksef-pdf-generator](https://github.com/CIRFMF/ksef-pdf-generator) (npm package
`@akmf/ksef-fe-invoice-converter`). It bundles its own fonts (pdfmake `vfs_fonts`), QR rendering and
i18n, so it needs no network access at runtime.

It is executed inside a headless `WebView` (Android) / `WKWebView` (iOS) to reproduce the official
KSeF invoice layout. The exposed UMD global is `window["ksef-fe-invoice-converter"]`; the entry
point
used by the app is `generateInvoice(file, { nrKSeF, qrCode }, "base64")`, which returns a base64
PDF.

Desktop does not use this bundle — it keeps the Apache FOP (`ksef-fop`) renderer.

## Regenerating / upgrading

The bundle is licensed under the MIT/ISC terms in `LICENSE`. To update to a newer library version:

```bash
git clone https://github.com/CIRFMF/ksef-pdf-generator
cd ksef-pdf-generator
npm install
npm run build
# copy dist/ksef-fe-invoice-converter.umd.cjs over this file (renamed to *.umd.js)
```

Current pinned version: **1.1.19**.
