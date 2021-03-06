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
package org.jetbrains.plugins.groovy.intentions.declaration;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class GrSetStrongTypeIntention extends Intention {

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    PsiElement parent = element.getParent();

    PsiElement elementToBuildTemplate;
    GrVariable[] variables;
    if (parent instanceof GrVariable && parent.getParent() instanceof GrVariableDeclaration) {
      variables = ((GrVariableDeclaration)parent.getParent()).getVariables();
      elementToBuildTemplate = parent.getParent();
    }
    else if (parent instanceof GrVariable && parent.getParent() instanceof GrForInClause) {
      variables = new GrVariable[]{(GrVariable)parent};
      elementToBuildTemplate = parent.getParent().getParent();
    }
    else if (parent instanceof GrVariableDeclaration) {
      variables = ((GrVariableDeclaration)parent).getVariables();
      elementToBuildTemplate = parent;
    }
    else if (parent instanceof GrVariable) {
      variables = new GrVariable[]{((GrVariable)parent)};
      elementToBuildTemplate = parent;
    }
    else {
      return;
    }

    ArrayList<TypeConstraint> types = new ArrayList<TypeConstraint>();

    if (parent.getParent() instanceof GrForInClause) {
      types.add(SupertypeConstraint.create(PsiUtil.extractIteratedType((GrForInClause)parent.getParent())));
    }
    else {
      for (GrVariable variable : variables) {
        GrExpression initializer = variable.getInitializerGroovy();
        if (initializer != null) {
          PsiType type = initializer.getType();
          if (type != null) {
            types.add(SupertypeConstraint.create(type));
          }
        }
      }
    }

    TemplateBuilderImpl builder = new TemplateBuilderImpl(elementToBuildTemplate);
    PsiManager manager = element.getManager();

    PsiElement replaceElement = setType(element, parent, elementToBuildTemplate);
    assert replaceElement != null;

    TypeConstraint[] constraints = types.toArray(new TypeConstraint[types.size()]);
    ChooseTypeExpression chooseTypeExpression = new ChooseTypeExpression(constraints, manager, replaceElement.getResolveScope());
    builder.replaceElement(replaceElement, chooseTypeExpression);


    final PsiElement afterPostprocess = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(elementToBuildTemplate);
    final Template template = builder.buildTemplate();
    TextRange range = afterPostprocess.getTextRange();
    Document document = editor.getDocument();
    document.deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager templateManager = TemplateManager.getInstance(project);
    templateManager.startTemplate(editor, template);
  }

  @Nullable
  private static PsiElement setType(PsiElement element, PsiElement parent, PsiElement elementToBuildTemplate) {
    GrModifierList modifierList = getModifierList(parent);

    if (modifierList != null && modifierList.hasModifierProperty(GrModifier.DEF) && modifierList.getModifiers().length == 1) {
      return PsiUtil.findModifierInList(modifierList, GrModifier.DEF);
    }
    else {
      final PsiClassType typeToUse = TypesUtil.createType("Abc", element);
      if (elementToBuildTemplate instanceof GrVariableDeclaration) {
        ((GrVariableDeclaration)elementToBuildTemplate).setType(typeToUse);
      }
      else {
        ((GrVariable)parent).setType(typeToUse);
      }

      return getTypeElement(parent);
    }
  }

  @Nullable
  private static GrTypeElement getTypeElement(PsiElement parent) {
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getTypeElementGroovy();
    }
    else {
      return ((GrVariableDeclaration)parent).getTypeElementGroovy();
    }
  }

  @Nullable
  private static GrModifierList getModifierList(PsiElement parent) {
    GrModifierList modifierList;

    if (parent instanceof GrVariable) {
      modifierList = ((GrVariable)parent).getModifierList();
    }
    else {
      modifierList = ((GrVariableDeclaration)parent).getModifierList();
    }
    return modifierList;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        PsiElement parent = element.getParent();

        PsiElement pparent;
        if (isNameIdentifierOfVariable(element, parent) || isModifierListOfVar(element, parent)) {
          pparent = parent.getParent();
        }
        else if (isModifierListOfVarDecl(element, parent)) {
          pparent = parent;
        }
        else {
          return false;
        }

        if (pparent instanceof GrVariableDeclaration) {
          GrVariable[] variables = ((GrVariableDeclaration)pparent).getVariables();
          for (GrVariable variable : variables) {
            if (isVarDeclaredWithInitializer(variable)) return true;
          }
        }
        else if (pparent instanceof GrForInClause) {
          return PsiUtil.extractIteratedType((GrForInClause)pparent) != null;
        }
        else {
          return isVarDeclaredWithInitializer((GrVariable)parent);
        }

        return false;
      }

      private boolean isModifierListOfVarDecl(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getModifierList() == element;
      }

      private boolean isModifierListOfVar(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariable && ((GrVariable)parent).getModifierList() == element;
      }


      private boolean isNameIdentifierOfVariable(PsiElement element, PsiElement parent) {
        return parent instanceof GrVariable &&
              ((GrVariable)parent).getTypeElementGroovy() == null &&
              element == ((GrVariable)parent).getNameIdentifierGroovy();
      }
    };
  }

  private static boolean isVarDeclaredWithInitializer(GrVariable variable) {
    GrExpression initializer = variable.getInitializerGroovy();
    return initializer != null && initializer.getType() != null;
  }
}
