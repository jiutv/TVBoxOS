package com.github.tvbox.osc.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.VideoParseRuler;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 全屏 WebView 容器 Activity
 * 对标 NewBox 的 webview:// 协议实现，支持两种模式：
 *
 * 1. BROWSE (webview://) — 纯网页浏览模式
 *    - 不劫持任何资源，WebView 内播放 HTML5 视频
 *    - 页面自带返回/刷新/关闭按钮栏，auto-hide
 *    - 遥控器 KEYCODE_BACK 先尝试网页历史后退，再关闭 Activity
 *
 * 2. SNIFF (webview-sniff://) — 网页嗅探模式
 *    - shouldInterceptRequest 拦截资源请求
 *    - VideoParseRuler.checkIsVideoForParse 识别 m3u8/mp4/mpd
 *    - isFilter + AdBlocker 过滤静态资源和广告
 *    - 抓到第一个有效流 → setResult(RESULT_OK, streamUrl + headers) + finish()
 *    - 嗅探期间用户按 back → 正常关闭，不闪退，RESULT_CANCELED
 *
 * 协议格式（与 NewBox 保持一致）：
 *   webview://https://xxx         → BROWSE
 *   webview-sniff://https://xxx  → SNIFF
 *
 * Intent extras（Activity 启动方传入）：
 *   url  (String, 必填)  —— 真实 URL（已去掉 scheme 前缀）
 *   mode (String, 可选)  —— "sniff" 或其他（默认 browse）
 */
public class FullWebActivity extends BaseActivity {

    // Activity 启动 / 结果传递用常量
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SNIFF = "sniff";
    public static final String RESULT_STREAM_URL = "streamUrl";
    public static final String RESULT_STREAM_HEADERS = "streamHeaders";

    // 顶部栏 auto-hide 时长
    private static final long TOP_BAR_HIDE_MS = 4000L;

    // 嗅探超时：多久没抓到流就放弃（留用户手动 back）
    private static final long SNIFF_TIMEOUT_MS = 120000L; // 2 分钟

    private WebView mWebView;
    private FrameLayout mRootContainer;
    private LinearLayout mTopBar;
    private TextView mBtnBack;
    private TextView mBtnRefresh;
    private TextView mBtnClose;
    private TextView mTvMode;

    private String mEntryUrl;       // 入口页面 URL（嗅探模式的 webUrl 参数）
    private boolean mSniffMode = false;
    private boolean mSniffFound = false;   // 嗅探是否已命中（命中后不再处理后续请求）

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** 顶部栏 auto-hide runnable */
    private final Runnable mHideTopBarRun = new Runnable() {
        @Override
        public void run() {
            if (mTopBar != null) mTopBar.setVisibility(View.GONE);
        }
    };

    /** 嗅探超时 runnable —— 超时后停止嗅探拦截（不再 block 资源） */
    private final Runnable mSniffTimeoutRun = new Runnable() {
        @Override
        public void run() {
            if (!mSniffFound) {
                LOG.i("🎬 FullWebActivity 嗅探超时，留在网页播放");
                mSniffFound = true; // 设为 true 阻止后续 shouldInterceptRequest 再 block 资源
            }
        }
    };

    @Override
    protected int getLayoutResID() {
        return com.github.tvbox.osc.R.layout.activity_full_web;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWebView = findViewById(com.github.tvbox.osc.R.id.full_web_view);
        mRootContainer = findViewById(com.github.tvbox.osc.R.id.root_container);
        mTopBar = findViewById(com.github.tvbox.osc.R.id.top_bar);
        mBtnBack = findViewById(com.github.tvbox.osc.R.id.btn_back);
        mBtnRefresh = findViewById(com.github.tvbox.osc.R.id.btn_refresh);
        mBtnClose = findViewById(com.github.tvbox.osc.R.id.btn_close);
        mTvMode = findViewById(com.github.tvbox.osc.R.id.tv_mode);

        // 读启动参数
        Intent intent = getIntent();
        mEntryUrl = intent.getStringExtra(EXTRA_URL);
        String mode = intent.getStringExtra(EXTRA_MODE);
        mSniffMode = MODE_SNIFF.equals(mode);

        if (TextUtils.isEmpty(mEntryUrl)) {
            LOG.w("🎬 FullWebActivity 未收到 url，关闭");
            finish();
            return;
        }

        // 配置 WebView（电视盒子风格，对标 NewBox）
        initWebView();

        // 顶部栏按钮
        mBtnBack.setOnClickListener(v -> {
            hideTopBar();
            if (mWebView != null && mWebView.canGoBack()) {
                mWebView.goBack();
            } else {
                finish();
            }
        });
        mBtnRefresh.setOnClickListener(v -> {
            hideTopBar();
            if (mWebView != null) mWebView.reload();
        });
        mBtnClose.setOnClickListener(v -> finish());

        // 初始显示顶部栏，几秒后自动隐藏
        showTopBar();

        // 模式提示
        mTvMode.setText(mSniffMode ? "嗅探模式：抓到流自动切换播放器" : "浏览模式：仅网页内播放");

        // 嗅探模式启动超时计时
        if (mSniffMode) {
            mHandler.postDelayed(mSniffTimeoutRun, SNIFF_TIMEOUT_MS);
        }

        // 加载页面
        LOG.i("🎬 FullWebActivity 加载: url=" + mEntryUrl + " mode=" + (mSniffMode ? "sniff" : "browse"));
        mWebView.loadUrl(mEntryUrl);
    }

    /**
     * 初始化 WebView 配置
     * - UA 使用电视盒子 UA（NewBox 风格）
     * - 开启 JS、自动播放、跨域、cookie
     * - 嗅探模式下挂 shouldInterceptRequest 拦截
     * - WebChromeClient 承接网页全屏视频
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings s = mWebView.getSettings();

        // UA：电视盒子浏览器 UA
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; TV Build/TVBox) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        // 允许网页视频自动播放（不走用户手势）
        s.setMediaPlaybackRequiresUserGesture(false);

        // 允许混合内容（http + https 混用）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        // 允许跨域访问
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            s.setAllowFileAccessFromFileURLs(true);
            s.setAllowUniversalAccessFromFileURLs(true);
        }

        // Cookie 全放行
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(mWebView, true);
        }

        // WebChromeClient：承接网页触发的全屏视频视图
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public View getVideoLoadingProgressView() {
                return new View(FullWebActivity.this);
            }
        });

        // WebViewClient：嗅探模式下拦截资源请求
        mWebView.setWebViewClient(mSniffMode ? new SniffWebViewClient() : new BrowseWebViewClient());
    }

    /** 浏览模式 Client —— 只做页面加载状态回调，不劫持任何资源 */
    private class BrowseWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            showTopBar();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showTopBar();
            super.onPageFinished(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            // 浏览模式：保持 WebView 内跳转，不拦截
            return false;
        }
    }

    /**
     * 嗅探模式 Client
     * - shouldInterceptRequest 识别视频流 → setResult 回传 + 关闭 Activity
     * - isFilter + AdBlocker.isAd 过滤静态资源和广告（嗅探期间阻断它们加速加载）
     * - 嗅探命中 / 超时 后不再 block 任何资源（让页面在 WebView 内正常渲染）
     */
    private class SniffWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            showTopBar();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            showTopBar();
            super.onPageFinished(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false; // 嗅探模式也保持 WebView 内跳转
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (mSniffFound) return null; // 已命中或已超时，不再拦截

            String url = request.getUrl().toString();
            if (!url.startsWith("http")) return null;
            if (url.endsWith("/favicon.ico")) return null;

            // 1. 广告：直接阻断
            if (AdBlocker.isAd(url)) {
                return AdBlocker.createEmptyResource();
            }

            // 2. VideoParseRuler.isFilter 命中（过滤规则）：也阻断
            if (VideoParseRuler.isFilter(mEntryUrl, url)) {
                return AdBlocker.createEmptyResource();
            }

            // 3. 嗅探视频流
            if (VideoParseRuler.checkIsVideoForParse(mEntryUrl, url)) {
                mSniffFound = true;
                mHandler.removeCallbacksAndMessages(null); // 清掉超时计时
                LOG.i("🎬 FullWebActivity 嗅探命中：" + url);

                // 组装 headers：从 WebView 请求里拷贝 ua / referer / origin + 当前 Cookie
                HashMap<String, String> headers = new HashMap<>();
                Map<String, String> reqHeaders = request.getRequestHeaders();
                if (reqHeaders != null) {
                    for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                        String k = e.getKey();
                        if (k.equalsIgnoreCase("user-agent") || k.equalsIgnoreCase("referer") || k.equalsIgnoreCase("origin")) {
                            headers.put(k, e.getValue() == null ? "" : " " + e.getValue());
                        }
                    }
                }
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    headers.put("Cookie", " " + cookie);
                }

                // 回传：streamUrl + base64(json(headers))
                Intent result = new Intent();
                result.putExtra(RESULT_STREAM_URL, url);
                result.putExtra(RESULT_STREAM_HEADERS, encodeHeaders(headers));
                setResult(RESULT_OK, result);
                finish();
                return null; // 虽然我们命中了，但返回 null 让原请求也继续走——避免页面崩
            }

            return null; // 非视频流、非广告、非过滤资源：放行
        }
    }

    /** 把 headers Map 序列化为 base64(JSON)，方便 Intent extras 传递 */
    public static String encodeHeaders(HashMap<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, String> e : headers.entrySet()) {
                obj.put(e.getKey(), e.getValue());
            }
            return Base64.encodeToString(obj.toString().getBytes(), Base64.NO_WRAP);
        } catch (Exception e) {
            LOG.e("🎬 encodeHeaders 失败：" + e.getMessage());
            return "";
        }
    }

    /** 把 base64(JSON) 反序列化为 headers Map */
    public static HashMap<String, String> decodeHeaders(String encoded) {
        HashMap<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(encoded)) return map;
        try {
            String json = new String(Base64.decode(encoded, Base64.NO_WRAP));
            JSONObject obj = new JSONObject(json);
            org.json.JSONObject obj2 = obj;
            java.util.Iterator<String> keys = obj2.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj2.optString(k, ""));
            }
        } catch (Exception e) {
            LOG.e("🎬 decodeHeaders 失败：" + e.getMessage());
        }
        return map;
    }

    // ============ 顶部栏 auto-hide ============

    private void showTopBar() {
        mHandler.removeCallbacks(mHideTopBarRun);
        if (mTopBar != null) mTopBar.setVisibility(View.VISIBLE);
        mHandler.postDelayed(mHideTopBarRun, TOP_BAR_HIDE_MS);
    }

    private void hideTopBar() {
        mHandler.removeCallbacks(mHideTopBarRun);
        if (mTopBar != null) mTopBar.setVisibility(View.GONE);
    }

    // ============ 遥控器按键 ============

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mTopBar != null && mTopBar.getVisibility() == View.VISIBLE) {
                // 顶部栏可见 → 先隐藏它，按 back 再触发网页后退
                hideTopBar();
                return true;
            }
            if (mWebView != null && mWebView.canGoBack()) {
                mWebView.goBack();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_MENU) {
            // 菜单键切换顶部栏
            if (mTopBar != null) {
                if (mTopBar.getVisibility() == View.VISIBLE) {
                    hideTopBar();
                } else {
                    showTopBar();
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ============ 生命周期 ============

    @Override
    protected void onPause() {
        super.onPause();
        if (mWebView != null) mWebView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
            showTopBar();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mWebView != null) {
            mWebView.stopLoading();
            mWebView.loadUrl("about:blank");
            mWebView.clearCache(true);
            mWebView.clearHistory();
            mWebView.removeAllViews();
            mWebView.destroy();
            mWebView = null;
        }
        super.onDestroy();
    }
}
