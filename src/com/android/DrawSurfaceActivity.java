package com.camera;

import android.app.Activity;
import android.view.View;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Button;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.res.Configuration;
import android.os.Bundle;
import android.graphics.Matrix;
import android.util.Log;
import com.camera.R;

public class DrawSurfaceActivity extends Activity implements SurfaceHolder.Callback {
    private static final String  TAG   = "DrawSurfaceActivity";
    private static final boolean DEBUG = true;

    private Button      mDrawBtn = null;
    private SurfaceView mSurface = null;
    private RenderThread mRenderThread = null;
    private SurfaceHolder mHolder = null;

    private int mSurfaceWidth  = 0;
    private int mSurfaceHeight = 0;
    private int mDrawVerticalPadding = 0;

    private String mFrameTip = "FrameNumber : ";

    @Override
    protected void onCreate (Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.draw_surface);

        mDrawBtn = (Button)findViewById(R.id.draw);
        mSurface = (SurfaceView)findViewById(R.id.surface);

        mDrawVerticalPadding = 50;

        init();
    }

    private void init () {
        mHolder = mSurface.getHolder();
        mHolder.addCallback(this);
        mHolder.setKeepScreenOn(true);

        mRenderThread = new RenderThread();
        mRenderThread.start();
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();
        mRenderThread.destroyRender();
    }

    @Override
    public void surfaceCreated (SurfaceHolder holder) {
        mRenderThread.startRender();
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder) {
        mRenderThread.stopRender();
    }

    @Override
    public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {
        mSurfaceWidth  = width;
        mSurfaceHeight = height;
    } 

    private class RenderThread extends Thread {
        private boolean isDestroyed = false;
        private boolean isStoped = true;

        private long flushCycle = 1000; //ms
        private long lastFlushTime = -1;
        private long frameNumber = 0;

        private Thread thread = null;

        @Override
        public void run () {
            thread = Thread.currentThread();

            while (!isDestroyed) {
                long currentTime = System.currentTimeMillis();

                try {
                    if (isStoped)
                        wait();
                }
                catch (Exception e){}

                if (lastFlushTime < 0 || currentTime - lastFlushTime >= flushCycle) {
                    lastFlushTime = currentTime;
                    frameNumber++;
                    draw();
                }

                try {
                    Thread.sleep(Math.max(flushCycle - (System.currentTimeMillis() - currentTime), 0));
                }
                catch (InterruptedException e) {
                    Log.w(TAG, "RenderThread sleep cycle interrupted");
                }
            }
        }

        private void draw () {
            int drawWidth  = mSurfaceWidth;
            int drawHeight = mSurfaceHeight - (2 * mDrawVerticalPadding);

            Canvas canvas = mHolder.lockCanvas();
            if (canvas != null) {
                canvas.save();
                canvas.translate(0, mDrawVerticalPadding);
                canvas.clipRect(0, 0, drawWidth, drawHeight);
                onDraw(canvas);
                canvas.restore();
                mHolder.unlockCanvasAndPost(canvas);
            }
        }

        private void onDraw (Canvas canvas) {
            String tip = mFrameTip + frameNumber;

            Paint paint = new Paint();
            paint.setTextSize(30);
            paint.setColor(Color.RED);

            canvas.drawColor(Color.BLACK);

            Rect bounds = new Rect();
            paint.getTextBounds(tip, 0, tip.length(), bounds);
            int left = (mSurfaceWidth - bounds.width()) / 2;
            int top  = bounds.height();
            canvas.drawText(tip, 0, tip.length(), left, top, paint);
        }

        synchronized void startRender () {
            if (isStoped == true) {
                frameNumber = 0;
                notify();
            }

            isStoped = false;
        }

        synchronized void stopRender () {
            isStoped = true;
        }

        void destroyRender () {
            isDestroyed = true;
        }
    }
}
