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

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RadComponentTest extends TestCase {
  public void testGroupSiblings() {
    Map<RadComponent,List<RadComponent>> map;

    // Test empty
    assertNotNull(RadComponent.groupSiblings(Collections.<TestComponent>emptyList()));

    // Test single
    TestComponent single = new TestComponent("single");
    map = RadComponent.groupSiblings(Collections.<TestComponent>singletonList(single));
    assertNotNull(map);
    assertEquals(1, map.size());
    assertSame(single, map.get(null).iterator().next());
    assertSame(null, map.keySet().iterator().next());
    assertSame(single, map.values().iterator().next().iterator().next());

    // Test multiple
    TestComponent parent1 = new TestComponent("single");
    TestComponent parent2 = new TestComponent("parent2");
    TestComponent child1 = new TestComponent("child1");
    TestComponent child2 = new TestComponent("child2");
    child1.setParent(parent1);
    child2.setParent(parent1);
    TestComponent child3 = new TestComponent("child3");
    TestComponent child4 = new TestComponent("child4");
    TestComponent child5 = new TestComponent("child5");
    child3.setParent(parent2);
    child4.setParent(parent2);
    child5.setParent(parent2);
    TestComponent grandChild1 = new TestComponent("grandChild1");
    grandChild1.setParent(child1);
    TestComponent child6 = new TestComponent("child6");

    //  Should partition into:
    //  null: child6
    //  parent1: child1, child2
    //  parent2: child3, child4, child5
    //  child1: grandChild1
    map = RadComponent.groupSiblings(Arrays.asList(child1, child2, child3, child4, child5, child6, grandChild1));
    assertNotNull(map);
    assertEquals(4, map.size());
    assertEquals(Arrays.<RadComponent>asList(child6), map.get(null));
    assertEquals(Arrays.<RadComponent>asList(child1, child2), map.get(parent1));
    assertEquals(Arrays.<RadComponent>asList(child3, child4, child5), map.get(parent2));
    assertEquals(Arrays.<RadComponent>asList(grandChild1), map.get(child1));
  }

  private static class TestComponent extends RadVisualComponent { // Because RadVisualComponent is abstract
    private final String myName;
    private TestComponent(String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
