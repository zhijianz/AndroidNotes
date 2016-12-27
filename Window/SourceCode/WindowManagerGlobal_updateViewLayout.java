/**
 * 相比于添加View而言，更新的操作会显得更加的清晰明了
 * 但是和添加的操作相比，还是会存在一个疑问：
 * 在整个添加的过程中，并没有看到为ViewRootImpl设置LayoutParams的过程
 * 而且创建也是在View.setLayoutParams之前
 * 
 * @param view   [description]
 * @param params [description]
 */

public void updateViewLayout(View view, ViewGroup.LayoutParams params) {
    if (view == null) {
        throw new IllegalArgumentException("view must not be null");
    }
    if (!(params instanceof WindowManager.LayoutParams)) {
        throw new IllegalArgumentException("Params must be WindowManager.LayoutParams");
    }

    final WindowManager.LayoutParams wparams = (WindowManager.LayoutParams)params;

    view.setLayoutParams(wparams);

    synchronized (mLock) {
        int index = findViewLocked(view, true);
        ViewRootImpl root = mRoots.get(index);
        mParams.remove(index);
        mParams.add(index, wparams);
        // 对于这个操作存疑，然而代码太长
        root.setLayoutParams(wparams, false);
    }
}