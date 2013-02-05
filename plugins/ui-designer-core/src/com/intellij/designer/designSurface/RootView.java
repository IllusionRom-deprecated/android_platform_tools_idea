/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer.designSurface;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Alexander Lobas
 */
public class RootView extends JComponent {
  protected final int myX;
  protected final int myY;
  protected BufferedImage myImage;

  public RootView(int x, int y, @NotNull BufferedImage image) {
    this(x, y);
    setImage(image);
  }

  public RootView(int x, int y) {
    myX = x;
    myY = y;
  }

  @NotNull
  public BufferedImage getImage() {
    return myImage;
  }

  public void setImage(@NotNull BufferedImage image) {
    myImage = image;
    updateSize();
    repaint();
  }

  protected void updateSize() {
    if (myImage != null) {
      setBounds(myX, myY, myImage.getWidth(), myImage.getHeight());
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintImage(g);
  }

  protected void paintImage(Graphics g) {
    g.drawImage(myImage, 0, 0, null);
  }
}
