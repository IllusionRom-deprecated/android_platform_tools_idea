/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.model;

import com.intellij.designer.designSurface.ScalableComponent;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;

public class RadVisualComponentTest extends TestCase {
  public void testGetBounds() {
    TestComponent testComponent = new TestComponent();
    assertNull(testComponent.getParent());
    assertEquals(Collections.<RadComponent>emptyList(), testComponent.getChildren());

    testComponent.setBounds(1, 2, 3, 4);
    assertEquals(new Rectangle(1,2, 3,4), testComponent.getBounds());

    MyRoot nativeComponent = new MyRoot(3.0);
    testComponent.setNativeComponent(nativeComponent);
    assertEquals(new Rectangle(1, 2, 3, 4), testComponent.getBounds());
  }

  public void testGetBoundsConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);
    testComponent.setBounds(100, 110, 300, 400);

    // No conversion for model coordinates
    assertEquals(new Rectangle(100, 110, 300, 400), testComponent.getBounds());
    // No conversion when querying for the model view itself
    assertEquals(new Rectangle(100, 110, 300, 400), testComponent.getBounds(nativeComponent));
    // When converting to the parent, should scale by above scale factor (2), then shift by view location relative to panel (15,20)
    assertEquals(new Rectangle(100 * 2 + 15, 110 * 2 + 20, 300 * 2, 400 * 2), testComponent.getBounds(panel));
    // When converting to another child, first shift to parent, then down to other child (5,5)
    assertEquals(new Rectangle(100 * 2 + 15 - 5, 110 * 2 + 20 - 5, 300 * 2, 400 * 2), testComponent.getBounds(other));
  }

  public void testModelRectangleConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    // No conversion when querying for the model view itself
    assertEquals(new Rectangle(100, 110, 300, 400), testComponent.fromModel(nativeComponent, new Rectangle(100, 110, 300, 400)));

    // When converting to the parent, should scale by above scale factor (2), then shift by view location relative to panel (15,20)
    assertEquals(new Rectangle(100 * 2 + 15, 110 * 2 + 20, 300 * 2, 400 * 2),
                 testComponent.fromModel(panel, new Rectangle(100, 110, 300, 400)));
    // When converting to another child, first shift to parent, then down to other child (5,5)
    assertEquals(new Rectangle(100 * 2 + 15 - 5, 110 * 2 + 20 - 5, 300 * 2, 400 * 2),
                 testComponent.fromModel(other, new Rectangle(100, 110, 300, 400)));

    // Compute reverse direction of the above rectangles

    assertEquals(new Rectangle(100, 110, 300, 400), testComponent.toModel(nativeComponent, new Rectangle(100, 110, 300, 400)));
    assertEquals(new Rectangle(100, 110, 300, 400),
                 testComponent.toModel(panel, new Rectangle(100 * 2 + 15, 110 * 2 + 20, 300 * 2, 400 * 2)));
    assertEquals(new Rectangle(100, 110, 300, 400),
                 testComponent.toModel(other, new Rectangle(100 * 2 + 15 - 5, 110 * 2 + 20 - 5, 300 * 2, 400 * 2)));
  }

  public void testModelPointConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    // No conversion when querying for the model view itself
    assertEquals(new Point(100, 110), testComponent.fromModel(nativeComponent, new Point(100, 110)));

    // When converting to the parent, should scale by above scale factor (2), then shift by view location relative to panel (15,20)
    assertEquals(new Point(100 * 2 + 15, 110 * 2 + 20), testComponent.fromModel(panel, new Point(100, 110)));
    // When converting to another child, first shift to parent, then down to other child (5,5)
    assertEquals(new Point(100 * 2 + 15 - 5, 110 * 2 + 20 - 5), testComponent.fromModel(other, new Point(100, 110)));

    // Compute reverse direction of the above Points

    assertEquals(new Point(100, 110), testComponent.toModel(nativeComponent, new Point(100, 110)));
    assertEquals(new Point(100, 110), testComponent.toModel(panel, new Point(100 * 2 + 15, 110 * 2 + 20)));
    assertEquals(new Point(100, 110), testComponent.toModel(other, new Point(100 * 2 + 15 - 5, 110 * 2 + 20 - 5)));
  }

  public void testModelSizeConversion() {
    TestComponent testComponent = new TestComponent();

    MyRoot nativeComponent = new MyRoot(2.0);
    JPanel panel = new JPanel();
    panel.add(nativeComponent);
    nativeComponent.setLocation(15, 20);
    JPanel other = new JPanel();
    panel.add(other);
    other.setLocation(5, 5);

    testComponent.setNativeComponent(nativeComponent);

    // No conversion when querying for the model view itself
    assertEquals(new Dimension(300, 400), testComponent.fromModel(nativeComponent, new Dimension(300, 400)));

    // When converting to the parent, should scale by above scale factor (2), then shift by view location relative to panel (15,20)
    assertEquals(new Dimension(300 * 2, 400 * 2), testComponent.fromModel(panel, new Dimension(300, 400)));
    // When converting to another child, first shift to parent, then down to other child (5,5)
    assertEquals(new Dimension(300 * 2, 400 * 2), testComponent.fromModel(other, new Dimension(300, 400)));

    // Compute reverse direction of the above Dimensions

    assertEquals(new Dimension(300, 400), testComponent.toModel(nativeComponent, new Dimension(300, 400)));
    assertEquals(new Dimension(300, 400), testComponent.toModel(panel, new Dimension(300 * 2, 400 * 2)));
    assertEquals(new Dimension(300, 400), testComponent.toModel(other, new Dimension(300 * 2, 400 * 2)));
  }

  private static class TestComponent extends RadVisualComponent { // Because RadVisualComponent is abstract
  }

  private static class MyRoot extends JComponent implements ScalableComponent {
    private double myScale;

    private MyRoot(double scale) {
      myScale = scale;
    }

    @Override
    public double getScale() {
      return myScale;
    }
  }
}
