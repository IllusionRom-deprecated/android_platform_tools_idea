/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.dom.layout;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.converters.FragmentClassConverter;

/**
 * @author Eugene.Kudelevsky
 */
public interface Fragment extends LayoutElement {
  @Attribute("name")
  @Convert(FragmentClassConverter.class)
  AndroidAttributeValue<PsiClass> getFragmentName();

  @Attribute("class")
  @Convert(FragmentClassConverter.class)
  GenericAttributeValue<PsiClass> getFragmentClass();
}