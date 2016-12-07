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
  node[shape=box, style=dashed, color=red];
  edge[penwidth=.6, color=purple]
  wm[label="WindowManager"];
  wmi[label="WindowManagerImpl"];
  wmg[label="WindowManagerGlobal"];

  wm->wmi->wmg;
  wm->wmg[label="桥接", fontcolor=purple]
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

在`WindowManagerGlobal`中有四个比较关键的列表（参见类图），其中三个用来保存和维护某个具体window的显示内容，包含了window的`ViewRootImpl/View/WindowManager.LayoutParams`，window中内容的增删改首先都是基于这三个列表保存的内容；剩下的最后一个列表存在是因为有时候在尝试删除某个window的时候，view的移除操作并不能够保证即时生效所以需要保存一些正在移除的内容防止各种访问冲突的异常发生。

# Window.LayoutParams

# WindowManager

# Window
