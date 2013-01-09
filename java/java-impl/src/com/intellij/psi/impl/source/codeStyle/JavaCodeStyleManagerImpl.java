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

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.*;

public class JavaCodeStyleManagerImpl extends JavaCodeStyleManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.JavaCodeStyleManagerImpl");

  @NonNls private static final String IMPL_SUFFIX = "Impl";
  @NonNls private static final String GET_PREFIX = "get";
  @NonNls private static final String IS_PREFIX = "is";
  @NonNls private static final String FIND_PREFIX = "find";
  @NonNls private static final String CREATE_PREFIX = "create";
  @NonNls private static final String SET_PREFIX = "set";

  private final Project myProject;

  public JavaCodeStyleManagerImpl(final Project project) {
    myProject = project;
  }

  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element) throws IncorrectOperationException {
    return shortenClassReferences(element, 0);
  }

  @Override
  public PsiElement shortenClassReferences(@NotNull PsiElement element, int flags) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if (!SourceTreeToPsiMap.hasTreeElement(element)) return element;

    final boolean addImports = (flags & DO_NOT_ADD_IMPORTS) == 0;
    final boolean incompleteCode = (flags & UNCOMPLETE_CODE) != 0;
    final TreeElement reference = new ReferenceAdjuster(myProject).process((TreeElement)element.getNode(), addImports, incompleteCode);
    return SourceTreeToPsiMap.treeToPsiNotNull(reference);
  }

  @Override
  public void shortenClassReferences(@NotNull PsiElement element, int startOffset, int endOffset)
    throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    if (SourceTreeToPsiMap.hasTreeElement(element)) {
      new ReferenceAdjuster(myProject).processRange((TreeElement)element.getNode(), startOffset, endOffset);
    }
  }

  @Override
  public PsiElement qualifyClassReferences(@NotNull PsiElement element) {
    final TreeElement reference = new ReferenceAdjuster(true, true).process((TreeElement)element.getNode(), false, false);
    return SourceTreeToPsiMap.treeToPsiNotNull(reference);
  }

  @Override
  public void optimizeImports(@NotNull PsiFile file) throws IncorrectOperationException {
    CheckUtil.checkWritable(file);
    if (file instanceof PsiJavaFile) {
      PsiImportList newList = prepareOptimizeImportsResult((PsiJavaFile)file);
      if (newList != null) {
        final PsiImportList importList = ((PsiJavaFile)file).getImportList();
        if (importList != null) {
          importList.replace(newList);
        }
      }
    }
  }

  @Override
  public PsiImportList prepareOptimizeImportsResult(@NotNull PsiJavaFile file) {
    return new ImportHelper(getSettings()).prepareOptimizeImportsResult(file);
  }

  @Override
  public boolean addImport(@NotNull PsiJavaFile file, @NotNull PsiClass refClass) {
    return new ImportHelper(getSettings()).addImport(file, refClass);
  }

  @Override
  public void removeRedundantImports(@NotNull final PsiJavaFile file) throws IncorrectOperationException {
    final Collection<PsiImportStatementBase> redundant = findRedundantImports(file);
    if (redundant == null) return;

    for (final PsiImportStatementBase importStatement : redundant) {
      final PsiJavaCodeReferenceElement ref = importStatement.getImportReference();
      //Do not remove non-resolving refs
      if (ref == null || ref.resolve() == null) {
        continue;
      }

      importStatement.delete();
    }
  }

  @Override
  @Nullable
  public Collection<PsiImportStatementBase> findRedundantImports(final PsiJavaFile file) {
    final PsiImportList importList = file.getImportList();
    if (importList == null) return null;
    final PsiImportStatementBase[] imports = importList.getAllImportStatements();
    if( imports.length == 0 ) return null;

    Set<PsiImportStatementBase> allImports = new THashSet<PsiImportStatementBase>(Arrays.asList(imports));
    final Collection<PsiImportStatementBase> redundant;
    if (JspPsiUtil.isInJspFile(file)) {
      // remove only duplicate imports
      redundant = ContainerUtil.newIdentityTroveSet();
      ContainerUtil.addAll(redundant, imports);
      redundant.removeAll(allImports);
      for (PsiImportStatementBase importStatement : imports) {
        if (importStatement instanceof JspxImportStatement && ((JspxImportStatement)importStatement).isForeignFileImport()) {
          redundant.remove(importStatement);
        }
      }
    }
    else {
      redundant = allImports;
      final List<PsiFile> roots = file.getViewProvider().getAllFiles();
      for (PsiElement root : roots) {
        root.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
            if (!reference.isQualified()) {
              final JavaResolveResult resolveResult = reference.advancedResolve(false);
              if (!inTheSamePackage(file, resolveResult.getElement())) {
                final PsiElement resolveScope = resolveResult.getCurrentFileResolveScope();
                if (resolveScope instanceof PsiImportStatementBase) {
                  final PsiImportStatementBase importStatementBase = (PsiImportStatementBase)resolveScope;
                  redundant.remove(importStatementBase);
                }
              }
            }
            super.visitReferenceElement(reference);
          }

          private boolean inTheSamePackage(PsiJavaFile file, PsiElement element) {
            if (element instanceof PsiClass && ((PsiClass)element).getContainingClass() == null) {
              final PsiFile containingFile = element.getContainingFile();
              if (containingFile instanceof PsiJavaFile) {
                return Comparing.strEqual(file.getPackageName(), ((PsiJavaFile)containingFile).getPackageName());
              }
            }
            return false;
          }
        });
      }
    }
    return redundant;
  }

  @Override
  public int findEntryIndex(@NotNull PsiImportStatementBase statement) {
    return new ImportHelper(getSettings()).findEntryIndex(statement);
  }

  @Override
  public SuggestedNameInfo suggestVariableName(@NotNull final VariableKind kind,
                                               @Nullable final String propertyName,
                                               @Nullable final PsiExpression expr,
                                               @Nullable PsiType type,
                                               final boolean correctKeywords) {
    LinkedHashSet<String> names = new LinkedHashSet<String>();

    if (expr != null && type == null) {
      type = expr.getType();
    }

    if (propertyName != null) {
      String[] namesByName = getSuggestionsByName(propertyName, kind, false, correctKeywords);
      sortVariableNameSuggestions(namesByName, kind, propertyName, null);
      ContainerUtil.addAll(names, namesByName);
    }

    final NamesByExprInfo namesByExpr;
    if (expr != null) {
      namesByExpr = suggestVariableNameByExpression(expr, kind, correctKeywords);
      if (namesByExpr.propertyName != null) {
        sortVariableNameSuggestions(namesByExpr.names, kind, namesByExpr.propertyName, null);
      }
      ContainerUtil.addAll(names, namesByExpr.names);
    }
    else {
      namesByExpr = null;
    }

    if (type != null) {
      String[] namesByType = suggestVariableNameByType(type, kind, correctKeywords);
      sortVariableNameSuggestions(namesByType, kind, null, type);
      ContainerUtil.addAll(names, namesByType);
    }

    final String _propertyName;
    if (propertyName != null) {
      _propertyName = propertyName;
    }
    else {
      _propertyName = namesByExpr != null ? namesByExpr.propertyName : null;
    }

    addNamesFromStatistics(names, kind, _propertyName, type);

    String[] namesArray = ArrayUtil.toStringArray(names);
    sortVariableNameSuggestions(namesArray, kind, _propertyName, type);

    final PsiType _type = type;
    return new SuggestedNameInfo(namesArray) {
      @Override
      public void nameChosen(String name) {
        if (_propertyName != null || _type != null && _type.isValid()) {
          JavaStatisticsManager.incVariableNameUseCount(name, kind, _propertyName, _type);
        }
      }
    };
  }

  private static void addNamesFromStatistics(Set<String> names, VariableKind variableKind, @Nullable String propertyName, @Nullable PsiType type) {
    String[] allNames = JavaStatisticsManager.getAllVariableNamesUsed(variableKind, propertyName, type);

    int maxFrequency = 0;
    for (String name : allNames) {
      int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
      maxFrequency = Math.max(maxFrequency, count);
    }

    int frequencyLimit = Math.max(5, maxFrequency / 2);

    for (String name : allNames) {
      if( names.contains( name ) )
      {
        continue;
      }
      int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
      if (LOG.isDebugEnabled()) {
        LOG.debug("new name:" + name + " count:" + count);
        LOG.debug("frequencyLimit:" + frequencyLimit);
      }
      if (count >= frequencyLimit) {
        names.add(name);
      }
    }

    if (propertyName != null && type != null) {
      addNamesFromStatistics(names, variableKind, propertyName, null);
      addNamesFromStatistics(names, variableKind, null, type);
    }
  }

  private String[] suggestVariableNameByType(PsiType type, final VariableKind variableKind, boolean correctKeywords) {
    String longTypeName = getLongTypeName(type);
    CodeStyleSettings.TypeToNameMap map = getMapByVariableKind(variableKind);
    if (map != null && longTypeName != null) {
      if (type.equals(PsiType.NULL)) {
        longTypeName = CommonClassNames.JAVA_LANG_OBJECT;
      }
      String name = map.nameByType(longTypeName);
      if (name != null && isIdentifier(name)) {
        return new String[]{name};
      }
    }

    Collection<String> suggestions = new LinkedHashSet<String>();

    suggestNamesForCollectionInheritors(type, variableKind, suggestions, correctKeywords);
    suggestNamesFromGenericParameters(type, variableKind, suggestions, correctKeywords);

    String typeName = normalizeTypeName(getTypeName(type));
    if (typeName != null) {
      ContainerUtil.addAll(suggestions, getSuggestionsByName(typeName, variableKind, type instanceof PsiArrayType, correctKeywords));
    }

    return ArrayUtil.toStringArray(suggestions);
  }

  private void suggestNamesFromGenericParameters(final PsiType type,
                                                 final VariableKind variableKind,
                                                 final Collection<String> suggestions, boolean correctKeywords) {
    if (!(type instanceof PsiClassType)) {
      return;
    }
    StringBuilder fullNameBuilder = new StringBuilder();
    final PsiType[] parameters = ((PsiClassType)type).getParameters();
    for (PsiType parameter : parameters) {
      if (parameter instanceof PsiClassType) {
        final String typeName = normalizeTypeName(getTypeName(parameter));
        if (typeName != null) {
          fullNameBuilder.append(typeName);
        }
      }
    }
    String baseName = normalizeTypeName(getTypeName(type));
    if (baseName != null) {
      fullNameBuilder.append(baseName);
      ContainerUtil.addAll(suggestions, getSuggestionsByName(fullNameBuilder.toString(), variableKind, false, correctKeywords));
    }
  }

  private void suggestNamesForCollectionInheritors(final PsiType type,
                                                   final VariableKind variableKind,
                                                   final Collection<String> suggestions,
                                                   final boolean correctKeywords) {
    PsiType componentType = PsiUtil.extractIterableTypeParameter(type, false);
    if (componentType == null || componentType.equals(type)) {
      return;
    }
    String typeName = normalizeTypeName(getTypeName(componentType));
    if (typeName != null) {
      ContainerUtil.addAll(suggestions, getSuggestionsByName(typeName, variableKind, true, correctKeywords));
    }
  }

  @Nullable
  private static String normalizeTypeName(String typeName) {
    if (typeName == null) {
      return null;
    }
    if (typeName.endsWith(IMPL_SUFFIX) && typeName.length() > IMPL_SUFFIX.length()) {
      return typeName.substring(0, typeName.length() - IMPL_SUFFIX.length());
    }
    return typeName;
  }

  @Nullable
  private static String getTypeName(PsiType type) {
    type = type.getDeepComponentType();
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final String className = classType.getClassName();
      if (className != null) return className;
      final PsiClass aClass = classType.resolve();
      return aClass instanceof PsiAnonymousClass ? ((PsiAnonymousClass)aClass).getBaseClassType().getClassName() : null;
    }
    else if (type instanceof PsiPrimitiveType) {
      return type.getPresentableText();
    }
    else if (type instanceof PsiWildcardType) {
      return getTypeName(((PsiWildcardType)type).getExtendsBound());
    }
    else if (type instanceof PsiIntersectionType) {
      return getTypeName(((PsiIntersectionType)type).getRepresentative());
    }
    else if (type instanceof PsiCapturedWildcardType) {
      return getTypeName(((PsiCapturedWildcardType)type).getWildcard());
    }
    else if (type instanceof PsiDisjunctionType) {
      return getTypeName(((PsiDisjunctionType)type).getLeastUpperBound());
    }
    else {
      return null;
    }
  }

  @Nullable
  private static String getLongTypeName(PsiType type) {
    if (type instanceof PsiClassType) {
      PsiClass aClass = ((PsiClassType)type).resolve();
      if (aClass == null) {
        return null;
      }
      else if (aClass instanceof PsiAnonymousClass) {
        PsiClass baseClass = ((PsiAnonymousClass)aClass).getBaseClassType().resolve();
        return baseClass != null ? baseClass.getQualifiedName() : null;
      }
      else {
        return aClass.getQualifiedName();
      }
    }
    else if (type instanceof PsiArrayType) {
      return getLongTypeName(((PsiArrayType)type).getComponentType()) + "[]";
    }
    else if (type instanceof PsiPrimitiveType) {
      return type.getPresentableText();
    }
    else if (type instanceof PsiWildcardType) {
      final PsiType bound = ((PsiWildcardType)type).getBound();
      return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
    }
    else if (type instanceof PsiCapturedWildcardType) {
      final PsiType bound = ((PsiCapturedWildcardType)type).getWildcard().getBound();
      return bound != null ? getLongTypeName(bound) : CommonClassNames.JAVA_LANG_OBJECT;
    }
    else if (type instanceof PsiIntersectionType) {
      return getLongTypeName(((PsiIntersectionType)type).getRepresentative());
    }
    else if (type instanceof PsiDisjunctionType) {
      return getLongTypeName(((PsiDisjunctionType)type).getLeastUpperBound());
    }
    else {
      return null;
    }
  }

  private static class NamesByExprInfo {
    final String[] names;
    final String propertyName;

    public NamesByExprInfo(String propertyName, String... names) {
      this.names = names;
      this.propertyName = propertyName;
    }
  }

  private NamesByExprInfo suggestVariableNameByExpression(PsiExpression expr, VariableKind variableKind, boolean correctKeywords) {
    final NamesByExprInfo names1 = suggestVariableNameByExpressionOnly(expr, variableKind, correctKeywords);
    final NamesByExprInfo names2 = suggestVariableNameByExpressionPlace(expr, variableKind, correctKeywords);

    PsiType type = expr.getType();
    final String[] names3;
    if (type != null) {
      names3 = suggestVariableNameByType(type, variableKind, correctKeywords);
    }
    else {
      names3 = null;
    }

    final LinkedHashSet<String> names = new LinkedHashSet<String>();
    final String[] fromLiterals = suggestVariableNameFromLiterals(expr, variableKind, correctKeywords);
    if (fromLiterals != null) {
      ContainerUtil.addAll(names, fromLiterals);
    }
    ContainerUtil.addAll(names, names1.names);
    ContainerUtil.addAll(names, names2.names);
    if (names3 != null) {
      ContainerUtil.addAll(names, names3);
    }

    String[] namesArray = ArrayUtil.toStringArray(names);
    String propertyName = names1.propertyName != null ? names1.propertyName : names2.propertyName;
    return new NamesByExprInfo(propertyName, namesArray);
  }

  @Nullable
  private String[] suggestVariableNameFromLiterals(PsiExpression expr, VariableKind variableKind, boolean correctKeywords) {
    final PsiElement[] literals = PsiTreeUtil.collectElements(expr, new PsiElementFilter() {
      @Override
      public boolean isAccepted(PsiElement element) {
        if (isStringPsiLiteral(element) && StringUtil.isJavaIdentifier(StringUtil.unquoteString(element.getText()))) {
          final PsiElement exprList = element.getParent();
          if (exprList instanceof PsiExpressionList) {
            final PsiElement call = exprList.getParent();
            if (call instanceof PsiNewExpression) {
              return true;
            } else if (call instanceof PsiMethodCallExpression) {
              //TODO: exclude or not getA().getB("name").getC(); or getA(getB("name").getC()); It works fine for now in the most cases
              return true;
            }
          }
        }
        return false;
      }
    });

    if (literals.length == 1) {
      final String text = StringUtil.unquoteString(literals[0].getText());
      return getSuggestionsByName(text, variableKind, expr.getType() instanceof PsiArrayType, correctKeywords);
    }
    return null;
  }

  private NamesByExprInfo suggestVariableNameByExpressionOnly(PsiExpression expr, final VariableKind variableKind, boolean correctKeywords) {
    if (expr instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpr = ((PsiMethodCallExpression)expr).getMethodExpression();
      String methodName = methodExpr.getReferenceName();
      if (methodName != null) {
        String[] words = NameUtil.nameToWords(methodName);
        if (words.length > 0) {
          final String firstWord = words[0];
          if (GET_PREFIX.equals(firstWord)
              || IS_PREFIX.equals(firstWord)
              || FIND_PREFIX.equals(firstWord)
              || CREATE_PREFIX.equals(firstWord)) {
            if (words.length > 1) {
              final String propertyName = methodName.substring(firstWord.length());
              String[] names = getSuggestionsByName(propertyName, variableKind, false, correctKeywords);
              final PsiExpression qualifierExpression = methodExpr.getQualifierExpression();
              if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiVariable) {
                names = ArrayUtil.append(names, StringUtil.sanitizeJavaIdentifier(changeIfNotIdentifier(qualifierExpression.getText() + StringUtil.capitalize(propertyName))));
              }
              return new NamesByExprInfo(propertyName, names);
            }
          }
          else if (words.length == 1) {
            return new NamesByExprInfo(methodName, getSuggestionsByName(methodName, variableKind, false, correctKeywords));
          }
        }
      }
    }
    else if (expr instanceof PsiReferenceExpression) {
      String propertyName = ((PsiReferenceExpression)expr).getReferenceName();
      PsiElement refElement = ((PsiReferenceExpression)expr).resolve();
      if (refElement instanceof PsiVariable) {
        VariableKind refVariableKind = getVariableKind((PsiVariable)refElement);
        propertyName = variableNameToPropertyName(propertyName, refVariableKind);
      }
      if (refElement != null && propertyName != null) {
        String[] names = getSuggestionsByName(propertyName, variableKind, false, correctKeywords);
        return new NamesByExprInfo(propertyName, names);
      }
    }
    else if (expr instanceof PsiArrayAccessExpression) {
      PsiExpression arrayExpr = ((PsiArrayAccessExpression)expr).getArrayExpression();
      if (arrayExpr instanceof PsiReferenceExpression) {
        String arrayName = ((PsiReferenceExpression)arrayExpr).getReferenceName();
        PsiElement refElement = ((PsiReferenceExpression)arrayExpr).resolve();
        if (refElement instanceof PsiVariable) {
          VariableKind refVariableKind = getVariableKind((PsiVariable)refElement);
          arrayName = variableNameToPropertyName(arrayName, refVariableKind);
        }

        if (arrayName != null) {
          String name = StringUtil.unpluralize(arrayName);
          if (name != null) {
            String[] names = getSuggestionsByName(name, variableKind, false, correctKeywords);
            return new NamesByExprInfo(name, names);
          }
        }
      }
    }
    else if (expr instanceof PsiLiteralExpression && variableKind == VariableKind.STATIC_FINAL_FIELD) {
      final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expr;
      final Object value = literalExpression.getValue();
      if (value instanceof String) {
        final String stringValue = (String)value;
        String[] names = getSuggestionsByValue(stringValue);
        if (names.length > 0) {
          return new NamesByExprInfo(null, constantValueToConstantName(names));
        }
      }
    } else if (expr instanceof PsiParenthesizedExpression) {
      return suggestVariableNameByExpressionOnly(((PsiParenthesizedExpression)expr).getExpression(), variableKind, correctKeywords);
    } else if (expr instanceof PsiTypeCastExpression) {
      return suggestVariableNameByExpressionOnly(((PsiTypeCastExpression)expr).getOperand(), variableKind, correctKeywords);
    } else if (expr instanceof PsiLiteralExpression) {
      final String text = StringUtil.stripQuotesAroundValue(expr.getText());
      if (isIdentifier(text)) {
        return new NamesByExprInfo(text, getSuggestionsByName(text, variableKind, false, correctKeywords));
      }
    }

    return new NamesByExprInfo(null, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  private static String constantValueToConstantName(final String[] names) {
    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < names.length; i++) {
      if (i > 0) result.append("_");
      result.append(names[i]);
    }
    return result.toString();
  }

  private static String[] getSuggestionsByValue(final String stringValue) {
    List<String> result = new ArrayList<String>();
    StringBuffer currentWord = new StringBuffer();

    boolean prevIsUpperCase  = false;

    for (int i = 0; i < stringValue.length(); i++) {
      final char c = stringValue.charAt(i);
      if (Character.isUpperCase(c)) {
        if (currentWord.length() > 0 && !prevIsUpperCase) {
          result.add(currentWord.toString());
          currentWord = new StringBuffer();
        }
        currentWord.append(c);
      } else if (Character.isLowerCase(c)) {
        currentWord.append(Character.toUpperCase(c));
      } else if (Character.isJavaIdentifierPart(c) && c != '_') {
        if (Character.isJavaIdentifierStart(c) || currentWord.length() > 0 || !result.isEmpty()) {
          currentWord.append(c);
        }
      } else {
        if (currentWord.length() > 0) {
          result.add(currentWord.toString());
          currentWord = new StringBuffer();
        }
      }

      prevIsUpperCase = Character.isUpperCase(c);
    }

    if (currentWord.length() > 0) {
      result.add(currentWord.toString());
    }
    return ArrayUtil.toStringArray(result);
  }

  private NamesByExprInfo suggestVariableNameByExpressionPlace(PsiExpression expr, final VariableKind variableKind, boolean correctKeywords) {
    if (expr.getParent() instanceof PsiExpressionList) {
      PsiExpressionList list = (PsiExpressionList)expr.getParent();
      PsiElement listParent = list.getParent();
      PsiSubstitutor subst = PsiSubstitutor.EMPTY;
      PsiMethod method = null;
      if (listParent instanceof PsiMethodCallExpression) {
        final JavaResolveResult resolveResult = ((PsiMethodCallExpression)listParent).getMethodExpression().advancedResolve(false);
        method = (PsiMethod)resolveResult.getElement();
        subst = resolveResult.getSubstitutor();
      }
      else {
        if (listParent instanceof PsiAnonymousClass) {
          listParent = listParent.getParent();
        }
        if (listParent instanceof PsiNewExpression) {
          method = ((PsiNewExpression)listParent).resolveConstructor();
        }
      }

      if (method != null) {
        final PsiElement navElement = method.getNavigationElement();
        if (navElement instanceof PsiMethod) {
          method = (PsiMethod)navElement;
        }
        PsiExpression[] expressions = list.getExpressions();
        int index = -1;
        for (int i = 0; i < expressions.length; i++) {
          if (expressions[i] == expr) {
            index = i;
            break;
          }
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (index < parameters.length) {
          String name = parameters[index].getName();
          if (name != null && TypeConversionUtil.areTypesAssignmentCompatible(subst.substitute(parameters[index].getType()), expr)) {
            name = variableNameToPropertyName(name, VariableKind.PARAMETER);
            String[] names = getSuggestionsByName(name, variableKind, false, correctKeywords);
            if (expressions.length == 1) {
              final String methodName = method.getName();
              String[] words = NameUtil.nameToWords(methodName);
              if (words.length > 0) {
                final String firstWord = words[0];
                if (SET_PREFIX.equals(firstWord)) {
                  final String propertyName = methodName.substring(firstWord.length());
                  final String[] setterNames = getSuggestionsByName(propertyName, variableKind, false, correctKeywords);
                  names = ArrayUtil.mergeArrays(names, setterNames);
                }
              }
            }
            return new NamesByExprInfo(name, names);
          }
        }
      }
    }
    else if (expr.getParent() instanceof PsiAssignmentExpression && variableKind == VariableKind.PARAMETER) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expr.getParent();
      if (expr == assignmentExpression.getRExpression()) {
        final PsiExpression leftExpression = assignmentExpression.getLExpression();
        if (leftExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression) leftExpression).getQualifier() == null) {
          String name = leftExpression.getText();
          if (name != null) {
            final PsiElement resolve = ((PsiReferenceExpression)leftExpression).resolve();
            if (resolve instanceof PsiVariable) {
              name = variableNameToPropertyName(name, getVariableKind((PsiVariable)resolve));
            }
            String[] names = getSuggestionsByName(name, variableKind, false, correctKeywords);
            return new NamesByExprInfo(name, names);
          }
        }
      }
    }

    return new NamesByExprInfo(null, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  @Override
  public String variableNameToPropertyName(String name, VariableKind variableKind) {
    if (variableKind == VariableKind.STATIC_FINAL_FIELD) {
      StringBuilder buffer = new StringBuilder();
      for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (c != '_') {
          if( Character.isLowerCase( c ) )
          {
            return variableNameToPropertyNameInner( name, variableKind );
          }

          buffer.append(Character.toLowerCase(c));
        continue;
        }
        //noinspection AssignmentToForLoopParameter
        i++;
        if (i < name.length()) {
          c = name.charAt(i);
          buffer.append(c);
        }
      }
      return buffer.toString();
    }

    return variableNameToPropertyNameInner(name, variableKind);
  }

  private String variableNameToPropertyNameInner(String name, VariableKind variableKind) {
    String prefix = getPrefixByVariableKind(variableKind);
    String suffix = getSuffixByVariableKind(variableKind);
    boolean doDecapitalize = false;

    if (name.startsWith(prefix) && name.length() > prefix.length()) {
      name = name.substring(prefix.length());
      doDecapitalize = true;
    }

    if (name.endsWith(suffix) && name.length() > suffix.length()) {
      name = name.substring(0, name.length() - suffix.length());
      doDecapitalize = true;
    }

    if (name.startsWith(IS_PREFIX) && name.length() > IS_PREFIX.length() && Character.isUpperCase(name.charAt(IS_PREFIX.length()))) {
      name = name.substring(IS_PREFIX.length());
      doDecapitalize = true;
    }

    if (doDecapitalize) {
      name = Introspector.decapitalize(name);
    }

    return name;
  }

  @Override
  public String propertyNameToVariableName(String propertyName, VariableKind variableKind) {
    if (variableKind == VariableKind.STATIC_FINAL_FIELD) {
      String[] words = NameUtil.nameToWords(propertyName);
      StringBuilder buffer = new StringBuilder();
      for (int i = 0; i < words.length; i++) {
        String word = words[i];
        if (i > 0) {
          buffer.append("_");
        }
        buffer.append(StringUtil.toUpperCase(word));
      }
      return buffer.toString();
    }

    String prefix = getPrefixByVariableKind(variableKind);
    String name = propertyName;
    if (name.length() > 0 && prefix.length() > 0 && !StringUtil.endsWithChar(prefix, '_')) {
      name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    name = prefix + name + getSuffixByVariableKind(variableKind);
    name = changeIfNotIdentifier(name);
    return name;
  }

  private String[] getSuggestionsByName(String name, VariableKind variableKind, boolean isArray, boolean correctKeywords) {
    boolean upperCaseStyle = variableKind == VariableKind.STATIC_FINAL_FIELD;
    boolean preferLongerNames = getSettings().PREFER_LONGER_NAMES;
    String prefix = getPrefixByVariableKind(variableKind);
    String suffix = getSuffixByVariableKind(variableKind);

    List<String> answer = new ArrayList<String>();
    for (String suggestion : NameUtil.getSuggestionsByName(name, prefix, suffix, upperCaseStyle, preferLongerNames, isArray)) {
      answer.add(correctKeywords ? changeIfNotIdentifier(suggestion) : suggestion);
    }

    return ArrayUtil.toStringArray(answer);
  }

  @Override
  public String suggestUniqueVariableName(String baseName, PsiElement place, boolean lookForward) {
    int index = 0;
    PsiElement scope = PsiTreeUtil.getNonStrictParentOfType(place, PsiStatement.class, PsiCodeBlock.class, PsiMethod.class);
    NextName:
    while (true) {
      String name = baseName;
      if (index > 0) {
        name += index;
      }
      index++;
      if (PsiUtil.isVariableNameUnique(name, place)) {
        if (lookForward) {
          final String name1 = name;
          PsiElement run = scope;
          while (run != null) {
            class CancelException extends RuntimeException {
            }
            try {
              run.accept(new JavaRecursiveElementWalkingVisitor() {
                @Override
                public void visitAnonymousClass(final PsiAnonymousClass aClass) {
                }

                @Override public void visitVariable(PsiVariable variable) {
                  if (name1.equals(variable.getName())) {
                    throw new CancelException();
                  }
                }
              });
            }
            catch (CancelException e) {
              continue NextName;
            }
            run = run.getNextSibling();
            if (scope instanceof PsiMethod) {//do not check next member for param name conflict
              break;
            }
          }

        }
        return name;
      }
    }
  }

  @Override
  @NotNull
  public SuggestedNameInfo suggestUniqueVariableName(@NotNull final SuggestedNameInfo baseNameInfo,
                                                     PsiElement place,
                                                     boolean ignorePlaceName,
                                                     boolean lookForward) {
    final String[] names = baseNameInfo.names;
    final LinkedHashSet<String> uniqueNames = new LinkedHashSet<String>(names.length);
    for (String name : names) {
      if (ignorePlaceName && place instanceof PsiNamedElement) {
        final String placeName = ((PsiNamedElement)place).getName();
        if (Comparing.strEqual(placeName, name)) {
          uniqueNames.add(name);
          continue;
        }
      }
      uniqueNames.add(suggestUniqueVariableName(name, place, lookForward));
    }

    return new SuggestedNameInfo(ArrayUtil.toStringArray(uniqueNames)) {
      @Override
      public void nameChosen(String name) {
        baseNameInfo.nameChosen(name);
      }
    };
  }

  private static void sortVariableNameSuggestions(String[] names,
                                           final VariableKind variableKind,
                                           @Nullable final String propertyName,
                                           @Nullable final PsiType type) {
    if( names.length <= 1 ) {
      return;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("sorting names:" + variableKind);
      if (propertyName != null) {
        LOG.debug("propertyName:" + propertyName);
      }
      if (type != null) {
        LOG.debug("type:" + type);
      }
      for (String name : names) {
        int count = JavaStatisticsManager.getVariableNameUseCount(name, variableKind, propertyName, type);
        LOG.debug(name + " : " + count);
      }
    }

    Comparator<String> comparator = new Comparator<String>() {
      @Override
      public int compare(String s1, String s2) {
        int count1 = JavaStatisticsManager.getVariableNameUseCount(s1, variableKind, propertyName, type);
        int count2 = JavaStatisticsManager.getVariableNameUseCount(s2, variableKind, propertyName, type);
        return count2 - count1;
      }
    };
    Arrays.sort(names, comparator);
  }

  @Override
  @NotNull
  public String getPrefixByVariableKind(VariableKind variableKind) {
    String prefix = "";
    switch (variableKind) {
      case FIELD:
        prefix = getSettings().FIELD_NAME_PREFIX;
        break;
      case STATIC_FIELD:
        prefix = getSettings().STATIC_FIELD_NAME_PREFIX;
        break;
      case PARAMETER:
        prefix = getSettings().PARAMETER_NAME_PREFIX;
        break;
      case LOCAL_VARIABLE:
        prefix = getSettings().LOCAL_VARIABLE_NAME_PREFIX;
        break;
      case STATIC_FINAL_FIELD:
        prefix = "";
        break;
      default:
        LOG.assertTrue(false);
        break;
    }
    if (prefix == null) {
      prefix = "";
    }
    return prefix;
  }

  @Override
  @NotNull
  public String getSuffixByVariableKind(VariableKind variableKind) {
    String suffix = "";
    switch (variableKind) {
      case FIELD:
        suffix = getSettings().FIELD_NAME_SUFFIX;
        break;
      case STATIC_FIELD:
        suffix = getSettings().STATIC_FIELD_NAME_SUFFIX;
        break;
      case PARAMETER:
        suffix = getSettings().PARAMETER_NAME_SUFFIX;
        break;
      case LOCAL_VARIABLE:
        suffix = getSettings().LOCAL_VARIABLE_NAME_SUFFIX;
        break;
      case STATIC_FINAL_FIELD:
        suffix = "";
        break;
      default:
        LOG.assertTrue(false);
        break;
    }
    if (suffix == null) {
      suffix = "";
    }
    return suffix;
  }

  @Nullable
  private CodeStyleSettings.TypeToNameMap getMapByVariableKind(VariableKind variableKind) {
    if (variableKind == VariableKind.FIELD) return getSettings().FIELD_TYPE_TO_NAME;
    if (variableKind == VariableKind.STATIC_FIELD) return getSettings().STATIC_FIELD_TYPE_TO_NAME;
    if (variableKind == VariableKind.PARAMETER) return getSettings().PARAMETER_TYPE_TO_NAME;
    if (variableKind == VariableKind.LOCAL_VARIABLE) return getSettings().LOCAL_VARIABLE_TYPE_TO_NAME;
    return null;
  }

  @NonNls
  private String changeIfNotIdentifier(String name) {
    if (!isIdentifier(name)) {
      return StringUtil.fixVariableNameDerivedFromPropertyName(name);
    }
    return name;
  }

  private boolean isIdentifier(String name) {
    return JavaPsiFacade.getInstance(myProject).getNameHelper().isIdentifier(name, LanguageLevel.HIGHEST);
  }

  private CodeStyleSettings getSettings() {
    return CodeStyleSettingsManager.getSettings(myProject);
  }

  public static boolean isStringPsiLiteral(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final String text = element.getText();
      return text.length() > 1 && StringUtil.isQuotedString(text);
    }
    return false;
  }
}