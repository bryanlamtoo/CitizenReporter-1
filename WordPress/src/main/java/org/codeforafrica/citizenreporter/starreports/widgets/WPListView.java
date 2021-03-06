package org.codeforafrica.citizenreporter.starreports.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.ListView;

/**
 * ListView which reports scroll changes and offers additional properties
 */
public class WPListView extends ListView {
    private float mLastMotionY;
    private boolean mIsMoving;
    private int mInitialScrollCheckY;
    private static final int SCROLL_CHECK_DELAY = 100;

    // use this listener to detect when list is scrolled, even during a fling - note that
    // this may fire very frequently, so make sure code inside listener is optimized
    private ViewTreeObserver.OnScrollChangedListener mScrollChangedListener;

    // use this listener to detect simple up/down scrolling
    private ScrollDirectionListener mScrollDirectionListener;

    public WPListView(Context context) {
        super(context);
    }

    public WPListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WPListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /*
     * returns the vertical scroll position
     */
    public int getVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    public void setScrollDirectionListener(ScrollDirectionListener listener) {
        mScrollDirectionListener = listener;
    }

    public void setOnScrollChangedListener(ViewTreeObserver.OnScrollChangedListener listener) {
        mScrollChangedListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mScrollChangedListener != null) {
            mScrollChangedListener.onScrollChanged();
        }
    }

    public boolean isScrolledToTop() {
        return (getChildCount() == 0 || getVerticalScrollOffset() == 0);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScrollDirectionListener != null) {
            int action = event.getAction() & MotionEvent.ACTION_MASK;

            switch (action) {
                case MotionEvent.ACTION_MOVE :
                    if (mIsMoving) {
                        int yDiff = (int) (event.getY() - mLastMotionY);
                        if (yDiff < 0) {
                            mScrollDirectionListener.onScrollDown();
                        } else if (yDiff > 0) {
                            mScrollDirectionListener.onScrollUp();
                        }
                        mLastMotionY = event.getY();
                    } else {
                        mIsMoving = true;
                        mLastMotionY = event.getY();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (mIsMoving) {
                        mIsMoving = false;
                        startScrollCheck();
                    }
                    break;

                default :
                    mIsMoving = false;
                    break;
            }
        }

        return super.onTouchEvent(event);
    }

    private void startScrollCheck() {
        mInitialScrollCheckY = getScrollY();
        post(mScrollTask);
    }

    private final Runnable mScrollTask = new Runnable() {
        @Override
        public void run() {
            if (mInitialScrollCheckY == getScrollY()) {
                if (mScrollDirectionListener != null) {
                    mScrollDirectionListener.onScrollCompleted();
                }
            } else {
                mInitialScrollCheckY = getScrollY();
                postDelayed(mScrollTask, SCROLL_CHECK_DELAY);
            }
        }
    };

    /*
     * returns true if the listView can scroll up/down vertically
     */
    public boolean canScrollUp() {
        return canScrollVertically(-1);
    }
    public boolean canScrollDown() {
        return canScrollVertically(1);
    }
}
