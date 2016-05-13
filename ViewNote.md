# MeasureSpec

## 基本定义

`MeasureSpec`从字面上翻译是测量规格的意思，本质上是一个32位的整数，其中最高2位表示的是`MeasureModel`而用后面的30位表示大小。可以通过`MeasureSpec`的源代码进一步理解上面讲述的定义。

```java
public static class MeasureSpec {
  private static final int MODE_SHIFT = 30;
  private static final int MODE_MASK  = 0x3 << MODE_SHIFT;

  /**
   * Measure specification mode: The parent has not imposed any constraint
   * on the child. It can be whatever size it wants.
   */
  public static final int UNSPECIFIED = 0 << MODE_SHIFT;

  /**
   * Measure specification mode: The parent has determined an exact size
   * for the child. The child is going to be given those bounds regardless
   * of how big it wants to be.
   */
  public static final int EXACTLY     = 1 << MODE_SHIFT;

  /**
   * Measure specification mode: The child can be as large as it wants up
   * to the specified size.
   */
  public static final int AT_MOST     = 2 << MODE_SHIFT;

  /**
   * Creates a measure specification based on the supplied size and mode.
   *
   * The mode must always be one of the following:
   * <ul>
   *  <li>{@link android.view.View.MeasureSpec#UNSPECIFIED}</li>
   *  <li>{@link android.view.View.MeasureSpec#EXACTLY}</li>
   *  <li>{@link android.view.View.MeasureSpec#AT_MOST}</li>
   * </ul>
   *
   * <p><strong>Note:</strong> On API level 17 and lower, makeMeasureSpec's
   * implementation was such that the order of arguments did not matter
   * and overflow in either value could impact the resulting MeasureSpec.
   * {@link android.widget.RelativeLayout} was affected by this bug.
   * Apps targeting API levels greater than 17 will get the fixed, more strict
   * behavior.</p>
   *
   * @param size the size of the measure specification
   * @param mode the mode of the measure specification
   * @return the measure specification based on size and mode
   */
  public static int makeMeasureSpec(int size, int mode) {
      if (sUseBrokenMakeMeasureSpec) {
          return size + mode;
      } else {
          return (size & ~MODE_MASK) | (mode & MODE_MASK);
      }
  }

  /**
   * Like {@link #makeMeasureSpec(int, int)}, but any spec with a mode of UNSPECIFIED
   * will automatically get a size of 0. Older apps expect this.
   *
   * @hide internal use only for compatibility with system widgets and older apps
   */
  public static int makeSafeMeasureSpec(int size, int mode) {
      if (sUseZeroUnspecifiedMeasureSpec && mode == UNSPECIFIED) {
          return 0;
      }
      return makeMeasureSpec(size, mode);
  }

  /**
   * Extracts the mode from the supplied measure specification.
   *
   * @param measureSpec the measure specification to extract the mode from
   * @return {@link android.view.View.MeasureSpec#UNSPECIFIED},
   *         {@link android.view.View.MeasureSpec#AT_MOST} or
   *         {@link android.view.View.MeasureSpec#EXACTLY}
   */
  public static int getMode(int measureSpec) {
      return (measureSpec & MODE_MASK);
  }

  /**
   * Extracts the size from the supplied measure specification.
   *
   * @param measureSpec the measure specification to extract the size from
   * @return the size in pixels defined in the supplied measure specification
   */
  public static int getSize(int measureSpec) {
      return (measureSpec & ~MODE_MASK);
  }

  static int adjust(int measureSpec, int delta) {
      final int mode = getMode(measureSpec);
      int size = getSize(measureSpec);
      if (mode == UNSPECIFIED) {
          // No need to adjust size for UNSPECIFIED mode.
          return makeMeasureSpec(size, UNSPECIFIED);
      }
      size += delta;
      if (size < 0) {
          Log.e(VIEW_LOG_TAG, "MeasureSpec.adjust: new size would be negative! (" + size +
                  ") spec: " + toString(measureSpec) + " delta: " + delta);
          size = 0;
      }
      return makeMeasureSpec(size, mode);
  }

  /**
   * Returns a String representation of the specified measure
   * specification.
   *
   * @param measureSpec the measure specification to convert to a String
   * @return a String with the following format: "MeasureSpec: MODE SIZE"
   */
  public static String toString(int measureSpec) {
      int mode = getMode(measureSpec);
      int size = getSize(measureSpec);

      StringBuilder sb = new StringBuilder("MeasureSpec: ");

      if (mode == UNSPECIFIED)
          sb.append("UNSPECIFIED ");
      else if (mode == EXACTLY)
          sb.append("EXACTLY ");
      else if (mode == AT_MOST)
          sb.append("AT_MOST ");
      else
          sb.append(mode).append(" ");

      sb.append(size);
      return sb.toString();
  }
// }
```
