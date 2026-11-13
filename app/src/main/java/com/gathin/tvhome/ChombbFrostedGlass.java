package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Rounded, blurred live snapshot of the launcher behind a modal panel.
 * CPU box blur of a 1/8 resolution capture avoids Android 12-only blur APIs
 * and works on Mi Box Android TV 9 as well as the PC emulator.
 */
public final class ChombbFrostedGlass extends Drawable {
    private final Bitmap source;
    private final View owner;
    private final int screenWidth, screenHeight;
    private final float radius, density;
    private final int tintAlpha;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final int[] point = new int[2];

    private ChombbFrostedGlass(View owner, Bitmap source, int screenWidth, int screenHeight,
                               float density, float radiusDp, int tintAlpha) {
        this.owner = owner;
        this.source = source;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.density = density;
        this.radius = radiusDp * density;
        this.tintAlpha = tintAlpha;
    }

    public static ChombbFrostedGlass fromActivity(View activityDecor, View panel,
                                                   float density, float radiusDp, int tintAlpha) {
        Bitmap capture = null;
        int w = activityDecor.getWidth(), h = activityDecor.getHeight();
        if (w > 0 && h > 0) {
            try {
                int smallW = Math.max(1, w / 8), smallH = Math.max(1, h / 8);
                capture = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(capture);
                canvas.scale((float) smallW / w, (float) smallH / h);
                activityDecor.draw(canvas);
                capture = blur(capture, 5);
            } catch (Throwable ignored) { capture = null; }
        }
        return new ChombbFrostedGlass(panel, capture, Math.max(w,1), Math.max(h,1),
                density, radiusDp, tintAlpha);
    }

    private static Bitmap blur(Bitmap bmp, int radius) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] pixels = new int[w*h], temp = new int[w*h], result = new int[w*h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int a=0,r=0,g=0,b=0,n=0;
                for (int k=-radius; k<=radius; k++) {
                    int xx = Math.min(w-1, Math.max(0,x+k));
                    int c = pixels[y*w+xx]; a+=Color.alpha(c);r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c);n++;
                }
                temp[y*w+x] = Color.argb(a/n,r/n,g/n,b/n);
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int a=0,r=0,g=0,b=0,n=0;
                for (int k=-radius; k<=radius; k++) {
                    int yy=Math.min(h-1,Math.max(0,y+k));
                    int c=temp[yy*w+x];a+=Color.alpha(c);r+=Color.red(c);g+=Color.green(c);b+=Color.blue(c);n++;
                }
                result[y*w+x] = Color.argb(a/n,r/n,g/n,b/n);
            }
            int[] swap=pixels;pixels=result;result=swap;
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        out.setPixels(pixels,0,w,0,0,w,h);
        bmp.recycle();
        return out;
    }

    @Override public void draw(Canvas canvas) {
        android.graphics.Rect b=getBounds();
        rect.set(b.left,b.top,b.right,b.bottom);
        if (source != null) {
            owner.getLocationOnScreen(point);
            BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix matrix = new Matrix();
            matrix.setScale((float)screenWidth / source.getWidth(), (float)screenHeight / source.getHeight());
            matrix.postTranslate(-point[0],-point[1]);
            shader.setLocalMatrix(matrix);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(shader);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(rect,radius,radius,paint);
            paint.setShader(null);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
                new int[]{Color.argb(tintAlpha,79,91,125),Color.argb(tintAlpha,34,47,68),Color.argb(tintAlpha,65,78,109)},
                null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.4f,1.3f*density));
        paint.setColor(0xE0E3EEFF);
        canvas.drawRoundRect(rect,radius,radius,paint);
        paint.setStrokeWidth(Math.max(1f,0.7f*density));
        paint.setColor(0x80FFFFFF);
        rect.inset(2*density,2*density);
        canvas.drawRoundRect(rect,Math.max(0,radius-2*density),Math.max(0,radius-2*density),paint);
        paint.setStyle(Paint.Style.FILL);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(android.graphics.ColorFilter filter) {paint.setColorFilter(filter); invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
