package de.steve.spiderfoot;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.net.http.SslError;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 41;
    private static final int NOTIFICATION_PERMISSION_REQUEST = 42;

    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger startupGeneration = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WebView webView;
    private View statusPanel;
    private ProgressBar startProgress;
    private ProgressBar pageProgress;
    private TextView statusTitle;
    private TextView statusDetail;
    private Button retryButton;
    private ValueCallback<Uri[]> pendingFileCallback;
    private boolean firstPageLoaded;
    private String baseUrl;
    private String privateRoot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        baseUrl = SpiderFootService.getBaseUrl(this);
        privateRoot = SpiderFootService.getWebRoot(this);

        webView = findViewById(R.id.web_view);
        statusPanel = findViewById(R.id.status_panel);
        startProgress = findViewById(R.id.start_progress);
        pageProgress = findViewById(R.id.page_progress);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        retryButton = findViewById(R.id.retry_button);

        configureWebView();
        retryButton.setOnClickListener(view -> startAndOpenServer());
        requestNotificationPermissionIfNeeded();
        startAndOpenServer();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES);
        }
        WebView.setWebContentsDebuggingEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (isLocalUrl(Uri.parse(url))) {
                    firstPageLoaded = true;
                    statusPanel.setVisibility(View.GONE);
                }
                pageProgress.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeUrl(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeUrl(Uri.parse(url));
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame() && isLocalUrl(request.getUrl())) {
                    showError("Die lokale Oberfläche ist nicht erreichbar.",
                            String.valueOf(error.getDescription()));
                }
            }

            @Override
            public void onReceivedSslError(
                    WebView view,
                    SslErrorHandler handler,
                    SslError error
            ) {
                // SpiderFoot Android itself uses plain loopback HTTP. Never bypass TLS errors
                // on external pages accidentally opened inside the WebView.
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress.setProgress(newProgress);
                pageProgress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = filePathCallback;

                try {
                    Intent chooser = fileChooserParams.createIntent();
                    chooser.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (ActivityNotFoundException exc) {
                    pendingFileCallback.onReceiveValue(null);
                    pendingFileCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Kein Dateiauswahldialog verfügbar.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(this::downloadFile);
    }

    private boolean routeUrl(Uri uri) {
        if (isLocalUrl(uri)) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return true;
        }

        if ("http".equalsIgnoreCase(scheme)
                || "https".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException exc) {
                Toast.makeText(this, "Für diesen Link ist keine App verfügbar.",
                        Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }

    private boolean isLocalUrl(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        return "http".equalsIgnoreCase(uri.getScheme())
                && SpiderFootService.HOST.equals(uri.getHost())
                && uri.getPort() == SpiderFootService.PORT
                && (path.equals(privateRoot) || path.startsWith(privateRoot + "/"));
    }

    private void startAndOpenServer() {
        int generation = startupGeneration.incrementAndGet();
        firstPageLoaded = false;
        showStarting();

        Intent serviceIntent = new Intent(this, SpiderFootService.class)
                .setAction(SpiderFootService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        startupExecutor.execute(() -> waitForServer(generation));
    }

    private void waitForServer(int generation) {
        String diagnostic = "";
        for (int attempt = 0; attempt < 180; attempt++) {
            if (generation != startupGeneration.get() || isFinishing()) {
                return;
            }

            String serviceError = SpiderFootService.getLastError();
            if (serviceError != null && !serviceError.isEmpty()) {
                diagnostic = serviceError;
                break;
            }

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl).openConnection();
                connection.setConnectTimeout(600);
                connection.setReadTimeout(1200);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
                int status = connection.getResponseCode();
                if (status >= 200 && status < 500) {
                    mainHandler.post(() -> {
                        if (generation == startupGeneration.get()) {
                            webView.loadUrl(baseUrl);
                        }
                    });
                    return;
                }
                diagnostic = "HTTP-Status " + status;
            } catch (Exception exc) {
                diagnostic = exc.getClass().getSimpleName() + ": " + String.valueOf(exc.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            try {
                Thread.sleep(500);
            } catch (InterruptedException exc) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        final String finalDiagnostic = diagnostic;
        mainHandler.post(() -> {
            if (generation == startupGeneration.get() && !firstPageLoaded) {
                showError("SpiderFoot konnte nicht gestartet werden.", finalDiagnostic);
            }
        });
    }

    private void showStarting() {
        statusPanel.setVisibility(View.VISIBLE);
        startProgress.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        statusTitle.setText(R.string.server_starting);
        statusDetail.setText(R.string.server_waiting);
    }

    private void showError(String title, String detail) {
        statusPanel.setVisibility(View.VISIBLE);
        startProgress.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        statusTitle.setText(title);

        if (detail == null || detail.trim().isEmpty()) {
            detail = "Keine zusätzliche Fehlermeldung vorhanden.";
        }
        if (detail.length() > 1800) {
            detail = detail.substring(0, 1800) + "\n…";
        }
        statusDetail.setText(detail);
    }

    private void downloadFile(
            String url,
            String userAgent,
            String contentDisposition,
            String mimeType,
            long contentLength
    ) {
        if (!isLocalUrl(Uri.parse(url))) {
            routeUrl(Uri.parse(url));
            return;
        }

        String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setMimeType(mimeType);
        request.setTitle(fileName);
        request.setDescription("SpiderFoot-Export");
        request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
        );
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.addRequestHeader("User-Agent", userAgent);

        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !cookie.isEmpty()) {
            request.addRequestHeader("Cookie", cookie);
        }

        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            manager.enqueue(request);
            Toast.makeText(this,
                    String.format(Locale.ROOT, "%s wird heruntergeladen.", fileName),
                    Toast.LENGTH_LONG).show();
        } catch (Exception exc) {
            Toast.makeText(this,
                    "Download fehlgeschlagen: " + exc.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || pendingFileCallback == null) {
            return;
        }

        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        pendingFileCallback.onReceiveValue(result);
        pendingFileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        startupGeneration.incrementAndGet();
        startupExecutor.shutdownNow();
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        webView.stopLoading();
        webView.destroy();
        super.onDestroy();
    }
}
