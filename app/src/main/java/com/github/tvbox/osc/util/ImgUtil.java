package com.github.tvbox.osc.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.FitCenter;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.util.Util;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class ImgUtil {
    private static final Map<String, Drawable> drawableCache = new HashMap<>();
    public static int defaultWidth = 244;
    public static int defaultHeight = 320;

    public static class Style {
        public float ratio;
        public String type;

        public Style(float ratio, String type) {
            this.ratio = ratio;
            this.type = type;
        }
    }

    public static boolean isBase64Image(String picUrl) {
        return picUrl != null && picUrl.startsWith("data:image");
    }

    public static Style initStyle() {
        String bStyle = ApiConfig.get().getHomeSourceBean().getStyle();
        if (!bStyle.isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject(bStyle);
                return new Style((float) jsonObject.getDouble("ratio"), jsonObject.getString("type"));
            } catch (JSONException ignored) {
            }
        }
        return null;
    }

    public static int spanCountByStyle(Style style, int defaultCount) {
        int spanCount = defaultCount;
        if ("rect".equals(style.type)) {
            if (style.ratio >= 1.7) {
                spanCount = 3;
            } else if (style.ratio >= 1.3) {
                spanCount = 4;
            }
        } else if ("list".equals(style.type)) {
            spanCount = 1;
        }
        return spanCount;
    }

    public static int getStyleDefaultWidth(Style style) {
        int styleDefaultWidth = 280;
        if (style.ratio < 1) styleDefaultWidth = 214;
        if (style.ratio > 1.7) styleDefaultWidth = 380;
        return styleDefaultWidth;
    }

    public static Bitmap decodeBase64ToBitmap(String base64Str) {
        String base64Data = base64Str.substring(base64Str.indexOf(",") + 1);
        byte[] decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
    }

    public static void load(String url, ImageView view, int roundingRadius) {
        load(url, view, roundingRadius, 0, 0, null);
    }

    public static void load(String url, ImageView view, int roundingRadius, int newWidth, int newHeight) {
        load(url, view, roundingRadius, newWidth, newHeight, null);
    }

    public static void load(String url, ImageView view, int roundingRadius, int newWidth, int newHeight, String label, ImageView.ScaleType scaleType) {
        view.setScaleType(scaleType);
        if (roundingRadius <= 0) roundingRadius = 1;
        Drawable fallback = createTextDrawable(TextUtils.isEmpty(label) ? "TVBox" : label, newWidth, newHeight, roundingRadius);
        Drawable placeholder = createImagePlaceholderDrawable(newWidth, newHeight, roundingRadius);
        if (isInvalidImageUrl(url)) {
            view.setImageDrawable(fallback);
            return;
        }
        RequestOptions options = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .transform(new FitCenter(), new RoundedCorners(roundingRadius));
        if (newWidth > 0 && newHeight > 0) {
            options = options.override(newWidth, newHeight);
        }
        Glide.with(App.getInstance())
                .asBitmap()
                .load(getUrl(url))
                .placeholder(placeholder)
                .error(fallback)
                .listener(getListener(view, scaleType, fallback))
                .apply(options)
                .into(view);
    }

    public static void load(String url, ImageView view, int roundingRadius, int newWidth, int newHeight, String label) {
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (roundingRadius <= 0) roundingRadius = 1;
        Drawable fallback = createTextDrawable(TextUtils.isEmpty(label) ? "TVBox" : label, newWidth, newHeight, roundingRadius);
        Drawable placeholder = createImagePlaceholderDrawable(newWidth, newHeight, roundingRadius);
        if (isInvalidImageUrl(url)) {
            view.setImageDrawable(fallback);
            return;
        }
        RequestOptions options = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .transform(new CenterCrop(), new RoundedCorners(roundingRadius));
        if (newWidth > 0 && newHeight > 0) {
            options = options.override(newWidth, newHeight);
        }
        Glide.with(App.getInstance())
                .asBitmap()
                .load(getUrl(url))
                .placeholder(placeholder)
                .error(fallback)
                .listener(getListener(view, ImageView.ScaleType.CENTER_CROP, fallback))
                .apply(options)
                .into(view);
    }

    public static void loadUrl(String url, ImageView view) {
        load(url, view, 10);
    }

    public static void loadVideoScreenshot(String uri, ImageView imageView, long frameTimeMicros) {
        RequestOptions requestOptions = RequestOptions.frameOf(frameTimeMicros * 1000)
                .transform(new CenterCrop(), new RoundedCorners(10));
        Glide.with(App.getInstance())
                .load(uri)
                .skipMemoryCache(true)
                .apply(requestOptions)
                .into(imageView);
    }

    /**
     * 加载图片并应用轻微虚化（高斯模糊）效果，用于大背景海报。
     */
    public static void loadBlur(String url, ImageView view, int radius) {
        if (isInvalidImageUrl(url)) {
            view.setImageDrawable(createTextDrawable("TVBox"));
            return;
        }
        if (radius <= 0) radius = 12;
        RequestOptions options = new RequestOptions()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .dontAnimate()
                .transform(new CenterCrop(), new BlurTransformation(radius));
        Glide.with(App.getInstance())
                .asBitmap()
                .load(getUrl(url))
                .apply(options)
                .into(view);
    }

    public static int getRandomColor() {
        Random random = new Random();
        return Color.argb(255, random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }

    public static Drawable createTextDrawable(String text) {
        return createTextDrawable(text, 0, 0, AutoSizeUtils.mm2px(App.getInstance(), 10));
    }

    private static Drawable createTextDrawable(String text, int width, int height, float cornerRadius) {
        if (TextUtils.isEmpty(text)) text = "TVBox";
        if (width <= 0) width = 180;
        if (height <= 0) height = 240;
        if (cornerRadius <= 0) cornerRadius = 1;
        String key = text + "_" + width + "x" + height + "_" + (int) cornerRadius;
        text = text.substring(0, 1);
        if (drawableCache.containsKey(key)) return drawableCache.get(key);
        int randomColor = getRandomColor();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(randomColor);
        paint.setStyle(Paint.Style.FILL);
        RectF rectF = new RectF(0, 0, width, height);
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(60);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float x = width / 2f;
        float y = (height - fontMetrics.bottom - fontMetrics.top) / 2f;
        canvas.drawText(text, x, y, paint);
        Drawable drawable = new BitmapDrawable(App.getInstance().getResources(), bitmap);
        drawableCache.put(key, drawable);
        return drawable;
    }

    private static Drawable createImagePlaceholderDrawable(int width, int height, float cornerRadius) {
        if (width <= 0) width = 180;
        if (height <= 0) height = 240;
        if (cornerRadius <= 0) cornerRadius = 1;
        String key = "placeholder_" + width + "x" + height + "_" + (int) cornerRadius;
        if (drawableCache.containsKey(key)) return drawableCache.get(key);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Bitmap icon = BitmapFactory.decodeResource(App.getInstance().getResources(), com.github.tvbox.osc.R.drawable.icon_img_placeholder);
        if (icon != null) {
            float left = (width - icon.getWidth()) / 2f;
            float top = (height - icon.getHeight()) / 2f;
            canvas.drawBitmap(icon, left, top, null);
        }

        Drawable drawable = new BitmapDrawable(App.getInstance().getResources(), bitmap);
        drawableCache.put(key, drawable);
        return drawable;
    }

    public static void clearCache() {
        drawableCache.clear();
    }

    public static void clearMemoryCache() {
        clearCache();
        try {
            Glide.get(App.getInstance()).clearMemory();
            LOG.i("echo-img-clear-memory-cache");
        } catch (Throwable th) {
            LOG.i("echo-img-clear-memory-cache-error:" + th.getMessage());
        }
    }

    private static Object getUrl(String url) {
        if (url.startsWith("data:")) return url;
        String header = null;
        String referer = null;
        String ua = null;
        String cookie = null;
        if (url.contains("@Headers=")) {
            header = url.split("@Headers=")[1].split("@")[0];
            try {
                header = URLDecoder.decode(header, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
        }
        if (url.contains("@Cookie=")) cookie = url.split("@Cookie=")[1].split("@")[0];
        if (url.contains("@User-Agent=")) ua = url.split("@User-Agent=")[1].split("@")[0];
        if (url.contains("@Referer=")) referer = url.split("@Referer=")[1].split("@")[0];
        url = url.split("@")[0];
        if (TextUtils.isEmpty(url)) return null;

        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        Map<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(header)) {
            try {
                JsonObject jsonInfo = new Gson().fromJson(header, JsonObject.class);
                for (String key : jsonInfo.keySet()) {
                    putHeader(headers, key, jsonInfo.get(key).getAsString());
                }
            } catch (Throwable ignored) {
            }
        }
        putHeader(headers, "Cookie", cookie);
        if (!TextUtils.isEmpty(ua)) putHeader(headers, "User-Agent", ua);
        if (!TextUtils.isEmpty(referer)) putHeader(headers, "Referer", referer);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.setHeader(entry.getKey(), entry.getValue());
        return new GlideUrl(url, builder.build());
    }

    public static Object getImageModel(String url) {
        return getUrl(url);
    }

    private static boolean isInvalidImageUrl(String url) {
        if (TextUtils.isEmpty(url)) return true;
        url = url.trim();
        if (TextUtils.isEmpty(url)) return true;
        return hasEmptyProxyParam(url, "img");
    }

    private static boolean hasEmptyProxyParam(String url, String key) {
        if (!url.startsWith("proxy://") && !url.contains("/proxy?")) return false;
        int queryIndex = url.indexOf('?');
        String query = queryIndex >= 0 ? url.substring(queryIndex + 1) : url.substring("proxy://".length());
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf('=');
            if (eqIndex < 0) continue;
            if (key.equals(pair.substring(0, eqIndex)) && TextUtils.isEmpty(pair.substring(eqIndex + 1))) {
                return true;
            }
        }
        return false;
    }

    private static void putHeader(Map<String, String> headers, String key, String value) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) return;
        headers.put(key, value.trim());
    }

    private static RequestListener<Bitmap> getListener(final ImageView view, final ImageView.ScaleType scaleType, final Drawable fallback) {
        return new RequestListener<Bitmap>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                view.setScaleType(scaleType);
                view.setImageDrawable(fallback);
                return true;
            }

            @Override
            public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                view.setScaleType(scaleType);
                return false;
            }
        };
    }

    /**
     * 基于 Stack Blur 算法的 Glide BitmapTransformation，用于背景海报虚化。
     */
    public static class BlurTransformation extends BitmapTransformation {
        private static final String ID = "com.github.tvbox.osc.util.ImgUtil$BlurTransformation";
        private final int radius;

        public BlurTransformation(int radius) {
            this.radius = radius;
        }

        @Override
        protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
            Bitmap bitmap = toTransform;
            if (bitmap.getConfig() == null) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            }
            return stackBlur(bitmap, radius);
        }

        @Override
        public void updateDiskCacheKey(@NonNull java.security.MessageDigest messageDigest) {
            messageDigest.update((ID + radius).getBytes(CHARSET));
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof BlurTransformation) {
                BlurTransformation other = (BlurTransformation) o;
                return other.radius == radius;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Util.hashCode(ID.hashCode(), Util.hashCode(radius));
        }
    }

    /**
     * Stack Blur 算法实现（近似高斯模糊），不依赖 RenderScript。
     */
    private static Bitmap stackBlur(Bitmap sentBitmap, int radius) {
        if (radius < 1) return sentBitmap;
        Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig() == null ? Bitmap.Config.ARGB_8888 : sentBitmap.getConfig(), true);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int temp = 256 * divsum;
        int[] dv = new int[temp];
        for (i = 0; i < temp; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;
        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - Math.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
