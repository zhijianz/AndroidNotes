> 关于一个Github上比较热门的BottomBar实现的源码分析，项目地址https://github.com/roughike/BottomBar

# 基本使用

1. 首先需要在res/menu中添加BottomBar需要实现的项目

```xml
<!-- res/menu -->
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/item_recent"
        android:icon="@drawable/ic_update_white_24dp"
        android:title="Recent"/>
    <item
        android:id="@+id/item_location"
        android:icon="@drawable/ic_location_on_white_24dp"
        android:title="Location"/>
    <item
        android:id="@+id/item_favorite"
        android:icon="@drawable/ic_favorite_white_24dp"
        android:title="Favorite"/>
    <item
        android:id="@+id/item_dinning"
        android:icon="@drawable/ic_local_dining_white_24dp"
        android:title="Dinning"/>
</menu>
```

2. 在Acitivity中创建并实现BottomBar的相关接口

```java
BottomBar mBottomBar = BottomBar.attach(this, savedInstanceState);
        mBottomBar.setItemsFromMenu(R.menu.menu_bottom_bar, new OnMenuTabSelectedListener() {
            @Override
            public void onMenuItemSelected(@IdRes int menuItemId) {
                switch (menuItemId){
                    case R.id.item_location:
                        Snackbar.make(mContainer, "Location Selected", Snackbar.LENGTH_LONG).show();
                        break;
                    case R.id.item_recent:
                        Snackbar.make(mContainer, "Recent Selected", Snackbar.LENGTH_LONG).show();
                        break;
                    case R.id.item_favorite:
                        Snackbar.make(mContainer, "Favorite Selected", Snackbar.LENGTH_LONG).show();
                }
            }
        });
```

# 源码点

1. 使用Menu设置每个项

```java
public void setItemsFromMenu(@MenuRes int menuRes, OnMenuTabClickListener listener) {
    clearItems();
    mItems = MiscUtils.inflateMenuFromResource((Activity) getContext(), menuRes);
    mMenuListener = listener;
    updateItems(mItems);

    if (mItems != null && mItems.length > 0
            && mItems instanceof BottomBarTab[]) {
        listener.onMenuTabSelected(((BottomBarTab) mItems[mCurrentTabPosition]).id);
    }
}

protected static BottomBarTab[] inflateMenuFromResource(Activity activity, @MenuRes int menuRes) {
    // A bit hacky, but hey hey what can I do
    PopupMenu popupMenu = new PopupMenu(activity, null);
    Menu menu = popupMenu.getMenu();
    activity.getMenuInflater().inflate(menuRes, menu);

    int menuSize = menu.size();
    BottomBarTab[] tabs = new BottomBarTab[menuSize];

    for (int i = 0; i < menuSize; i++) {
        MenuItem item = menu.getItem(i);
        BottomBarTab tab = new BottomBarTab(item.getIcon(),
                String.valueOf(item.getTitle()));
        tab.id = item.getItemId();
        tabs[i] = tab;
    }

    return tabs;
}

```

在配置BottomBar的时候，是通过Menu的xml文件类实现的。在其内部实现的原理如同上面的代码所展示的那样，通过Android本身提供于解析Menu的API来解析配置项目，从而获取到配置的内容，在这里可以算是一个小技巧。

2. 动画效果实现

BottomBar点击的动画效果可以分解成三个部分，一个是Android Material Design 主题自带的点击响应波纹动画，第二个是被选中项目动画，最后一个是原先被选中项对应的动画。第一种不需要分析，剩下两种看源码。

```java
// animation for select
ViewPropertyAnimatorCompat titleAnimator = ViewCompat.animate(title)
                    .setDuration(ANIMATION_DURATION)
                    .scaleX(1)
                    .scaleY(1);
titleAnimator.alpha(1.0f);

ValueAnimator paddingAnimator = ValueAnimator.ofInt(start, end);
paddingAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        icon.setPadding(icon.getPaddingLeft(), (Integer) animation.getAnimatedValue(),
            icon.getPaddingRight(), icon.getPaddingBottom());
    }
});
paddingAnimator.setDuration(duration);
paddingAnimator.start();
ViewCompat.animate(icon)
        .setDuration(ANIMATION_DURATION)
        .alpha(1.0f)
        .start();

// animator for unselect
float scale = mIsShiftingMode ? 0 : 0.86f;
ViewPropertyAnimatorCompat titleAnimator = ViewCompat.animate(title)
                    .setDuration(ANIMATION_DURATION)
                    .scaleX(scale)
                    .scaleY(scale);
titleAnimator.alpha(0);
ViewCompat.animate(icon)
        .setDuration(ANIMATION_DURATION)
        .alpha(mTabAlpha)
        .start();

// resize tab width
ValueAnimator animator = ValueAnimator.ofFloat(start, end);
animator.setDuration(150);
animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
    @Override
    public void onAnimationUpdate(ValueAnimator animator) {
        ViewGroup.LayoutParams params = tab.getLayoutParams();
        if (params == null) return;

        params.width = Math.round((float) animator.getAnimatedValue());
        tab.setLayoutParams(params);
    }
});
animator.start();                      
```

在产生Selected Change的时候，会根据设计规范说明的参数对对应的项目执行动画操作

3. 自动隐藏

自动隐藏部分涉及到CoordinationLayout某些特性的使用，所以会在后续对CoordinationLayout有一定了解之后再去详细解释，这里真是按照源码进行一定的流程解读

```java
// 设置Behavior
if (mIsShy && !mIsTabletMode) {
    getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
        @SuppressWarnings("deprecation")
        @Override
        public void onGlobalLayout() {
            if (!mShyHeightAlreadyCalculated) {
                ((CoordinatorLayout.LayoutParams) getLayoutParams())
                        .setBehavior(new BottomNavigationBehavior(getOuterContainer().getHeight(), 0, isShy(), mIsTabletMode));
            }

            ViewTreeObserver obs = getViewTreeObserver();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                obs.removeOnGlobalLayoutListener(this);
            } else {
                obs.removeGlobalOnLayoutListener(this);
            }
        }
    });
}

// 自定义Behavior

public class BottomNavigationBehavior<V extends View> extends VerticalScrollingBehavior<V> {
    private static final Interpolator INTERPOLATOR = new LinearOutSlowInInterpolator();
    private final int mBottomNavHeight;
    private final int mDefaultOffset;
    private boolean isShy = false;
    private boolean isTablet = false;

    private ViewPropertyAnimatorCompat mTranslationAnimator;
    private boolean hidden = false;
    private int mSnackbarHeight = -1;
    private final BottomNavigationWithSnackbar mWithSnackBarImpl = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? new LollipopBottomNavWithSnackBarImpl() : new PreLollipopBottomNavWithSnackBarImpl();
    private boolean mScrollingEnabled = true;

    public BottomNavigationBehavior(int bottomNavHeight, int defaultOffset, boolean shy, boolean tablet) {
        mBottomNavHeight = bottomNavHeight;
        mDefaultOffset = defaultOffset;
        isShy = shy;
        isTablet = tablet;
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, V child, View dependency) {
        mWithSnackBarImpl.updateSnackbar(parent, dependency, child);
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public void onNestedVerticalOverScroll(CoordinatorLayout coordinatorLayout, V child, @ScrollDirection int direction, int currentOverScroll, int totalOverScroll) {
    }

    @Override
    public void onDependentViewRemoved(CoordinatorLayout parent, V child, View dependency) {
        updateScrollingForSnackbar(dependency, true);
        super.onDependentViewRemoved(parent, child, dependency);
    }

    private void updateScrollingForSnackbar(View dependency, boolean enabled) {
        if (!isTablet && dependency instanceof Snackbar.SnackbarLayout) {
            mScrollingEnabled = enabled;
        }
    }

    @Override
    public boolean onDependentViewChanged(CoordinatorLayout parent, V child, View dependency) {
        updateScrollingForSnackbar(dependency, false);
        return super.onDependentViewChanged(parent, child, dependency);
    }

    @Override
    public void onDirectionNestedPreScroll(CoordinatorLayout coordinatorLayout, V child, View target, int dx, int dy, int[] consumed, @ScrollDirection int scrollDirection) {
        handleDirection(child, scrollDirection);
    }

    private void handleDirection(V child, int scrollDirection) {
        if (!mScrollingEnabled) return;
        if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_DOWN && hidden) {
            hidden = false;
            animateOffset(child, mDefaultOffset);
        } else if (scrollDirection == ScrollDirection.SCROLL_DIRECTION_UP && !hidden) {
            hidden = true;
            animateOffset(child, mBottomNavHeight + mDefaultOffset);
        }
    }

    @Override
    protected boolean onNestedDirectionFling(CoordinatorLayout coordinatorLayout, V child, View target, float velocityX, float velocityY, @ScrollDirection int scrollDirection) {
        handleDirection(child, scrollDirection);
        return true;
    }

    private void animateOffset(final V child, final int offset) {
        ensureOrCancelAnimator(child);
        mTranslationAnimator.translationY(offset).start();
    }

    private void ensureOrCancelAnimator(V child) {
        if (mTranslationAnimator == null) {
            mTranslationAnimator = ViewCompat.animate(child);
            mTranslationAnimator.setDuration(300);
            mTranslationAnimator.setInterpolator(INTERPOLATOR);
        } else {
            mTranslationAnimator.cancel();
        }
    }


    public void setHidden(@NonNull  V view, boolean bottomLayoutHidden) {
        if (!bottomLayoutHidden && hidden) {
            animateOffset(view, mDefaultOffset);
        } else if (bottomLayoutHidden && !hidden) {
            animateOffset(view,  mBottomNavHeight + mDefaultOffset);
        }
        hidden = bottomLayoutHidden;
    }


    public static <V extends View> BottomNavigationBehavior<V> from(@NonNull V view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof CoordinatorLayout.LayoutParams)) {
            throw new IllegalArgumentException("The view is not a child of CoordinatorLayout");
        }
        CoordinatorLayout.Behavior behavior = ((CoordinatorLayout.LayoutParams) params)
                .getBehavior();
        if (!(behavior instanceof BottomNavigationBehavior)) {
            throw new IllegalArgumentException(
                    "The view is not associated with BottomNavigationBehavior");
        }
        return (BottomNavigationBehavior<V>) behavior;
    }

    private interface BottomNavigationWithSnackbar {
        void updateSnackbar(CoordinatorLayout parent, View dependency, View child);
    }


    private class PreLollipopBottomNavWithSnackBarImpl implements BottomNavigationWithSnackbar {

        @Override
        public void updateSnackbar(CoordinatorLayout parent, View dependency, View child) {
            if (!isTablet && isShy && dependency instanceof Snackbar.SnackbarLayout) {
                if (mSnackbarHeight == -1) {
                    mSnackbarHeight = dependency.getHeight();
                }
                if (ViewCompat.getTranslationY(child) != 0) return;
                int targetPadding = mBottomNavHeight + mSnackbarHeight - mDefaultOffset;

                ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) dependency.getLayoutParams();
                layoutParams.bottomMargin = targetPadding;
                child.bringToFront();
                child.getParent().requestLayout();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    ((View) child.getParent()).invalidate();
                }

            }
        }
    }

    private class LollipopBottomNavWithSnackBarImpl implements BottomNavigationWithSnackbar {

        @Override
        public void updateSnackbar(CoordinatorLayout parent, View dependency, View child) {
            if (!isTablet && isShy && dependency instanceof Snackbar.SnackbarLayout) {
                if (mSnackbarHeight == -1) {
                    mSnackbarHeight = dependency.getHeight();
                }
                if (ViewCompat.getTranslationY(child) != 0) return;
                int targetPadding = (mSnackbarHeight + mBottomNavHeight - mDefaultOffset);
                dependency.setPadding(dependency.getPaddingLeft(),
                        dependency.getPaddingTop(), dependency.getPaddingRight(), targetPadding
                );
            }
        }
    }
}

```

在使用自动隐藏功能的时候必须要使用`CoordinatorLayout`作为整个界面的最外层容器，利用`CoordinatorLayout.Behavior`来完成`BottomBar`和容器内容进行滚动的控件之间的联系，而关于具体`Behavior`的实现可以参照上面的源代码
