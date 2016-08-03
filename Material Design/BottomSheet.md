> BottomSheet 会在应用程序的底部弹出交互的彩蛋，可以在这个菜单中对当前内容进行进一步的交互操作。在Android Support Library Design 23.2中官方添加了对于BottomSheet的实现支持，在这对这种实现方式进行简单的介绍

# BottomSheet

这种类型的BottomSheet实现的是一种非模式状态，弹出内容和原有内容处于同一水平，并却不会在触摸外部之后会收回BottomSheet

```java
// 在gradle中添加支持库
compile 'com.android.support:design:23.4.0'
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<android.support.design.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/cl_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    tools:context="com.example.zzj.demomaterialdesign.MainActivity">

    <!-- more -->

    <android.support.v4.widget.NestedScrollView
        android:id="@+id/nsv_bottom_sheet"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/holo_orange_light"
        android:clipToPadding="true"
        app:layout_behavior="android.support.design.widget.BottomSheetBehavior">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:padding="16dp"
            android:singleLine="false"
            android:text="@string/input" />
    </android.support.v4.widget.NestedScrollView>
</android.support.design.widget.CoordinatorLayout>

```
在上面的布局文件中，`NestedScrollView`所包含的内容就是`BottomSheet`将要展现的内容。在整个布局文件中有两个地方需要去注意一下；第一个是必须使用`CoordinatorLayout`作为最外层的容器，因为`BottomSheet`的实现需要依赖于`CoordinatorLayout.Behavior`去实现；第二点是在`NestedScrollView`中设置好`app:layout_behavior`属性，使用的是支持库中提供的`android.support.design.widget.BottomSheetBehavior`，让`NestedScrollView`具有`BottomSheet`的行为模式。

```java
public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private BottomSheetBehavior mBottomSheetBehavior;
    private View mBottomSheetView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBottomSheetView = findViewById(R.id.nsv_bottom_sheet);
        mBottomSheetBehavior = BottomSheetBehavior.from(mBottomSheetView);
        findViewById(R.id.btn_bottom_sheet).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_bottom_sheet:
                if (mBottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED){
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }else {
                    mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                break;
        }
    }
}
```

上面的代码中，通过获取原来在布局文件中定义好的`BottomSheetBehavior`，通过该对象对`BottomSheet`进行操作控制

# BottomSheetDialogFragment

除了上面介绍的在原有的布局文件中定义`BottomSheet`的内容这种方法之外，还可以使用`BottomSheetDialogFragment`的方式来实现，不过这种方式实现的是`Modal Bottom Sheet`。意思是在这种实现方式中，`BottomSheet`就相当于一个模式对话框，弹出时范围之外的内容会变成灰色，点击时隐藏`BottomSheet`。

```xml
<?xml version="1.0" encoding="utf-8"?>
<android.support.v4.widget.NestedScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="360dp"
    android:orientation="vertical"
    android:background="@android:color/holo_orange_light">
    <TextView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp"
        android:text="@string/input"/>
</android.support.v4.widget.NestedScrollView>
```

```java
public class CustomBottomSheetFragment extends BottomSheetDialogFragment {
    @Override
    public void setupDialog(Dialog dialog, int style) {
        super.setupDialog(dialog, style);
        View rootView = View.inflate(getContext(), R.layout.fragment_bottom_sheet, null);
        ViewCompat.setElevation(rootView, 16);
        getDialog().setContentView(rootView);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) ((View)rootView.getParent()).getLayoutParams();
        CoordinatorLayout.Behavior behavior = params.getBehavior();
        if (behavior != null && behavior instanceof BottomSheetBehavior){
            ((BottomSheetBehavior) behavior).setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN){
                        CustomBottomSheetFragment.this.dismiss();
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {

                }
            });
        }
    }
}


public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private CustomBottomSheetFragment mBottomSheetFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn_bottom_sheet_fragment).setOnClickListener(this);
    }
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.btn_bottom_sheet_fragment:
                if (mBottomSheetFragment == null){
                    mBottomSheetFragment = new CustomBottomSheetFragment();
                }
                mBottomSheetFragment.show(getSupportFragmentManager(), "");
                break;
        }
    }
}

```

这种实现方式的关键点是通过父容器`CoordinatorLayout`去获取到`BottomSheetBehavior`，然后再去实现`BottomSheet`的某些操作。
