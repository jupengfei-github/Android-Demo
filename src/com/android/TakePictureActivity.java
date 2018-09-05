package com.camera;

import android.app.Activity;
import android.view.View;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.Button;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.graphics.Bitmap;
import android.content.res.Configuration;
import java.io.ByteArrayOutputStream;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.RotateDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.content.Intent;
import android.app.PendingIntent;
import android.os.Bundle;
import android.graphics.Matrix;
import android.util.Log;
import com.camera.R;

import com.vivo.common.BbkTitleView;

public class TakePictureActivity extends Activity implements SurfaceHolder.Callback {
    private static final String  TAG   = "TakePictureActivity";
    private static final boolean DEBUG = true;

    private ImageView mPreview     = null;
    private Button    mTakePicture = null;
    private SurfaceView mSurface = null;
    private Camera mCamera = null;
    private Bitmap mBitmap = null;
    private ShrinkSearchView mShrink = null;

    private int mRotation = 0;
    private boolean on = false;

    @Override
    protected void onCreate (Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.take_picture);

        mShrink = (ShrinkSearchView)findViewById(R.id.shrink);
        mPreview = (ImageView)findViewById(R.id.preview);
        mTakePicture = (Button)findViewById(R.id.takepicture);
        mSurface = (SurfaceView)findViewById(R.id.surface);

        BbkTitleView view = (BbkTitleView)findViewById(R.id.decorView);
        view.showLeftButton();
        view.showRightButton();

        mShrink.setShrinkBackground(R.drawable.decor_bg);
        view.setLeftButtonText("hello");
        view.setRightButtonText("hi");
        view.setCenterText("center");

        init();
    }

    @Override
    protected void onDestroy () {
        super.onDestroy();

        if (mBitmap != null && !mBitmap.isRecycled()) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }

    private void init () {
        mTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                if (!on)
                    mShrink.shrinkON();
                else
                    mShrink.shrinkOFF();

                on = !on;
                //sendIntent();
                /*if (mCamera != null)
                    mCamera.takePicture(null, null, new TakePictureCallback());
                 */
            }

            private void sendIntent () {
                Intent target = new Intent(Intent.ACTION_SEND);
                target.setType("image/*");

                Intent serviceIntent = new Intent("android.intent.action.test");
                PendingIntent pending = PendingIntent.getService(TakePictureActivity.this, 0,
                    serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                Intent chooser = Intent.createChooser(target, null);
                chooser.putExtra(Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER, pending.getIntentSender());
                TakePictureActivity.this.startActivity(chooser);
            }
        });

/*        SurfaceHolder holder = mSurface.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
        holder.setKeepScreenOn(true);
        holder.setFormat(PixelFormat.RGBA_8888); */
    }

    @Override
    public void surfaceCreated (SurfaceHolder holder) {
        mCamera = Camera.open();
        if (mCamera == null) {
            Log.d(TAG, "surfaceCreated -- open Camera failed");
            return;
        }

        Camera.Parameters parameter = mCamera.getParameters();
        parameter.setPreviewFrameRate(30);
        initCameraDisplayOrientation();

        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        }
        catch (Exception e) {
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "startPrview failed", e);
        }
   }

    private void initCameraDisplayOrientation () {
        Configuration config = getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT)
            mRotation = 90;
        else
            mRotation = 0;

        mCamera.setDisplayOrientation(mRotation);
    }

    @Override
    public void surfaceDestroyed (SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
        initCameraDisplayOrientation();
    }

    private class TakePictureCallback implements Camera.PictureCallback {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream(data.length);
            byteArray.write(data, 0, data.length);
            byte[] outputByte = byteArray.toByteArray();

            if (mBitmap != null && !mBitmap.isRecycled()) {
                mBitmap.recycle();
                mBitmap = null;
            }

            mBitmap = BitmapFactory.decodeByteArray(outputByte, 0, outputByte.length);
            
            Matrix matrix = new Matrix();
            matrix.preRotate(mRotation);

            Bitmap bitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(),
                matrix, false);
            if (mBitmap != bitmap) {
                mBitmap.recycle();
                mBitmap = bitmap;
            }

            mPreview.setImageBitmap(mBitmap);
            camera.startPreview();
        }
    }
}
