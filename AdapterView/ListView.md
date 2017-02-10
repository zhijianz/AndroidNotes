# ListView 绘制流程

下面的代码记录ListView中关键的绘制流程

```java
/**
 * 1-1. 布局的开始在父类AbsListView.onLayout中执行
 * 运行的实质是强制要求当前的子view以及ScapView进行布局
 * 然后调用ListView.layoutChildren进行整体布局
 */
AbsListView.onLayout

/**
 * 1-2. 第一次layoutChildren的时候因为还没有任何的子view
 * 所以大部分的操作都是没有意义的，操作直接导向fillFromTop
 */
ListView.layoutChildren

/**
 * 1-3. 从mFirstPosition开始自顶向下填充listview，计算
 * 出第一个item的坐标之后将操作导向fillDown
 */
ListView.fillFromTop

/**
 * 1-4. fillDown的操作通过item的pos和坐标判断是否在屏幕内
 * 来进行了一个循环，在这个循环中不断的更改pos将操作推动到
 * 下一个view并调用makeAndAddView
 */
ListView.fillDown

/**
 * 1-4-1. makeAndAddView的目的是为当前的pos拿到一个对应的
 * view，这个过程首先是从RecycleBin.ActiveViewList中获
 * 取，尝试失败之后调用obtainView获取一个（必然会产生一个）
 */
ListView.makeAndAddView

/**
 * 1-4-1-1. 因为obtainView这个函数比较重要，所以在这直接把源码贴出来
 * 在这个view的产生过程中
 * 优先尝试从RecycleBin.ScrapViewList中获取
 * 在上一步成功后调用Adapter.getView更新view的数据
 * 如果第一步获取不到View则调用Adapter.getView进行创建
 *
 * 在这个函数中看到了我们熟悉的Adapter.getView
 */
 View obtainView(int position, boolean[] isScrap) {  
     isScrap[0] = false;  
     View scrapView;  
     scrapView = mRecycler.getScrapView(position);  
     View child;  
     if (scrapView != null) {  
         child = mAdapter.getView(position, scrapView, this);  
         if (child != scrapView) {  
             mRecycler.addScrapView(scrapView);  
             if (mCacheColorHint != 0) {  
                 child.setDrawingCacheBackgroundColor(mCacheColorHint);  
             }  
         } else {  
             isScrap[0] = true;  
             dispatchFinishTemporaryDetach(child);  
         }  
     } else {  
         child = mAdapter.getView(position, null, this);  
         if (mCacheColorHint != 0) {  
             child.setDrawingCacheBackgroundColor(mCacheColorHint);  
         }  
     }  
     return child;  
 }  

 /**
  * 1-4-1-2. 在obtainView创建了对应的ItemView之后，调用setupChild
  * 将创建出来的ItemView附加到ListView中
  */
 ListView.setupChild

 // 总流程
 onLayout(){
   layoutChildren(){
     fillFromTop(){
       fillDown(){
         while(还在屏幕内){
           makeandaddview(){
             obtainView()
             setupChild()
           }
         }   
       }
     } 
   }   
 }

```

在view的现实过程中会触发两次的`measure/layout`过程，为了避免在重复的过程中再次去执行创建`ItemView`的操作，第二次`layout`的时候会将第一次已经创建好的所有`ItemView`从`ListView`中`detach`掉，注意这并没有将它们删除，所以再次使用的时候不会涉及到创建的性能消耗，当再次需要这些内容的时候才从`RecycleBin`的缓存中拿出来并`attach`到`ListView`中。

## scroll

```java
/**
 * 滑动部分的内容直接从关键代码入手
 */
ACTION_MOVE{
  /**
   * 每次发生滑动事件的时候都会调用这个函数进行处理
   * 在这个函数里面，首先通过ItemView的位置判断是
   * 否已经被移出了屏幕，如果是这样的话就通过RecycleBin
   * 移动回收到scrap队列中并且detach掉
   */
  trackMotionScroll{
    /**
     * 在外层的函数将无用的itemview回收掉之后
     * 这个函数负责根据手指滑动的偏移量计算并
     * 实现listview 的滑动效果
     */
    offsetChildrenTopAndBottom{}
    /**
     * 在上一步回收掉多余项之后产生的空白空间继续
     * 从数据集中获取数据并且根据这些数据填充新的
     * 子项
     */
    fillGap{
      /**
       * 执行流程到了这里之后又再次回到了最开始数据
       * 填充所进行过的流程，不同的是这个时候的缓存
       * 列表已经存在可用的缓存并可以对这些缓存进行
       * 复用，实现listview的真正作用
       */
      fillDown/fillUp{}
    }
  }
}

```
