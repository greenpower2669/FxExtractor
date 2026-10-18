package fr.fxextractor;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * MIDI Gen AI v5 laboratory. The HTML, JS and WASM are packaged with the APK.
 * The CPU ONNX checkpoint is packaged as an asset and is loaded automatically\n * when this screen opens. No networking is required.
 * Generated MIDI remains on the device and is exported via Android SAF.
 */
public final class MidiAiActivity extends Activity {
    private static final int SAVE_MIDI = 21;
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private WebView webView;
    private byte[] lastMidi;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder diagnostic = new StringBuilder("FxExtractor 0.3.7 MIDI hors ligne\n");
    private String fileName = "FxExtractor-MIDI-AI-v5.mid";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new NativeBridge(), "FxNative");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.i("FxExtractorMIDI", message.message() + " @" + message.lineNumber());
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                    record("WebView JS ERROR: " + message.message());
                return true;
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // JS may fetch a checkpoint, but the top-level document must never navigate off-app.
                Uri u = request.getUrl();
                return request.isForMainFrame() && !"appassets.androidplatform.net".equals(u.getHost());
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handler.postDelayed(() -> {
                    if (webView == null) return;
                    view.evaluateJavascript("Boolean(window.__fxMidiReady)", value -> {
                        if (!"true".equals(value)) {
                            record("Module JS MIDI non initialisé après 10s (" + url + ")");
                            view.evaluateJavascript(
                                "document.getElementById('loadStatus').textContent='❌ Échec du chargement JavaScript MIDI. Clique Copier le diagnostic.'", null);
                        }
                    });
                }, 10000);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame())
                    record("Chargement écran : " + error.getDescription());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (!"https".equals(u.getScheme()) || !LOCAL_HOST.equals(u.getHost())) return null;
                String path = u.getPath();
                if (path == null || !path.startsWith("/midi/")) return null;
                String name = path.substring(6);
                if (name.isEmpty() || name.startsWith("/") || name.contains("\\")
                        || name.contains("..") || !name.matches("[A-Za-z0-9_./-]+"))
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found",
                        java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                String type = name.endsWith(".wasm") ? "application/wasm" :
                    name.endsWith(".onnx") ? "application/octet-stream" :
                    name.endsWith(".mjs") || name.endsWith(".js") ? "text/javascript" :
                    name.endsWith(".json") ? "application/json" : "text/html";
                try {
                    InputStream stream = getAssets().open("midi/" + name);
                    return new WebResourceResponse(type, "UTF-8", stream);
                } catch (IOException ignored) {
                    return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found",
                        java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                }
            }
        });
        setContentView(webView);
        webView.loadUrl("https://" + LOCAL_HOST + "/midi/index.html");
    }

    private synchronized void record(String message) {
        Log.i("FxExtractorMIDI", message);
        diagnostic.append(message).append('\n');
        if (diagnostic.length() > 16000) diagnostic.delete(0, diagnostic.length() - 12000);
    }

    public final class NativeBridge {
        @JavascriptInterface public void trace(String message) {
            if (message != null) record(message.substring(0, Math.min(message.length(), 1500)));
        }

        @JavascriptInterface public void copyDiagnostic(String report) {
            runOnUiThread(() -> {
                String full = (report == null ? "" : report) + "\n" + diagnostic.toString();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("FxExtractor diagnostic MIDI", full));
                Toast.makeText(MidiAiActivity.this, "Diagnostic copié. Tu peux le coller dans la conversation.", Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface public void receiveMidi(String encoded, String name) {
            try {
                byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                if (decoded.length < 20 || decoded.length > 2_000_000
                        || decoded[0] != 'M' || decoded[1] != 'T'
                        || decoded[2] != 'h' || decoded[3] != 'd')
                    throw new IllegalArgumentException("Fichier MIDI invalide ou trop volumineux");
                runOnUiThread(() -> {
                    lastMidi = decoded;
                    fileName = "FxExtractor-MIDI-AI-v5.mid";
                    Toast.makeText(MidiAiActivity.this, "MIDI prêt : " + decoded.length + " octets", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(MidiAiActivity.this,
                    "Erreur MIDI : " + ex.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface public void saveMidi() {
            runOnUiThread(() -> {
                if (lastMidi == null) {
                    Toast.makeText(MidiAiActivity.this, "Génère un MIDI avant de l'enregistrer", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("audio/midi");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                startActivityForResult(intent, SAVE_MIDI);
            });
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SAVE_MIDI || result != RESULT_OK || data == null || data.getData() == null)
            return;
        byte[] snapshot = lastMidi;
        if (snapshot == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IOException("Destination indisponible");
            out.write(snapshot);
            Toast.makeText(this, "MIDI enregistré", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export échoué : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("FxNative");
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
