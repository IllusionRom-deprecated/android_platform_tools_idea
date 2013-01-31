/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer.model;

import com.intellij.designer.palette.PaletteItem;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import javax.swing.*;

import static com.intellij.designer.model.MetaManager.*;

/**
 * Implementation of a {@link PaletteItem} which delegates to another {@linkplain PaletteItem}
 * but which possibly overrides the title, icon and or creation properties.
 */
class VariationPaletteItem implements PaletteItem {
  private final PaletteItem myDefaultItem;
  private final MetaModel myModel;
  private final Element myElement;
  private final String myTitle;
  private final String myIconPath;
  private final String myTooltip;
  private final String myCreation;
  private Icon myIcon;

  VariationPaletteItem(PaletteItem defaultItem, MetaModel model, Element element) {
    myDefaultItem = defaultItem;
    myModel = model;
    myElement = element;

    String title = myElement.getAttributeValue(ATTR_TITLE);
    if (StringUtil.isEmpty(title)) {
      title = myDefaultItem.getTitle();
    }
    myTitle = title;

    String iconPath = myElement.getAttributeValue(ATTR_ICON);
    if (StringUtil.isEmpty(iconPath)) {
      myIcon = myDefaultItem.getIcon();
    }
    myIconPath = iconPath;

    String tooltip = myElement.getAttributeValue(ATTR_TOOLTIP);
    if (StringUtil.isEmpty(tooltip)) {
      tooltip = myDefaultItem.getTooltip();
    }
    myTooltip = tooltip;

    Element creation = myElement.getChild(CREATION);
    if (creation != null) {
      myCreation = creation.getTextTrim();
    } else {
      myCreation = myModel.getCreation();
    }
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public Icon getIcon() {
    if (myIcon == null) {
      assert myIconPath != null;
      myIcon = IconLoader.findIcon(myIconPath, myModel.getModel());
    }
    return myIcon;
  }

  @Override
  public String getTooltip() {
    return myTooltip;
  }

  @Override
  public String getVersion() {
    return myDefaultItem.getVersion();
  }

  @Override
  public boolean isEnabled() {
    return myDefaultItem.isEnabled();
  }

  @Override
  public String getCreation() {
    return myCreation;
  }

  @Override
  public MetaModel getMetaModel() {
    return myModel;
  }
}
