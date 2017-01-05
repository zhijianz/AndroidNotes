> 关于Android的Window运行机制

# Window

> Abstract base class for a top-level window look and behavior policy. An instance of this class should be used as the top-level view added to the window manager. It provides standard UI policies such as a background, title area, default key processing, etc. The only existing implementation of this abstract class is android.view.PhoneWindow, which you should instantiate when needing a Window.

什么是Window？

参考上面的Android官方文档对于这个类的描述，Window的实例应该被当作一个最顶层的View来使用，并且在这个类里面抽象了这种类型顶层View的显示外观和行为模式。所以对于Android的UI体系，应该可以理解成在原有View树状结构最顶层加入一个Window对象作为树根，并且这个顶层Window只能够是PhoneWindow的实例。

```{viz}
digraph ui_system {
  node[shape = box, style = dashed, color = red];
  edge[style = dashed, color = slategrey];

  pw[label = "PhoneWindow"];
  dv[label = "DecorView"];
  cr[label = "ContentRoot"];
  cp[label = "ContentParent(android.R.id.content)"];
  else[label = "something else decided by theme"];
  vs[label = "current ViewTree system"];

  rankdir = TB;
  pw -> dv -> cr -> cp;
  cr -> else;
  cp -> vs;
}
```

上图描述的应该是Android系统当前的UI体系结构，而通常讨论到的只是View的层次，这也是平常开发最常接触到的部分。现在考虑Window体系存在之后就需要尝试来研究在View树结果上层的这些内容怎么样在平时开发的过程中起到作用。

# Window 入门使用

在深入学习Window体系之前，先尝试通过学习Window的简单使用来对其有一个最初步的印象。

Window提供了最基础的三种类型的操作`addView, updateView, removeView`，这些操作都依赖于`WindowManager`作为操作的入口。

## window add view

```java
WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
WindowManager.LayoutParams params = new WindowManager.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
       WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.TRANSPARENT);
params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
params.gravity = Gravity.BOTTOM;
params.horizontalWeight = 1;
manager.addView(windowView1, params);
```

上面的代码将一个原来已经创建好的`windowView1`添加到Window中，在这个添加的过程中主要做了两件事情。首先是获取到`WindowManager`用来和`WindowManagerService`进行交互，在这里就是添加View；然后就是创建这个View对应的`WindowManager.LayoutParams`对象，目的是设置添加该View之后Window展现出来的一系列属性，这个对象整个Window学习的过程中都扮演着重要的角色，在后面会进行详细的介绍。所以比较抽象的理解的话，创建Window并且向其中添加View是一件比较简单的事情，而主要的工作都是在进行该Window属性的设置。

## window update view

```java
WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
ViewGroup.LayoutParams params = windowView1.getLayoutParams();
if (params instanceof WindowManager.LayoutParams){
    WindowManager.LayoutParams p = (WindowManager.LayoutParams) params;
    p.gravity = Gravity.BOTTOM | Gravity.RIGHT;
    manager.updateViewLayout(windowView1, p);
}
```

和View的添加过程一样，对已经添加到Window中的对象的更新操作同样是依赖`WindowManager.LayoutParams`和`WindowManager`两个对象进行。

## window remove view

```java
WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
ViewGroup.LayoutParams params = windowView1.getLayoutParams();
if (params instanceof WindowManager.LayoutParams){
    manager.removeView(windowView1);
}
```

上面的三段代码介绍了Window的三种最基本的使用方式，可以看到在`增/删/改`的过程中都离不开`WindowManager.LayoutParams`和`WindowManagerService`两个对象。所以在后面的篇幅中会重点的解释这两块内容。

# WindowManager 内部机制

在上面的内容中介绍了Window的基本使用流程，也就是对于window中内容的管理。而这些管理的操作在内部实际执行的行为则是这一部分需要学习的内容。

```{viz}
digraph key_component{
  center=true;
  node[shape=box, style=dashed, color=red];
  edge[penwidth=.6, color=slategrey]
  wm[label="WindowManager"];
  wmi[label="WindowManagerImpl"];
  wmg[label="WindowManagerGlobal"];
  node[shape=cds];
  subgraph cluster_list{
    label="list for view attached to window"
    shape=box;
    style=dashed;
    color=slategrey;
    fontColor=purple;
    node[shape=box, style=dashed, color=red];
    root[label="ViewRootImpl"];
    view[label="View"];
    params[label="WindowManager.LayoutParams"];
  }

  rankdir=LR;
  wm->wmi->wmg;
  wmg->{root view params};
}
```

```{puml}
interface WindowManager{

}

class WindowManagerImpl{

}

class WindowManagerGlobal{
  private final ArrayList<ViewRootImpl> mRoots;
  private final ArrayList<View> mViews;
  private final ArrayList<WindowManager.LayoutParams> mParams;
  private final ArrayList<View> mDayingViews;
  priavte static IWindowSession sWindowSession;
}

WindowManagerImpl -|> WindowManager
WindowManagerImpl --> WindowManagerGlobal
```

在之前的示例代码中，所有关于Window的操作都是通过WindowManager提供的接口来实现，但实际上执行者是WindowManagerGlobal。参见上面类图中表述出来的关系，`WindowManager`只是一个定义了window相关操作的接口，直接实现类是`WindowManagerImpl`，但是从这个类的源代码中会发现，关于window的所有操作的被桥接到`WindowManagerGlobal`中执行。关于`WindowManagerGlobal`中具体实现的window相关操作的代码分析可以参考`/SourceCode`目录下的源代码，这里介绍一些流程之外的关键信息。

在`WindowManagerGlobal`中有四个比较关键的列表（参见类图），其中三个用来保存和维护某个具体window的显示内容，包含了window的`ViewRootImpl/View/WindowManager.LayoutParams`，window中内容的增删改首先都是基于这三个列表保存的内容；剩下的最后一个列表存在是因为有时候在尝试删除某个window的时候，view的移除操作并不能够保证即时生效所以需要保存一些正在移除的内容防止各种访问冲突的异常发生。同时，关于window的操作在本质上都是需要和`WindowManagerService`进行交互的`IPC`过程，而这部分内容由该类的`sWindowSession`负责完成。`sWindowSession`是一个`IWindowSession`，具体的实现在`Session`类中。

另外一点，在看完`WindowManagerGlobal`中关于window操作的相关代码之后，比较明显的一点是所有的这些操作本质上都是基于上诉四个列表的增删改过程。而在这个增删改的过程中，`ViewRootImpl`扮演着相当重要的角色，如果说window的内容本质上是View所呈现出来的界面，而LayoutParams负责同时调控View在window中显示的参数配置，那么`ViewRootImpl`所执行的就是串联`window`和`view`的角色。在向window中添加内容的时候，需要`ViewRootImpl`发起view的绘制流程来显示界面；更改window内容的时候同样需要调用`ViewRootImpl`来实现界面UI的更新；最后window删除内容的时候需要调用`ViewRootImpl.die`来下发这个时候对应的各种回调通知，和相关的终结操作。并且，在上面的三个流程中，在`ViewRootImpl`的操作执行到响应阶段的时候会调起上面提及的`IPC`来完成`WindowManagerService`的调用。

# WindowManager.LayoutParams

[WindowManager.LayoutParams 详细参数参考](http://blog.sina.com.cn/s/blog_4b3c1f950100qd9s.html)

在示例代码中进行基本的window操作的时候，`WindowManager.LayoutParams`承担了配置window内容显示特性的责任。在这里尝试去详细了解，通过使用`WindowManager.LayoutParams`可以对window的内容达到什么样的展示效果。

```java
/**
 * @params w window的宽度
 * @params h window的高度
 * @params _type window的类型
 * @params _flags window行为参数的配置
 * @params _format 期望的位图图形模式，通常从PixelFormat中取值
 */
public LayoutParams(int w, int h, int _type, int _flags, int _format) {
    super(w, h);
    type = _type;
    flags = _flags;
    format = _format;
}
```

上面的代码是`LayoutParams`比较典型的一个构造函数，在其多个参数中对于创建window内容影响最大的是`_type/_flags`

## Window的类型

在创建的window的时候必须要设置其对应的类型，不同类型的window会具备不同的依赖关系、权限要求、层级坐标关系等等。从总体来说，window的类型可以分成三种——`应用窗口类型、子窗口类型、系统窗口类型`，这三种不同的类型越往后层级坐标越大也就是会覆盖在别的window上层。具体的内容见[参考文章](http://blog.sina.com.cn/s/blog_4b3c1f950100qd9s.html)

## Window 内容行为模式

在构造函数中或者后面的整个使用过程中，都可以通过`_flags`在一定范围配置window的行为模式。这些配置内容通常包括添加View的显示方式（全屏之类的），交互行为（软键盘输入行为，触摸模式）等等内容。具体内容参见[参考文章](http://blog.sina.com.cn/s/blog_4b3c1f950100qd9s.html)

# Window

从文章一开始就在讲window，但一直没有正面去介绍`window`类。

```java
/**
 * Abstract base class for a top-level window look and behavior policy.  An
 * instance of this class should be used as the top-level view added to the
 * window manager. It provides standard UI policies such as a background, title
 * area, default key processing, etc.
 *
 * <p>The only existing implementation of this abstract class is
 * android.policy.PhoneWindow, which you should instantiate when needing a
 * Window.  Eventually that class will be refactored and a factory method
 * added for creating Window instances without knowing about a particular
 * implementation.
 */
```

现看一下`Window`源代码文件中对于这个类的注释。在这个注释中对`Window`做了两个方面的介绍。首先是`Window`作为`top-level view`的地位，提供了类似于`背景，title栏，按键事件处理`等基础UI策略；另外一点是`PhoneWindow`作为`Window`的唯一实现类，可以通过工厂方法创建示例，最常见到的是`Policy.makeNewWindow`。

`Window`类的源代码主要是围绕着`LayoutParams/Features`进行，这两个属性基本决定了window的外观和行为。`Window`中提供了大量的接口来对`LayoutParams.flags`支持的属性进行设置。另外，`Window`可以在调用`setContentView`代码之前通过`requestFeature`接口设置这个窗口支持的特性。

```java
/**
 * Enable extended screen features.  This must be called before
 * setContentView().  May be called as many times as desired as long as it
 * is before setContentView().  If not called, no extended features
 * will be available.  You can not turn off a feature once it is requested.
 * You canot use other title features with {@link #FEATURE_CUSTOM_TITLE}.
 *
 * @param featureId The desired features, defined as constants by Window.
 * @return The features that are now set.
 */
```

参见`requestFeature`的注释会发现这个函数的调用会有下面的几个特点：

1. 必须要在`setContentView`之前调用。通过查看`setContentView`的源代码发现，这个限定存在的原因是在`setContentView`中创建容器的过程中就需要使用到`Window.features`来实现特定的显示特征，比如`window.title`。所以同理，`addContentView`同样需要考虑调用次序的问题

2. `window.features`在主动调用这个函数进行设置的时候，是不具备任何的沿用属性的。但实际从源代码中发现会有一个`DEFAULT_FEATURES`。

3. 在调用这个函数主动设定了`widnow.features`之后，并没用提供撤销已设定特性的接口，或者说提供了但是被`hide`了。

下面列出`Window`中支持的`FEATURE`:

```java
   public static final int FEATURE_OPTIONS_PANEL = 0;
   public static final int FEATURE_NO_TITLE = 1;
   public static final int FEATURE_PROGRESS = 2;
   public static final int FEATURE_LEFT_ICON = 3;
   public static final int FEATURE_RIGHT_ICON = 4;
   public static final int FEATURE_INDETERMINATE_PROGRESS = 5;
   public static final int FEATURE_CONTEXT_MENU = 6;
   public static final int FEATURE_CUSTOM_TITLE = 7;
   public static final int FEATURE_ACTION_BAR = 8;
   public static final int FEATURE_ACTION_BAR_OVERLAY = 9;
   public static final int FEATURE_ACTION_MODE_OVERLAY = 10;
   public static final int FEATURE_SWIPE_TO_DISMISS = 11;
   public static final int FEATURE_CONTENT_TRANSITIONS = 12;
   public static final int FEATURE_ACTIVITY_TRANSITIONS = 13;
```

`PhoneWindow`作为唯一的实现`Window`的子类，完成了父类定义的大量的接口，其中最重要的应该是view内容的填充过程。就和文章开始的示例图中描述的那样，`PhoneWindow`中定义了三个容器来组成其`View结构树`。这三个容器`mDcor/mContentRoot/mContentParent`。`mDecor`作为整个`view结构树`的最顶层，本身是继承自`FrameLayout`实现的一个容器，同时也是在这个window中实际被`WindowManager`进行操作的view；`mContentRoot`被`mDecor`所包含，在参见`setContentView`过程分析的时候会发现，这个容器会根据不同的主题实现等到不同的实际值，同时也会展现出对应该主题的外观和响应的行为；`mContentParent`就是平时最提及的`android.R.id.content`，开发中`setContentView`的内容也是被添加到这个容器里面。

而关于`PhoneWindow`内容添加过程的分析可以看另外的一篇文章《GenerateLayout》

## 关于 window 的小结

从两个window相关类的源代码分析来看，`Window`本质上扮演的是一个窗口内容管理的抽象

1. 创建并且管理整个用于显示的view树，最重要的一点是关于内容的添加，也就是`setContentView`的流程

2. 为`LayoutParams/FEATURE`提供对应的接口管理窗口内容显示的外观和行为

3. 提供callback为包含window的视图分发响应的事件回调。

# Window 体系结构

```{viz}
digraph windw {
    graph[compound = true, fontcolor = purple, style = dashed, penwidth = .6
    , color = slategrey]
    node[shape = plaintext]
    edge[penwidth = .5, color = slategrey]
    rankdir = TB

    subgraph cluster_manager {
      label = "encapsulate window system logic"
      fontsize = 22
      color = slategrey

      WindowManager -> WindowManagerImpl -> WindowManagerGlobal
      {randir = LR; rank = same; WindowManager, WindowManagerImpl}

      subgraph cluster_list {
        label = "list content represent window abstaction"
        fontsize = 14

        View
        params[label = "WindowManager.LayoutParams"]
        ViewRootImpl
      }
    }

    subgraph cluster_phonewindow {
      label = "internal logic for PhoneWindow"
      PhoneWindow -> {mDecorView, mContentParent, mContentRoot}
    }

    subgraph cluster_dialog {
      label = "dialog logic"
      Dialog -> {"Callback & Lifecycle", ContentView, Outlook}
    }

    WindowManagerGlobal -> params[lhead = cluster_list, minlen = 2]
    params -> PhoneWindow[dir = back]
    Dialog -> PhoneWindow
}
```

关于上图中描述的window体系结构（图画的真心是丑），在这个依赖调用体系中，最后的箭头都是指向了`WindowManager`所在的内部之际逻辑。在`PhoneWindow`这一层通常的使用场景都是出现在`Activity`这种有视图元素的组件当中，而在这一层实际上执行的操作只是对`WindowManager`中涉及的关键属性提供开放调用的接口以及一些关键的回调，窗口的逻辑本质上还是原来的那一套。`Dialog`这一层应该是平时开发最常接触到的一层，而内部的实现逻辑则是在`PhoneWindow`原有的基础上增加了一些对应生命周期的接口。
