> 在学习window的相关知识的时候会知道，在android的应用程序里面，展示的交互界面由View来呈现，而View则是包含在一个Window的抽象逻辑里面，由其各种回调对View进行相应的操作。这条规则最明显的地方体现在Activity或者是Dialog创建的时候都会创建一个PhoneWindow对象，然后在这个window里面增加显示内容的View。这篇文章的起源是研究创建Dialog时必须要进行的一些去除黑框，title之类的定义操作，深入学习之后发现这些内容不仅仅适用于Dialog，而是所有使用PhoneWindow的界面对象。

# 常见用户界面结构

```{viz}
digraph gui_structure{
  subgraph cluster0 {
        node[style=filled, color=white];  //定义子图中的节点的样式
        color=red; //定义子图的填充色
        label = "PhoneWindow"

        subgraph cluster0 {
          node[style=filled, color=white];  //定义子图中的节点的样式
          color=red; //定义子图的填充色
          label = "DecorView"
          subgraph cluster0 {
            node[style=filled, color=white];  //定义子图中的节点的样式
            color=red; //定义子图的填充色
            label = "contentParent"
            rankdir = TB;
            subgraph{
              node[style=filled, color=white];  //定义子图中的节点的样式
              color=red; //定义子图的填充色
              Title;
              }
              subgraph {
                node[style=filled, color=white];  //定义子图中的节点的样式
                color=red; //定义子图的填充色
                label = "DecorView"
                "androdi.R.id.content"
                }
            }
          }
     }
}
```

在常见的界面构成中，元素之间的包含关系如上图所描述的那样。这些内容在各种关于View的学习资料中都会由详细的介绍，在这里就直接略过了。在这里再次画出来是为了突出`PhoneWindow`的存在以及在之后的关于的`contentParent`会有一些想法上的尝试。


# 关于Dialog的定制

```xml
<style name="CustomWindowStyle">
    <!--是否悬浮在现有界面上层，如果false会替换现有-->
    <item name="android:windowIsFloating">true</item>
    <!--软键盘的显示模式-->
    <item name="android:windowSoftInputMode">adjustPan</item>
    <!--是否出现蒙层-->
    <item name="android:backgroundDimEnabled">false</item>
    <!--蒙层透明度-->
    <item name="android:backgroundDimAmount">0.5</item>
    <!--window.DecorView的背景，mDecor.setWindowBackground-->
    <item name="android:windowBackground">@color/transparent</item>
    <!--window的前景，mDecor.setWindowFrame-->
    <item name="android:windowFrame">@color/transparent</item>
</style>
```

上面的样式用于定制Dialog显示的外观，这些属性抽取自`PhoneWindow.generateLayout`函数，并且丢弃了其中一些版本适配存在问题的属性。在最初从网上发现这种定制方式的的时候心里是比较疑惑的，因为不管怎么样去调整关键词去进行搜索都只会出现使用样式定制的解决方案，而对于这些样式是如何起到改变对话框默认外观的过程并没有任何的解释和介绍，所以自己尝试通过对`Dialog`的源码分析挖出了这篇文章。

# Dialog View的添加流程

```{puml}
actor client
participant Dialog
participant PhoneWindow

activate client
client -> Dialog : new Dialog(context, theme, createContextThemeWrapper)
activate Dialog
Dialog --> Dialog : new ContextThemeWrapper
Dialog --> Dialog : PolicyManager.makeNewWindow
Dialog --> client : dialog
deactivate Dialog

client -> Dialog : dialog.setContentView
activate Dialog
Dialog -> PhoneWindow : window.setContentView
deactivate Dialog

client -> Dialog : dialog.show
activate Dialog
Dialog -> PhoneWindow : window.getDecorView
activate PhoneWindow
PhoneWindow --> PhoneWindow : installDecor
activate PhoneWindow
PhoneWindow --> PhoneWindow : generateDecor
PhoneWindow --> PhoneWindow : generateLayout
deactivate PhoneWindow
PhoneWindow --> Dialog : DecorView
deactivate PhoneWindow
Dialog --> client : set DecorView visible
deactivate Dialog
deactivate client
```

上面的流程图展示的是对话框如何再显示的过程中将要显示的View创建出来并且添加到要显示的`PhoneWindow`中。下面的分析会依据这个调用流程尝试解释在这个显示的过程中，对话框的显示的样式是怎样被定义的好的样式所影响的，并且尝试将这个分析推广到所有使用`PhoneWindow`的组件中，比如自定`Activity`的样式。

## Dialog 构造函数

在自定义对话框外观的时候，需要调用到`Dialog`下面的这个构造函数

```java
Dialog(Context context, int theme, boolean createContextThemeWrapper) {
    if (createContextThemeWrapper) {
        if (theme == 0) {
            TypedValue outValue = new TypedValue();
            context.getTheme().resolveAttribute(com.android.internal.R.attr.dialogTheme,
                    outValue, true);
            theme = outValue.resourceId;
        }
        mContext = new ContextThemeWrapper(context, theme);
    } else {
        mContext = context;
    }

    mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    Window w = PolicyManager.makeNewWindow(mContext);
    mWindow = w;
    w.setCallback(this);
    w.setOnWindowDismissedCallback(this);
    w.setWindowManager(mWindowManager, null, null);
    w.setGravity(Gravity.CENTER);
    mListenersHandler = new ListenersHandler(this);
}
```

在这个构造函数中，自定义对话框样式的`theme`传入之后会用于创建对应的`Context`,然后在之后的步骤中通过这个`Context`取出里面包含的属性内容；另外的一点是调用`PolicyManager.makeNewWindow`（实际的实现是Policy.java）创建了`PhoneWindow`对象。再往下面的流程就是一个标准的窗口内容的管理流程。后面的代码用来添加各种`Window`的回调接口，在使用`Window`的组件都需要去实现这一步骤。

## PhoneWindow.setContentView

在新创建了一个对话框之后，通常的实现操作会调用`dialog.setContentView`来设置自定义的显示的内容，这个操作的实质是调用已经创建的好的`Window`对象将要显示的内容添加到其中；其实，就算是没有实质的去调用`dialog.setContentView`，从后面的调用流程中我们可以看到尝试从`Window`中获取要显示的`DecorView`的时候还是会去走一样的内容创建流程，区别只是在于那个时候创建出来的内容并没有我们期望加入的自定义内容，但是对于我们这篇文章所讨论的主旨并没有影响，所以在这里就直接简单的带过，后面在详细介绍`DecorView的创建流程`。

```java
public void setContentView(View view, ViewGroup.LayoutParams params) {
        // Note: FEATURE_CONTENT_TRANSITIONS may be set in the process of installing the window
        // decor, when theme attributes and the like are crystalized. Do not check the feature
        // before this happens.
        if (mContentParent == null) {
            installDecor();
        } else if (!hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            mContentParent.removeAllViews();
        }

        if (hasFeature(FEATURE_CONTENT_TRANSITIONS)) {
            view.setLayoutParams(params);
            final Scene newScene = new Scene(mContentParent, view);
            transitionTo(newScene);
        } else {
            mContentParent.addView(view, params);
        }
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }
```

## Dialog 的显示

对话框创建完成之后，调用`dialog.show`会将其显示出来，本质上就如上面一直在强调的那样，显示的内容实质的就是`DecorView`,这个显示的操作对于`Dialog`来说也只是控制`DecorView`的可见性以及一些属性和相关回调的使用而已。而另一方面，`Dialog.mDecorView`的来源是其创建的那个`PhoneWindow`对象，调用其`getDecorView`获得的，所以尝试从这个函数入手，研究`DecorView`的创建过程以及自定义属性对其的影响。

```java
public final View getDecorView() {
      if (mDecor == null) {
          installDecor();
      }
      return mDecor;
  }
```

上面的代码就是`PhoneWindow.getDecorView`的实现，很简单的几行代码，因为所有的创建工作都在`installDecor`里完成，所以到这里只能够继续往下面挖。

```java
private void installDecor() {
        if (mDecor == null) {
            mDecor = generateDecor();
            ...
        }
        if (mContentParent == null) {
            mContentParent = generateLayout(mDecor);
            ...
    }
```

从`installDecor`中抽取了两句代码，关于`DecorView/ContentParent`的创建入口。`DecorView`继承自`FrameLayout`,在这个创建的函数里面也只是简单的创建了这个对象，所以也没有什么值得深入探讨的内容，在这里抽出来只是为了说明`DecorView`的来源。那么关键的过程就应该是怎么去创建`ContentParent`,关于这个对象在窗口显示体系中扮演的角色可以参照文章开始所画的那张结构图，概述的理解就是这个容器是存在于常说的`DecorView`和`android.R.id.content`直接的一个过渡层，这种设计的目的是为不同的主题加载不同的预定义布局生成窗口的基础视图，这些在等下的代码中都会有描述。

```java
protected ViewGroup generateLayout(DecorView decor) {
        // Apply data from current theme.

        TypedArray a = getWindowStyle();
		...
		/**
		* Window_windowIsFloating，dialog内容展示的方式
		* false: 替代原有的内容进行展示
		* true: 浮在原有内容上层进行展示
		*/
        mIsFloating = a.getBoolean(R.styleable.Window_windowIsFloating, false);
        int flagsToUpdate = (FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_INSET_DECOR)
                & (~getForcedWindowFlags());
        if (mIsFloating) {
            setLayout(WRAP_CONTENT, WRAP_CONTENT);
            setFlags(0, flagsToUpdate);
        } else {
            setFlags(FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_INSET_DECOR, flagsToUpdate);
        }

        if (a.getBoolean(R.styleable.Window_windowNoTitle, false)) {
            requestFeature(FEATURE_NO_TITLE);
        } else if (a.getBoolean(R.styleable.Window_windowActionBar, false)) {
            // Don't allow an action bar if there is no title.
            requestFeature(FEATURE_ACTION_BAR);
        }

        if (a.getBoolean(R.styleable.Window_windowActionBarOverlay, false)) {
            requestFeature(FEATURE_ACTION_BAR_OVERLAY);
        }

        if (a.getBoolean(R.styleable.Window_windowActionModeOverlay, false)) {
            requestFeature(FEATURE_ACTION_MODE_OVERLAY);
        }

		// 应该是只支持kikat_watch
        if (a.getBoolean(R.styleable.Window_windowSwipeToDismiss, false)) {
            requestFeature(FEATURE_SWIPE_TO_DISMISS);
        }

        if (a.getBoolean(R.styleable.Window_windowFullscreen, false)) {
            setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN & (~getForcedWindowFlags()));
        }

        if (a.getBoolean(R.styleable.Window_windowTranslucentStatus,
                false)) {
            setFlags(FLAG_TRANSLUCENT_STATUS, FLAG_TRANSLUCENT_STATUS
                    & (~getForcedWindowFlags()));
        }

        if (a.getBoolean(R.styleable.Window_windowTranslucentNavigation,
                false)) {
            setFlags(FLAG_TRANSLUCENT_NAVIGATION, FLAG_TRANSLUCENT_NAVIGATION
                    & (~getForcedWindowFlags()));
        }

        if (a.getBoolean(R.styleable.Window_windowOverscan, false)) {
            setFlags(FLAG_LAYOUT_IN_OVERSCAN, FLAG_LAYOUT_IN_OVERSCAN&(~getForcedWindowFlags()));
        }

        if (a.getBoolean(R.styleable.Window_windowShowWallpaper, false)) {
            setFlags(FLAG_SHOW_WALLPAPER, FLAG_SHOW_WALLPAPER&(~getForcedWindowFlags()));
        }

        if (a.getBoolean(R.styleable.Window_windowEnableSplitTouch,
                getContext().getApplicationInfo().targetSdkVersion
                        >= android.os.Build.VERSION_CODES.HONEYCOMB)) {
            setFlags(FLAG_SPLIT_TOUCH, FLAG_SPLIT_TOUCH&(~getForcedWindowFlags()));
        }

        a.getValue(R.styleable.Window_windowMinWidthMajor, mMinWidthMajor);
        a.getValue(R.styleable.Window_windowMinWidthMinor, mMinWidthMinor);
        if (a.hasValue(R.styleable.Window_windowFixedWidthMajor)) {
            if (mFixedWidthMajor == null) mFixedWidthMajor = new TypedValue();
            a.getValue(R.styleable.Window_windowFixedWidthMajor,
                    mFixedWidthMajor);
        }
        if (a.hasValue(R.styleable.Window_windowFixedWidthMinor)) {
            if (mFixedWidthMinor == null) mFixedWidthMinor = new TypedValue();
            a.getValue(R.styleable.Window_windowFixedWidthMinor,
                    mFixedWidthMinor);
        }
        if (a.hasValue(R.styleable.Window_windowFixedHeightMajor)) {
            if (mFixedHeightMajor == null) mFixedHeightMajor = new TypedValue();
            a.getValue(R.styleable.Window_windowFixedHeightMajor,
                    mFixedHeightMajor);
        }
        if (a.hasValue(R.styleable.Window_windowFixedHeightMinor)) {
            if (mFixedHeightMinor == null) mFixedHeightMinor = new TypedValue();
            a.getValue(R.styleable.Window_windowFixedHeightMinor,
                    mFixedHeightMinor);
        }
        if (a.getBoolean(R.styleable.Window_windowContentTransitions, false)) {
            requestFeature(FEATURE_CONTENT_TRANSITIONS);
        }
        if (a.getBoolean(R.styleable.Window_windowActivityTransitions, false)) {
            requestFeature(FEATURE_ACTIVITY_TRANSITIONS);
        }

        final WindowManager windowService = (WindowManager) getContext().getSystemService(
                Context.WINDOW_SERVICE);
        if (windowService != null) {
            final Display display = windowService.getDefaultDisplay();
            final boolean shouldUseBottomOutset =
                    display.getDisplayId() == Display.DEFAULT_DISPLAY
                            || (getForcedWindowFlags() & FLAG_FULLSCREEN) != 0;
            if (shouldUseBottomOutset && a.hasValue(R.styleable.Window_windowOutsetBottom)) {
                if (mOutsetBottom == null) mOutsetBottom = new TypedValue();
                a.getValue(R.styleable.Window_windowOutsetBottom,
                        mOutsetBottom);
            }
        }

        final Context context = getContext();
        final int targetSdk = context.getApplicationInfo().targetSdkVersion;
        final boolean targetPreHoneycomb = targetSdk < android.os.Build.VERSION_CODES.HONEYCOMB;
        final boolean targetPreIcs = targetSdk < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
        final boolean targetPreL = targetSdk < android.os.Build.VERSION_CODES.LOLLIPOP;
        final boolean targetHcNeedsOptions = context.getResources().getBoolean(
                R.bool.target_honeycomb_needs_options_menu);
        final boolean noActionBar = !hasFeature(FEATURE_ACTION_BAR) || hasFeature(FEATURE_NO_TITLE);

        if (targetPreHoneycomb || (targetPreIcs && targetHcNeedsOptions && noActionBar)) {
            addFlags(WindowManager.LayoutParams.FLAG_NEEDS_MENU_KEY);
        } else {
            clearFlags(WindowManager.LayoutParams.FLAG_NEEDS_MENU_KEY);
        }

        // Non-floating windows on high end devices must put up decor beneath the system bars and
        // therefore must know about visibility changes of those.
        if (!mIsFloating && ActivityManager.isHighEndGfx()) {
            if (!targetPreL && a.getBoolean(
                    R.styleable.Window_windowDrawsSystemBarBackgrounds,
                    false)) {
                setFlags(FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                        FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS & ~getForcedWindowFlags());
            }
        }
        if (!mForcedStatusBarColor) {
            mStatusBarColor = a.getColor(R.styleable.Window_statusBarColor, 0xFF000000);
        }
        if (!mForcedNavigationBarColor) {
            mNavigationBarColor = a.getColor(R.styleable.Window_navigationBarColor, 0xFF000000);
        }

        if (mAlwaysReadCloseOnTouchAttr || getContext().getApplicationInfo().targetSdkVersion
                >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            if (a.getBoolean(
                    R.styleable.Window_windowCloseOnTouchOutside,
                    false)) {
                setCloseOnTouchOutsideIfNotSet(true);
            }
        }

        WindowManager.LayoutParams params = getAttributes();

        if (!hasSoftInputMode()) {
            params.softInputMode = a.getInt(
                    R.styleable.Window_windowSoftInputMode,
                    params.softInputMode);
        }

		// 设置window后面的蒙层
        if (a.getBoolean(R.styleable.Window_backgroundDimEnabled,
                mIsFloating)) {
            /* All dialogs should have the window dimmed */
            if ((getForcedWindowFlags()&WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0) {
                params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }
            if (!haveDimAmount()) {
                params.dimAmount = a.getFloat(
                        android.R.styleable.Window_backgroundDimAmount, 0.5f);
            }
        }

		// 设置window出现的动画
        if (params.windowAnimations == 0) {
            params.windowAnimations = a.getResourceId(
                    R.styleable.Window_windowAnimationStyle, 0);
        }

        // The rest are only done if this window is not embedded; otherwise,
        // the values are inherited from our container.
        if (getContainer() == null) {
            if (mBackgroundDrawable == null) {
				// window背景
                if (mBackgroundResource == 0) {
                    mBackgroundResource = a.getResourceId(
                            R.styleable.Window_windowBackground, 0);
                }
				// window前景
                if (mFrameResource == 0) {
                    mFrameResource = a.getResourceId(R.styleable.Window_windowFrame, 0);
                }
                mBackgroundFallbackResource = a.getResourceId(
                        R.styleable.Window_windowBackgroundFallback, 0);
                if (false) {
                    System.out.println("Background: "
                            + Integer.toHexString(mBackgroundResource) + " Frame: "
                            + Integer.toHexString(mFrameResource));
                }
            }
            mElevation = a.getDimension(R.styleable.Window_windowElevation, 0);
            mClipToOutline = a.getBoolean(R.styleable.Window_windowClipToOutline, false);
            mTextColor = a.getColor(R.styleable.Window_textColor, Color.TRANSPARENT);
        }

        // Inflate the window decor.

        int layoutResource;
        int features = getLocalFeatures();
        // System.out.println("Features: 0x" + Integer.toHexString(features));
        if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0) {
            layoutResource = R.layout.screen_swipe_dismiss;
        } else if ((features & ((1 << FEATURE_LEFT_ICON) | (1 << FEATURE_RIGHT_ICON))) != 0) {
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        R.attr.dialogTitleIconsDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = R.layout.screen_title_icons;
            }
            // XXX Remove this once action bar supports these features.
            removeFeature(FEATURE_ACTION_BAR);
            // System.out.println("Title Icons!");
        } else if ((features & ((1 << FEATURE_PROGRESS) | (1 << FEATURE_INDETERMINATE_PROGRESS))) != 0
                && (features & (1 << FEATURE_ACTION_BAR)) == 0) {
            // Special case for a window with only a progress bar (and title).
            // XXX Need to have a no-title version of embedded windows.
            layoutResource = R.layout.screen_progress;
            // System.out.println("Progress!");
        } else if ((features & (1 << FEATURE_CUSTOM_TITLE)) != 0) {
            // Special case for a window with a custom title.
            // If the window is floating, we need a dialog layout
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        R.attr.dialogCustomTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = R.layout.screen_custom_title;
            }
            // XXX Remove this once action bar supports these features.
            removeFeature(FEATURE_ACTION_BAR);
        } else if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
            // If no other features and not embedded, only need a title.
            // If the window is floating, we need a dialog layout
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        R.attr.dialogTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {
                layoutResource = a.getResourceId(
                        R.styleable.Window_windowActionBarFullscreenDecorLayout,
                        R.layout.screen_action_bar);
            } else {
                layoutResource = R.layout.screen_title;
            }
            // System.out.println("Title!");
        } else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
            layoutResource = R.layout.screen_simple_overlay_action_mode;
        } else {
            // Embedded, so no decoration is needed.
            layoutResource = R.layout.screen_simple;
            // System.out.println("Simple!");
        }

        mDecor.startChanging();

        View in = mLayoutInflater.inflate(layoutResource, null);
        decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mContentRoot = (ViewGroup) in;

        ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }

        if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
            ProgressBar progress = getCircularProgressBar(false);
            if (progress != null) {
                progress.setIndeterminate(true);
            }
        }

        if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0) {
            registerSwipeCallbacks();
        }

        // Remaining setup -- of background and title -- that only applies
        // to top-level windows.
        if (getContainer() == null) {
            final Drawable background;
            if (mBackgroundResource != 0) {
                background = getContext().getDrawable(mBackgroundResource);
            } else {
                background = mBackgroundDrawable;
            }
            mDecor.setWindowBackground(background);

            final Drawable frame;
            if (mFrameResource != 0) {
                frame = getContext().getDrawable(mFrameResource);
            } else {
                frame = null;
            }
            mDecor.setWindowFrame(frame);

            mDecor.setElevation(mElevation);
            mDecor.setClipToOutline(mClipToOutline);

            if (mTitle != null) {
                setTitle(mTitle);
            }

            if (mTitleColor == 0) {
                mTitleColor = mTextColor;
            }
            setTitleColor(mTitleColor);
        }

        mDecor.finishChanging();

        return contentParent;
    }

```

整个函数的代码三百多行，乍一看起来让人头皮发麻望而却步。但是只要静下心来认真看一会就会发现，虽然代码量比较大，但是里面的逻辑确实比较清晰明了。

### 获取主题中的属性

从文章的一开始就一直在讨论，自定义的样式如何影响到了窗口显示的界面外观，到了这个函数基本上可以说是接近真相了。

```java

TypedArray a = getWindowStyle();

public final TypedArray getWindowStyle() {
    synchronized (this) {
        if (mWindowStyle == null) {
            mWindowStyle = mContext.obtainStyledAttributes(
                    com.android.internal.R.styleable.Window);
        }
        return mWindowStyle;
    }
}

```

在函数最开始的地方就调用了一句获取窗口样式属性的代码，而这个样式获取的过程就是使用在最开始创建的`ContextThemeWrapper`对象中包含的属性内容。那么从这里开始，之前在自定义样式中设置的那些属性就真的粉墨登场了。

### 设置Window属性

```java
if (a.getBoolean(R.styleable.Window_windowNoTitle, false)) {
    requestFeature(FEATURE_NO_TITLE);
} else if (a.getBoolean(R.styleable.Window_windowActionBar, false)) {
    // Don't allow an action bar if there is no title.
    requestFeature(FEATURE_ACTION_BAR);
}
...
if (a.getBoolean(R.styleable.Window_windowFullscreen, false)) {
    setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN & (~getForcedWindowFlags()));
}
...
WindowManager.LayoutParams params = getAttributes();
if (!hasSoftInputMode()) {
    params.softInputMode = a.getInt(R.styleable.Window_windowSoftInputMode,
            params.softInputMode);
}
```

在函数中充斥着大量上述类型的代码，目的是通过调用`Window`的几个关键函数根据自定义样式提供的属性对窗口的特征进行自定义操作。

### 设置DecorView属性

```java
mDecor.startChanging();

View in = mLayoutInflater.inflate(layoutResource, null);
decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
mContentRoot = (ViewGroup) in;

ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
if (contentParent == null) {
    throw new RuntimeException("Window couldn't find content container view");
}
...

// Remaining setup -- of background and title -- that only applies
// to top-level windows.
if (getContainer() == null) {
    final Drawable background;
    if (mBackgroundResource != 0) {
        background = getContext().getDrawable(mBackgroundResource);
    } else {
        background = mBackgroundDrawable;
    }
    mDecor.setWindowBackground(background);

    final Drawable frame;
    if (mFrameResource != 0) {
        frame = getContext().getDrawable(mFrameResource);
    } else {
        frame = null;
    }
    mDecor.setWindowFrame(frame);

    mDecor.setElevation(mElevation);
    mDecor.setClipToOutline(mClipToOutline);

    if (mTitle != null) {
        setTitle(mTitle);
    }

    if (mTitleColor == 0) {
        mTitleColor = mTextColor;
    }
    setTitleColor(mTitleColor);
}

mDecor.finishChanging();
```

窗口显示样式出了受其本身属性影响之外，另外一个最直接的因素应该就是`DecorView`的显示控制。所以在这个函数中做的第三件事情就是对`DecorView`的显示属性进行设置。

### 样式基础视图的实现

```java
int layoutResource;
int features = getLocalFeatures();
// System.out.println("Features: 0x" + Integer.toHexString(features));
if ((features & (1 << FEATURE_SWIPE_TO_DISMISS)) != 0) {
    layoutResource = R.layout.screen_swipe_dismiss;
} else if ((features & ((1 << FEATURE_LEFT_ICON) | (1 << FEATURE_RIGHT_ICON))) != 0) {
    if (mIsFloating) {
        TypedValue res = new TypedValue();
        getContext().getTheme().resolveAttribute(
                R.attr.dialogTitleIconsDecorLayout, res, true);
        layoutResource = res.resourceId;
    } else {
        layoutResource = R.layout.screen_title_icons;
    }
    // XXX Remove this once action bar supports these features.
    removeFeature(FEATURE_ACTION_BAR);
    // System.out.println("Title Icons!");
} else if ((features & ((1 << FEATURE_PROGRESS) | (1 << FEATURE_INDETERMINATE_PROGRESS))) != 0
        && (features & (1 << FEATURE_ACTION_BAR)) == 0) {
    // Special case for a window with only a progress bar (and title).
    // XXX Need to have a no-title version of embedded windows.
    layoutResource = R.layout.screen_progress;
    // System.out.println("Progress!");
} else if ((features & (1 << FEATURE_CUSTOM_TITLE)) != 0) {
    // Special case for a window with a custom title.
    // If the window is floating, we need a dialog layout
    if (mIsFloating) {
        TypedValue res = new TypedValue();
        getContext().getTheme().resolveAttribute(
                R.attr.dialogCustomTitleDecorLayout, res, true);
        layoutResource = res.resourceId;
    } else {
        layoutResource = R.layout.screen_custom_title;
    }
    // XXX Remove this once action bar supports these features.
    removeFeature(FEATURE_ACTION_BAR);
} else if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
    // If no other features and not embedded, only need a title.
    // If the window is floating, we need a dialog layout
    if (mIsFloating) {
        TypedValue res = new TypedValue();
        getContext().getTheme().resolveAttribute(
                R.attr.dialogTitleDecorLayout, res, true);
        layoutResource = res.resourceId;
    } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {
        layoutResource = a.getResourceId(
                R.styleable.Window_windowActionBarFullscreenDecorLayout,
                R.layout.screen_action_bar);
    } else {
        layoutResource = R.layout.screen_title;
    }
    // System.out.println("Title!");
} else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
    layoutResource = R.layout.screen_simple_overlay_action_mode;
} else {
    // Embedded, so no decoration is needed.
    layoutResource = R.layout.screen_simple;
    // System.out.println("Simple!");
}

```

上面的代码执行的是`ContentParent`创建过程。在这段代码中可以看到，针对不同类型的API版本已经主题属性，会加载不同的预定义布局文件来创建这个容器。当然，这些容器中都会有一个共同的特点，就是必然会包含一个`id=android.R.id.content`的FrameLayout。但是在这之外的事情就有主题自己决定然后在屏幕上显示出不同的基础样式。

### 待研究的小坑

从`ContentParent`的创建过程想到了这种有意思的实现方式，就是尝试通过某些特殊的手段将`ContentParent`创建过程中使用的预定义布局换成某个自定义的布局从而达到所有的视图都包含在某个特定的容器中，可以在这个容器中进行统一的自定义手势开发。
