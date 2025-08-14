package com.huyu.dynamic.node;

import com.huyu.dynamic.context.IMContext;
import com.huyu.dynamic.utils.IMStrUtils;

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * 解析 #{},${}
 */
public class IMTextSqlNode implements IMSqlNode {

  private String text;

  // 是否是动态sql,相当于具有 ${}, 后期会使用到
  private boolean isDynamic;

  // 是否具有#{}
  private boolean isPrepared;

  public IMTextSqlNode(final String text) {
    this.text = IMStrUtils.removeExtraWhitespaces(text);
  }

  @Override
  public boolean apply(IMContext imContext) {
    imContext.appendText((createParser(new AppendTokenValueHandler(imContext, null))).parse(text));
    return false;
  }

  public void appendText(String text) {
    this.text += IMStrUtils.removeExtraWhitespaces(text);
  }

  /**
   * 解析文本是否具有 ${xx}
   *
   * @return
   */
  public boolean isDynamic() {
    CheckerHasTokenParser checker = new CheckerHasTokenParser();
    GenericTokenParser parser = createParser(checker);
    parser.parse(text);
    return isDynamic = checker.isHasToken();
  }

  /**
   * 解析文本是否具有 #{xx}
   *
   * @return
   */
  public boolean isPrepared() {
    CheckerHasTokenParser checker = new CheckerHasTokenParser();
    GenericTokenParser parser = createStaticParser(checker);
    parser.parse(text);
    return isPrepared = checker.isHasToken();
  }

  private GenericTokenParser createParser(TokenHandler handler) {
    return new GenericTokenParser("${", "}", handler);
  }

  /**
   * #{}, 构建ParamMapping的数据 和 构建对应的值
   *
   * @param handler
   *
   * @return
   */
  private GenericTokenParser createStaticParser(TokenHandler handler) {
    return new GenericTokenParser("#{", "}", handler);
  }

  private static class AppendTokenValueHandler implements TokenHandler {

    private final IMContext context;
    private final Pattern injectionFilter;

    public AppendTokenValueHandler(IMContext context, Pattern injectionFilter) {
      this.context = context;
      this.injectionFilter = injectionFilter;
    }

    @Override
    public String handleToken(String content) {
      Object parameter = context.currentBinds().get("_parameter");
      if (parameter == null) {
        context.currentBinds().put("value", null);
      } else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
        context.currentBinds().put("value", parameter);
      }

      // 通过ognl来获取值
      Object value = OgnlCache.getValue(content, context.currentBinds());

      // 直接调用toString方法
      String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"

      // 不允许包含特殊字符
      checkInjection(srtValue);
      return srtValue;
    }

    private void checkInjection(String value) {
      if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
        throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
      }
    }
  }

  private static class CheckerHasTokenParser implements TokenHandler {

    private boolean hasToken;

    public CheckerHasTokenParser() {
      // Prevent Synthetic Access
    }

    public boolean isHasToken() {
      return hasToken;
    }

    @Override
    public String handleToken(String content) {
      this.hasToken = true;
      return null;
    }
  }
}
