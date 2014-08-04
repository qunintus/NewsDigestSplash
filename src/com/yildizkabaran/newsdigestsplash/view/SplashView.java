package com.yildizkabaran.newsdigestsplash.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import com.yildizkabaran.newsdigestsplash.BuildConfig;
import com.yildizkabaran.newsdigestsplash.R;

/**
 * A simple view class that displays a number of colorful circles rotating, then eventually the circles will merge
 * together and enlarge as a transparent hole
 * @author yildizkabaran
 *
 */
public class SplashView extends View {

  private static final String TAG = "SplashView";
  
  /**
   * A simple interface to listen to the state of the splash animation
   * @author yildizkabaran
   *
   */
  public static interface ISplashListener {
    public void onStart();
    public void onUpdate(float completionFraction);
    public void onEnd();
  }
  
  /**
   * A Context constructor is provided for creating the view by code. All other constructors use this constructor.
   * @param context
   */
  public SplashView(Context context){
    super(context);
    initialize();
  }

  /**
   * This constructor is redirected to the Context constructor.
   * @param context
   * @param attrs
   */
  public SplashView(Context context, AttributeSet attrs) {
    super(context, attrs);
    setupAttributes(attrs);
    initialize();
  }

  /**
   * This constructor is redirected to the Context constructor.
   * @param context
   * @param attrs
   */
  public SplashView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    setupAttributes(attrs);
    initialize();
  }
  
  /*** REMOVE THESE *****/
  private static final int COLOR_ORANGE = Color.rgb(255, 150, 0);
  private static final int COLOR_AQUA = Color.rgb(2, 209, 172);
  private static final int COLOR_YELLOW = Color.rgb(255, 210, 0);
  private static final int COLOR_BLUE = Color.rgb(0, 198, 255);
  private static final int COLOR_GREEN = Color.rgb(0, 224, 153);
  private static final int COLOR_PINK = Color.rgb(255, 56, 145);
  /********/
  
  public static final boolean DEFAULT_REMOVE_FROM_PARENT_ON_END = true;
  public static final int DEFAULT_RADIUS = 30; //dp
  public static final int DEFAULT_CIRCLE_RADIUS = 6; //dp
  public static final int DEFAULT_SPLASH_BG_COLOR = Color.rgb(238, 236, 226);
  public static final long DEFAULT_ROTATION_DURATION = 1200;
  
  private boolean mRemoveFromParentOnEnd = true; // a flag for removing the view from its parent once the animation is over
  private float mRotationRadius;
  private float mCircleRadius;
  private int[] mColors = {COLOR_ORANGE, COLOR_AQUA, COLOR_YELLOW, COLOR_BLUE, COLOR_GREEN, COLOR_PINK};
  private int mBgColor;
  
  private float mHoleRadius = 0F;
  private float mCurrentRotationAngle = 0F;
  private float mCurrentRotationRadius;
  private float mCurrentCircleRadius;
  private int mSingleCircleColor;
  private long mRotationDuration = DEFAULT_ROTATION_DURATION;
  
  private SplashState mState = null;
  
  // cache the objects so that we don't have to allocate during onDraw
  private Paint mPaint = new Paint();
  private Paint mPaintBackground = new Paint();
  
  // cache some numeric calculations
  private float mCenterX;
  private float mCenterY;
  private float mDiagonalDist;
  
  /**
   * Setup the custom attributes from XML
   * @param attrs
   */
  private void setupAttributes(AttributeSet attrs) {
    Context context = getContext();

    TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.NewsDigestSplashView);

    int numAttrs = a.getIndexCount();
    for (int i = 0; i < numAttrs; ++i) {
      int attr = a.getIndex(i);
      switch (attr) {
      case R.styleable.NewsDigestSplashView_removeFromParentOnEnd:
        setRemoveFromParentOnEnd(a.getBoolean(i, DEFAULT_REMOVE_FROM_PARENT_ON_END));
        break;
      }
    }
    a.recycle();
  }
  
  /**
   * Initialized the view properties. No much is done in this method since most variables already have set defaults
   */
  private void initialize(){
    setBackgroundColor(Color.TRANSPARENT);
    
    float density = getResources().getDisplayMetrics().density;
    setRotationRadius(density * DEFAULT_RADIUS);
    setCircleRadius(density * DEFAULT_CIRCLE_RADIUS);
    
    mPaint.setAntiAlias(true);
    
    setSplashBackgroundColor(DEFAULT_SPLASH_BG_COLOR);
    mPaintBackground.setStyle(Paint.Style.STROKE);
    mPaintBackground.setAntiAlias(true);
  }
  
  public void setRotationRadius(float rotationRadius){
    mRotationRadius = rotationRadius;
  }
  
  public void setCircleRadius(float circleRadius){
    mCircleRadius = circleRadius;
  }
  
  public void setSplashBackgroundColor(int bgColor){
    mBgColor = bgColor;
    mPaintBackground.setColor(mBgColor);
  }
  
  /**
   * Set the flag to remove or keep the view after the animation is over. This is set to true by default. The view must be inside a ViewManager
   * (or ViewParent) for this to work. Otherwise, the view will not be removed and a warning log will be produced.
   * @param shouldRemove
   */
  public void setRemoveFromParentOnEnd(boolean shouldRemove){
    mRemoveFromParentOnEnd = shouldRemove;
  }
  
  /**
   * Starts the disappear animation. If a listener is provided it will notify the listener on animation events
   * @param listener
   */
  public void splashAndDisappear(final ISplashListener listener){
    // create an animator from scale 1 to max
    final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
    
    // add an update listener so that we draw the view on each update
    animator.addUpdateListener(new AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        // invalidate the view so that it gets redraw if it needs to be
        invalidate();
        
        // notify the listener if set
        // for some reason this animation can run beyond 100%
        if(listener != null){
          listener.onUpdate((float) animation.getCurrentPlayTime() / animation.getDuration());
        }
      }
    });
    
    // add a listener for the general animation events, use the AnimatorListenerAdapter so that we don't clutter the code
    animator.addListener(new AnimatorListenerAdapter(){
      @Override
      public void onAnimationStart(Animator animation){
        // notify the listener of animation start (if listener is set)
        if(listener != null){
          listener.onStart();
        }
      }
      
      @Override
      public void onAnimationEnd(Animator animation){
        // check if we need to remove the view on animation end
        if(mRemoveFromParentOnEnd){
          // get the view parent
          ViewParent parent = getParent();
          // check if a parent exists and that it implements the ViewManager interface
          if(parent != null && parent instanceof ViewManager){
            ViewManager viewManager = (ViewManager) parent;
            // remove the view from its parent
            viewManager.removeView(SplashView.this);
          } else if(BuildConfig.DEBUG) {
            // even though we had to remove the view we either don't have a parent, or the parent does not implement the method
            // necessary to remove the view, therefore create a warning log (but only do this if we are in DEBUG mode)
            Log.w(TAG, "splash view not removed after animation ended because no ViewManager parent was found");
          }
        }
        
        // notify the listener of animation end (if listener is set)
        if(listener != null){
          listener.onEnd();
        }
      }
    });
    
    // start the animation using post so that the animation does not start if the view is not in foreground
    post(new Runnable(){
      @Override
      public void run(){
        // start the animation in reverse to get the desired effect from the interpolator
        animator.start();
      }
    });
  }
  
  @Override
  protected void onSizeChanged (int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    
    mCenterX = w / 2F;
    mCenterY = h / 2F;
    mDiagonalDist = (float) Math.sqrt(w * w + h * h);
  }
  
  private void handleFirstDraw(){
    mState = new RotationState();
    
    mCurrentRotationAngle = 0F;
    mCurrentRotationRadius = mRotationRadius;
    mCurrentCircleRadius = mCircleRadius;
  }
  
  @Override
  protected void onDraw(Canvas canvas){
    if(mState == null){
      handleFirstDraw();
    }
    mState.drawState(canvas);
  }
  
  private void drawBackground(Canvas canvas){
    if(mHoleRadius > 0F){
      float strokeWidth = mDiagonalDist - mHoleRadius;
      float circleRadius = mHoleRadius + strokeWidth / 2;
      
      mPaintBackground.setStrokeWidth(strokeWidth);
      canvas.drawCircle(mCenterX, mCenterY, circleRadius, mPaintBackground);
    } else {
      canvas.drawColor(mBgColor);
    }
  }
  
  private void drawCircles(Canvas canvas){
    int numCircles = mColors.length;
    float rotationAngle = (float) (2 * Math.PI / numCircles);
    for(int i=0; i<numCircles; ++i){
      double angle = mCurrentRotationAngle + (i * rotationAngle);
      double circleX = mCenterX + mCurrentRotationRadius * Math.sin(angle);
      double circleY = mCenterY - mCurrentRotationRadius * Math.cos(angle);
      
      mPaint.setColor(mColors[i]);
      canvas.drawCircle((float) circleX, (float) circleY, mCurrentCircleRadius, mPaint);
    }
  }
  
  private void drawSingleCircle(Canvas canvas){
    mPaint.setColor(mSingleCircleColor);
    canvas.drawCircle(mCenterX, mCenterY, mCurrentCircleRadius, mPaint);
  }
  
  private abstract class SplashState {
    public abstract void drawState(Canvas canvas);
  }
  
  private class RotationState extends SplashState {
    private ValueAnimator mAnimator;
    
    public RotationState(){
      mAnimator = ValueAnimator.ofFloat(0, (float) (Math.PI * 2));
      mAnimator.setDuration(mRotationDuration);
      mAnimator.setInterpolator(new LinearInterpolator());
      mAnimator.addUpdateListener(new AnimatorUpdateListener(){
        @Override
        public void onAnimationUpdate(ValueAnimator animator) {
          mCurrentRotationAngle = (Float) animator.getAnimatedValue();
          invalidate();
        }
      });
      mAnimator.setRepeatCount(ValueAnimator.INFINITE);
      mAnimator.setRepeatMode(ValueAnimator.RESTART);
      mAnimator.start();
    }
    
    @Override
    public void drawState(Canvas canvas){
      drawBackground(canvas);
      drawCircles(canvas);
    }
  }
  
//  private class MergingState extends SplashState {
//    private Interpolator mInterpolator = new OvershootInterpolator(6F);
//    private long mCurrentValue = MERGING_DURATION;
//
//    @Override
//    public void drawState(Canvas canvas){
//      drawBackground(canvas);
//      drawCircles(canvas);
//    }
//    
//    public void updateState(long millisElapsed) {
//      mCurrentValue -= millisElapsed;
//      if(mCurrentValue <= 0){
//        mCurrentRadius = 0;
//        mState = new SingularityState();
//      } else {
//        mCurrentRadius = mRadius * mInterpolator.getInterpolation((float) mCurrentValue / MERGING_DURATION);
//      }
//    }
//  }
//  
//  private class SingularityState extends SplashState {
//    private Interpolator mInterpolator = new OvershootInterpolator(6F);
//    private long mCurrentValue = SINGULARITY_DURATION;
//
//    @Override
//    public void drawState(Canvas canvas){
//      drawBackground(canvas);
//      drawSingleCircle(canvas);
//    }
//    
//    public void updateState(long millisElapsed) {
//      mCurrentValue -= millisElapsed;
//      if(mCurrentValue <= 0){
//        mCurrentCircleRadius = 0;
//        mState = new ExpandingState();
//      } else {
//        mCurrentCircleRadius = mCircleRadius * mInterpolator.getInterpolation((float) mCurrentValue / SINGULARITY_DURATION);
//      }
//    }
//  }
//  
//  private class ExpandingState extends SplashState {
//    private Interpolator mInterpolator = new DecelerateInterpolator();
//    private long mCurrentValue = 0;
//    
//    @Override
//    public void drawState(Canvas canvas){
//      drawBackground(canvas);
//    }
//    
//    public void updateState(long millisElapsed){
//      mCurrentValue += millisElapsed;
//      mHoleRadius = mDiagonalDist * mInterpolator.getInterpolation((float) mCurrentValue / EXPAND_DURATION);
//    }
//  }
}
