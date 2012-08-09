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
package com.intellij.psi.codeStyle.arrangement.model;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:18 PM
 */
public class ArrangementSettingsCompositeNode implements ArrangementSettingsNode {

  @NotNull private final List<ArrangementSettingsNode> myOperands = new ArrayList<ArrangementSettingsNode>();
  @NotNull private final Operator myOperator;

  public ArrangementSettingsCompositeNode(@NotNull Operator operator) {
    myOperator = operator;
  }

  @NotNull
  public List<ArrangementSettingsNode> getOperands() {
    return myOperands;
  }

  public void addOperand(@NotNull ArrangementSettingsNode node) {
    myOperands.add(node);
  }

  @NotNull
  public Operator getOperator() {
    return myOperator;
  }

  @Override
  public void invite(@NotNull ArrangementSettingsNodeVisitor visitor) {
    visitor.visit(this);
  }

  public enum Operator {
    AND, OR
  }
}