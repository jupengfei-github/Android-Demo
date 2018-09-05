package com.camera;

import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.graphics.Canvas;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.animation.AnimationSet;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.annotation.Nullable;
import android.annotation.DrawableRes;
import android.annotation.IdRes;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;

/**
 * @author jupengfei
 *
 * ShrinkSearchView is a ViewGroup container, you can add other UIViews
 * from xml layout or common api. ShrinkSearchView will decorate UIView
 * with SearchWidget. The SearchWidget can be shrinkOn or shrinOff to 
 * display search area.
 */
public class ShrinkSearchView extends FrameLayout {
    private static final String  TAG = "ShrinkSearchView";
    private static final boolean DBG = true;

    private static final long  ANIM_DURATION = 300; //ms

    private ViewGroup mRootView    = null;
    private EditText  mSearchView  = null;
    private Button    mSearchLabel = null;
    private View      mSearchIcon  = null;
    private Drawable  mBackground  = null;

    /* weather animating currently */
    private boolean mAnimating    = false;
    private boolean mDispatchAnim = false;

    /* current animation state */ 
    private AnimationState mAnimState = AnimationState.ANIM_SHRINK_ON;
    public enum AnimationState {
        ANIM_SHRINK_OFF,     /* shrink off */
        ANIM_SHRINKING_OFF,  /* shrinking to off */
        ANIM_SHRINK_ON,      /* shrink on  */
        ANIM_SHRINKING_ON,   /* shrinking to on  */
    }

    /* ShrinkSearchView background interpolator */
    private Interpolator mShrinkBgInterpolator = null;
    private float mShrinkBgAlpha = 1.0f;

    /* searchIcon gradient color */
    private float mSearchIconZoomFactor = 2.0f;
    private int mSearchIconOffColor = 0xFF555555;
    private int mSearchIconOnColor  = 0xFF0000FF;
    private float mSearchIconLastX = 0;

    private long mAnimStartTime = 0;
    private int mBackgroundExtra  = 0;
    private int mBackgroundLength = 0;
    private float mBackgroundAlpha = 1.0f;
    private Interpolator mBackgroundInterpolator = null;

    private Callback mCallback = null;
    private Runnable mAnimStartRunnable = new Runnable () {
        @Override
        public void run () {
            if (mCallback != null)
                mCallback.onAnimationStart();
        }
    };

    private Runnable mAnimEndRunnable = new Runnable () {
        @Override
        public void run () {
            mAnimating = false;

            if (mAnimState == AnimationState.ANIM_SHRINKING_OFF)
                mAnimState = AnimationState.ANIM_SHRINK_OFF;
            else if (mAnimState == AnimationState.ANIM_SHRINKING_ON)
                mAnimState = AnimationState.ANIM_SHRINK_ON;
 
            if (mCallback != null)
                mCallback.onAnimationEnd();
        }
    };

    public ShrinkSearchView (Context context) {
        this(context, null);
    }

    public ShrinkSearchView (Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public ShrinkSearchView (Context context, AttributeSet attr, int defStyle) {
        this(context, attr, R.attr.shrinkSearchView, defStyle);
    }

    private ShrinkSearchView (Context context, AttributeSet attr, int defAttr, int defStyle) {
        super(context, attr, defAttr, defStyle);

        TypedArray a = context.obtainStyledAttributes(attr, R.styleable.ShrinkView, defAttr, defStyle);
        /* will be zoom, must be Nine-Patch */
        mBackground = a.getDrawable(R.styleable.ShrinkView_animBg);
        int layoutId = a.getResourceId(R.styleable.ShrinkView_layout, -1);
        initLayout(layoutId);

        /* searchIcon will be zoom in animation. so ensure drawable could be operate normally(VectorDrawable) */
        Drawable d = a.getDrawable(R.styleable.ShrinkView_searchIcon);
        if (d != null)
            mSearchIcon.setBackground(d);

        a.recycle();

        mBackgroundInterpolator = new AccelerateDecelerateInterpolator();
        mShrinkBgInterpolator   = new AccelerateDecelerateInterpolator();
    }

    private void initLayout (int layoutId) {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(layoutId, this);

        mRootView    = (ViewGroup)findViewById(R.id.root);
        mSearchView  = (EditText)findViewById(R.id.search);
        mSearchLabel = (Button)findViewById(R.id.label);
        mSearchLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                if (mAnimState == AnimationState.ANIM_SHRINK_OFF)
                    shrinkON();
                else
                    Log.w(TAG, "ignore state change because of animating : " + mAnimState.name());
            }
        });

        mSearchIcon  = (View)findViewById(R.id.icon);
        mSearchIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View v) {
                if (mAnimState == AnimationState.ANIM_SHRINK_ON)
                    shrinkOFF();
                else
                    Log.w(TAG, "ignore state change because of animating : " + mAnimState.name());
            }
        });
    }

    @Override
    public void onFinishInflate () {
        /* ensure mRootView on the top */
        removeView(mRootView);
        addView(mRootView, mRootView.getLayoutParams());
    }

    public void shrinkON () {
        if (mAnimating || mAnimState == AnimationState.ANIM_SHRINK_ON) {
            Log.w(TAG, "ignore shrinkON for animting state : " + mAnimState.name());
            return;
        }

        mSearchIconLastX = mSearchIcon.getX();
        mSearchLabel.setVisibility(View.GONE);
        mSearchView.setVisibility(View.GONE);
        mSearchIcon.getBackground().setTint(mSearchIconOnColor);

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)mSearchIcon.getLayoutParams();
        lp.removeRule(RelativeLayout.ALIGN_PARENT_START);
        lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        mSearchIcon.setLayoutParams(lp);

        if (isLaidOut()) {
            mAnimStartTime = System.currentTimeMillis();
            mAnimState = AnimationState.ANIM_SHRINKING_ON;
            mAnimating = true;
            mDispatchAnim = true;
        }
        else
            mAnimState = AnimationState.ANIM_SHRINK_ON;

        post(mAnimStartRunnable);
    }

    public void shrinkOFF () {
        if (mAnimating || mAnimState == AnimationState.ANIM_SHRINK_OFF) {
            Log.w(TAG, "ignore shrinkOFF for animting state : " + mAnimState.name());
            return;
        }

        mSearchIconLastX = mSearchIcon.getX();
        mSearchLabel.setVisibility(View.VISIBLE);
        mSearchView.setVisibility(View.VISIBLE);

        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams)mSearchIcon.getLayoutParams();
        lp.addRule(RelativeLayout.ALIGN_PARENT_START);
        lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
        mSearchIcon.setLayoutParams(lp);

        if (isLaidOut()) {
            mAnimStartTime = System.currentTimeMillis();
            mAnimState = AnimationState.ANIM_SHRINKING_OFF;
            mAnimating = true;
            mDispatchAnim = true;
        }
        else
            mAnimState = AnimationState.ANIM_SHRINK_OFF;

        post(mAnimStartRunnable);
    }

    @Override
    protected void dispatchDraw (Canvas canvas) {
        if (mAnimState == AnimationState.ANIM_SHRINKING_OFF || mAnimState == AnimationState.ANIM_SHRINK_OFF) {
            initBackgroundAnimParams();
        }

        if (mDispatchAnim) {
            if (mAnimState == AnimationState.ANIM_SHRINKING_ON)
                initShrinkONAnim();
            else if (mAnimState == AnimationState.ANIM_SHRINKING_OFF)
                initShrinkOFFAnim();
        }

        mDispatchAnim = false;
        drawBackgroundAnimation(canvas);

        super.dispatchDraw(canvas);
    }

    private void initBackgroundAnimParams () {
        int distance = (int)(mSearchLabel.getX() - mSearchIcon.getX());
        mBackgroundLength = distance + mSearchLabel.getWidth();

        ViewGroup.MarginLayoutParams lpSearch = (ViewGroup.MarginLayoutParams)mSearchView.getLayoutParams();
        ViewGroup.MarginLayoutParams lpLabel  = (ViewGroup.MarginLayoutParams)mSearchLabel.getLayoutParams();
        mBackgroundExtra  = mSearchLabel.getWidth() + lpSearch.getMarginEnd() + lpLabel.getMarginStart();
    }

    private void initShrinkOFFAnim () {
        AnimationSet animSet = null;

        /* mSearchLabel alpha animation */
        AlphaAnimation mSearchLabelAlpha = new AlphaAnimation(0, 1.0f);
        mSearchLabelAlpha.setDuration(ANIM_DURATION);
        mSearchLabel.setAnimation(mSearchLabelAlpha);

        /* mSearchIcon scale/translate animation */
        ScaleAnimation mSearchIconScale = new ScaleAnimation(1, 1/mSearchIconZoomFactor, 1, 1/mSearchIconZoomFactor,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        TranslateAnimation mSearchIconTrans = new TranslateAnimation(mSearchIconLastX - getX(), 0, 0, 0);
        animSet = new AnimationSet(true);
        animSet.addAnimation(mSearchIconScale);
        animSet.addAnimation(mSearchIconTrans);
        animSet.setDuration(ANIM_DURATION);
        animSet.setFillAfter(true);
        mSearchIcon.setAnimation(animSet);

        /* mSearchView translate animation */
        TranslateAnimation mSearchViewTrans = new TranslateAnimation(mSearchIconLastX - mSearchIcon.getX(), 0, 0, 0);
        AlphaAnimation mSearchViewAlpha = new AlphaAnimation(0, 1.0f);
        animSet = new AnimationSet(true);
        animSet.addAnimation(mSearchViewTrans);
        animSet.addAnimation(mSearchViewAlpha);
        animSet.setDuration(ANIM_DURATION);
        mSearchView.setAnimation(animSet);
    }

    private void initShrinkONAnim () {
        AnimationSet animSet = null;

        /* mSearchLabel alpha animation */
        AlphaAnimation mSearchLabelAlpha = new AlphaAnimation(1.0f, 0);
        mSearchLabelAlpha.setDuration(ANIM_DURATION);
        mSearchLabel.setAnimation(mSearchLabelAlpha);

        /* mSearchIcon scale animation */
        ScaleAnimation mSearchIconScale = new ScaleAnimation(1/mSearchIconZoomFactor, 1, 1/mSearchIconZoomFactor, 1,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        TranslateAnimation mSearchIconTrans = new TranslateAnimation(mSearchIconLastX - mSearchIcon.getX(), 0, 0, 0);
        animSet = new AnimationSet(true);
        animSet.addAnimation(mSearchIconScale);
        animSet.addAnimation(mSearchIconTrans);
        animSet.setDuration(ANIM_DURATION);
        animSet.setFillAfter(true);
        mSearchIcon.setAnimation(animSet);

        /* mSearchView translate animation */
        TranslateAnimation mSearchViewTrans = new TranslateAnimation(0, mSearchIcon.getX() - mSearchIconLastX, 0, 0);
        AlphaAnimation mSearchViewAlpha = new AlphaAnimation(1.0f, 0);
        animSet = new AnimationSet(true);
        animSet.addAnimation(mSearchViewTrans);
        animSet.addAnimation(mSearchViewAlpha);
        animSet.setDuration(ANIM_DURATION);
        mSearchView.setAnimation(animSet);
    }

    private void drawBackgroundAnimation (Canvas canvas) {
        /** Search Area Background animation **/
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)mSearchIcon.getLayoutParams();
        int left  = lp.getMarginStart() + getPaddingStart();
        int right = mBackgroundLength - mBackgroundExtra;

        float bgAlpha = mBackgroundAlpha;
        float shrinkBgAlpha = mShrinkBgAlpha; 

        if (mAnimating) {
            long elipsedTime = System.currentTimeMillis() - mAnimStartTime;
            float input = Math.min((float)elipsedTime / ANIM_DURATION, 1.0f);

            /* reverse interpolator */
            if (mAnimState == AnimationState.ANIM_SHRINKING_OFF)
                input = 1 - input;

            /* Search Area Background Animation */
            float fract = mBackgroundInterpolator.getInterpolation(input);
            left  += (int)(fract * mBackgroundLength);
            right = mBackgroundLength - (int)((1 - fract) * mBackgroundExtra);
            bgAlpha = (1 - fract) * mBackgroundAlpha;

            /** Shrink Background animation **/
            fract = mShrinkBgInterpolator.getInterpolation(input);
            shrinkBgAlpha = (1 - fract) * mShrinkBgAlpha;

            invalidate();
            if (elipsedTime >= ANIM_DURATION)
                endAnimation();
        }
        else {
            if (mAnimState == AnimationState.ANIM_SHRINK_ON) {
                /* hide Search Area Background */
                bgAlpha = 0;
                left = right = 0;

                /* hide Shrink Background */
                shrinkBgAlpha = 0;
            }
        }

        if (mBackground != null) {
            mBackground.setAlpha((int)(bgAlpha * 255));
            mBackground.setBounds(left, 0, right, getHeight());
            mBackground.draw(canvas);
        }

        /* draw gradient background */
        if (mRootView.getBackground() != null) {
            Drawable d = mRootView.getBackground();
            d.setAlpha((int)(shrinkBgAlpha * 255));
        }
   }

    private void endAnimation () {
        post(mAnimEndRunnable);

        mSearchIcon.setClickable(true);
        if (mAnimState == AnimationState.ANIM_SHRINKING_OFF)
            mSearchIcon.getBackground().setTint(mSearchIconOffColor);

        /* clear input area */
        if (mAnimState == AnimationState.ANIM_SHRINKING_ON)
            mSearchView.setText(null);
    }

    public EditText getEditText () {
        return mSearchView;
    }

    public void setCallback (@Nullable Callback callback) {
        mCallback = callback;
    }

    public void setShrinkBackground (@DrawableRes Drawable drawable) {
        mRootView.setBackground(drawable);
    }

    public void setShrinkBackground (@IdRes int resId) {
        mRootView.setBackgroundResource(resId);
    }

    public AnimationState getCurentState () {
        return mAnimState;
    }

    /* zoom in searchIon, must >= 1.0 */
    public void setIconZoomFactor (float factor) {
       if (factor <= 1.0) {
           Log.e(TAG, "invalid ZoomFactor : " + factor);
           return;
       }
       mSearchIconZoomFactor = factor;
    }

    public void setIconGradientColor (int shrinkONColor, int shrinkOFFColor) {
        mSearchIconOffColor = shrinkOFFColor;
        mSearchIconOnColor  = shrinkONColor;
    }

    public interface Callback {
        public void onAnimationStart ();
        public void onAnimationEnd ();
    }
}
