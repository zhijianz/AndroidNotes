# ViewAnimation


```{viz}
digraph animation {
  edge[style = dashed, penwidth = .7, color = slategrey];
  node[shape = box, style = dashed, color = red]

  animation[label = 动画];
  tween[label = TweenedAnimation];
  frame[label = FrameAnimation];

  node[shape = folder, color = purple];
  tweenPackage[label = "android.view.animation"];
  framePackage[label = "android.graphics.drawable"];

  node[color = deepskyblue, shape = box];
  transitionAnimation[label = TransitionAnimation];
  rotateAnimation[label = RotateAimation];
  scaleAnimation[label= ScaleAnimation];
  alphaAnimation[label = AlphaAnimation];
  animationDrawable[label = AnimationDrawable];
  AnimatedStateListDrawable[label = AnimatedStateListDrawable];

  // relation
  rankdir = LR;
  animation -> {tween frame}

  tween -> {transitionAnimation rotateAnimation scaleAnimation alphaAnimation};
  tween -> tweenPackage;
  frame -> framePackage;
  frame -> animationDrawable;
  frame -> AnimatedStateListDrawable;
  {rankdir = TB; rank = same; tweenPackage animation framePackage};
}
```

## 帧动画

```{puml}
class AnimationDrawable{

}

class DrawableContainer{

}

interface Animatable{

}

AnimationDrawable -|> DrawableContainer
AnimationDrawable --|> Animatable
```

```java
package android.graphics.drawable;

/**
 * Interface that drawables supporting animations should implement.
 */
public interface Animatable {
    /**
     * Starts the drawable's animation.
     */
    void start();

    /**
     * Stops the drawable's animation.
     */
    void stop();

    /**
     * Indicates whether the animation is running.
     *
     * @return True if the animation is running, false otherwise.
     */
    boolean isRunning();
}
```

上面是帧动画关键类`AnimationDrawable`的结构图，这个类被放置在`android.graphics.drawable`包中。所以从类的继承结构以及API放置的位置可以看出来，帧动画本质上还是一种`Drawable`资源，其具体动画特性是由于实现了`Animatable`接口。因此，在使用帧动画的时候，使用的方式和使用其他的`Drawable`资源也相似：将动画描述资源放置在`res/drawable`目录下；将动画资源作为`View`的图像资源来使用。

### AnimatedStateListDrawable

这个类型的帧动画是为了支持`Material Design`在`Android 5.0`的时候退出的，目前只能够在`API21`以上使用。这个动画实现的效果和最初的`StateListDrawable`类型，控件在不同的状态时会展现出不同的关键帧，但是在状态切换的时候可以执行指定的动画效果。

```xml
<?xml version="1.0" encoding="utf-8"?>
<animated-selector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    tools:targetApi="lollipop">
    <item
        android:id="@+id/checking"
        android:drawable="@mipmap/a1"
        android:state_checked="false" />
    <item
        android:id="@+id/checked"
        android:drawable="@mipmap/a2"
        android:state_checked="true" />
    <transition
        android:fromId="@id/checking"
        android:toId="@id/checked">
        <animation-list>
            <item
                android:drawable="@mipmap/a1"
                android:duration="100" />
            <item
                android:drawable="@mipmap/a2"
                android:duration="100" />
            <item
                android:drawable="@mipmap/a3"
                android:duration="100" />
            <item
                android:drawable="@mipmap/a4"
                android:duration="100" />
            <item
                android:drawable="@mipmap/a5"
                android:duration="100" />
            <item
                android:drawable="@mipmap/a6"
                android:duration="100" />
        </animation-list>
    </transition>
</animated-selector>
```

上面的代码定义了一个`AnimatedStateListDrawable`资源放在`res/drawable`目录下，因为没有找到支持库所以只能指定特定的`targetApi`。在这个资源中需要完成的任务有两项，首先是通过`<item/>`节点定义控件在不同状态下展现出来的关键帧；然后在使用指定状态转变过程时执行的帧动画过程。其实现在引用开发的过程中并没有太多需要这种功能的场景，能够想到的也就是在制定不同主题的时候为了实现一个tab点击动画可以使用这个来减轻许多的开发工作，但是现在主要的问题时没有能够兼容21以下的版本。
