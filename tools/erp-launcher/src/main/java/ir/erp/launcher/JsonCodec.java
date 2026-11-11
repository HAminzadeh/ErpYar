package ir.erp.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, dependency-free strict JSON codec for the local cache manifest. */
final class JsonCodec {
  private final String input;
  private int i;
  private JsonCodec(String input) { this.input = input; }
  static Object parse(String input) {
    if (input.length() > 1_048_576) throw new StartupException("Cache manifest exceeds 1 MiB");
    JsonCodec parser = new JsonCodec(input);
    Object out = parser.value(); parser.ws();
    if (parser.i != input.length()) throw new StartupException("Trailing data in cache manifest");
    return out;
  }
  private Object value() {
    ws(); if (i == input.length()) throw invalid();
    return switch (input.charAt(i)) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> str();
      case 'n' -> literal("null", null);
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      default -> number();
    };
  }
  private Object literal(String token, Object out) {
    if (!input.startsWith(token, i)) throw invalid(); i += token.length(); return out;
  }
  private Map<String, Object> object() {
    i++; Map<String,Object> result = new LinkedHashMap<>(); ws();
    if (take('}')) return result;
    while (true) {
      ws(); if (i >= input.length() || input.charAt(i) != '"') throw invalid();
      String key = str(); ws(); expect(':');
      Object value = value();
      if (result.containsKey(key)) throw new StartupException("Duplicate JSON key: " + key);
      result.put(key, value);
      ws(); if (take('}')) return result; expect(',');
    }
  }
  private List<Object> array() {
    i++; List<Object> items = new ArrayList<>(); ws();
    if (take(']')) return items;
    while (true) { items.add(value()); ws(); if (take(']')) return items; expect(','); }
  }
  private String str() {
    expect('"'); StringBuilder sb = new StringBuilder();
    while (i < input.length()) {
      char c = input.charAt(i++);
      if (c == '"') return sb.toString();
      if (c < 0x20) throw invalid();
      if (c == '\\') {
        if (i >= input.length()) throw invalid(); c = input.charAt(i++);
        switch (c) {
          case '"', '\\', '/' -> sb.append(c);
          case 'n' -> sb.append('\n'); case 'r' -> sb.append('\r'); case 't' -> sb.append('\t');
          case 'b' -> sb.append('\b'); case 'f' -> sb.append('\f');
          case 'u' -> {
            if (i + 4 > input.length()) throw invalid();
            try { sb.append((char) Integer.parseInt(input.substring(i, i + 4), 16)); }
            catch (NumberFormatException ex) { throw invalid(); }
            i += 4;
          }
          default -> throw invalid();
        }
      } else sb.append(c);
    }
    throw invalid();
  }
  private Number number() {
    int start=i;
    if (take('-')) { /* sign */ }
    if (i == input.length() || !Character.isDigit(input.charAt(i))) throw invalid();
    if (take('0')) { if (i < input.length() && Character.isDigit(input.charAt(i))) throw invalid(); }
    else while (i < input.length() && input.charAt(i) >= '0' && input.charAt(i) <= '9') i++;
    if (i < input.length() && (input.charAt(i)=='.' || input.charAt(i)=='e' || input.charAt(i)=='E'))
      throw new StartupException("Only integer numbers are supported in cache manifest");
    try { return Long.parseLong(input.substring(start,i)); } catch (NumberFormatException ex) { throw invalid(); }
  }
  private void ws() { while (i < input.length() && " \r\n\t".indexOf(input.charAt(i)) >= 0) i++; }
  private boolean take(char ch) { if (i < input.length() && input.charAt(i)==ch) {i++;return true;}return false; }
  private void expect(char ch) { ws(); if (!take(ch)) throw invalid(); }
  private StartupException invalid() { return new StartupException("Malformed Docker cache JSON at offset " + i); }

  static String stringify(Object value) {
    if (value == null) return "null";
    if (value instanceof String s) return quote(s);
    if (value instanceof Number n) return n.toString();
    if (value instanceof Boolean b) return b.toString();
    if (value instanceof Map<?, ?> map) {
      List<String> parts=new ArrayList<>();
      for (var e : map.entrySet()) parts.add(quote((String)e.getKey())+":"+stringify(e.getValue()));
      return "{"+String.join(",",parts)+"}";
    }
    if (value instanceof List<?> list) {
      return "[" + String.join(",", list.stream().map(JsonCodec::stringify).toList()) + "]";
    }
    throw new IllegalArgumentException("JSON type unsupported");
  }
  private static String quote(String s) {
    StringBuilder out=new StringBuilder("\"");
    for (int j=0;j<s.length();j++) {
      char c=s.charAt(j);
      switch(c) {
        case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
        default -> { if(c<0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
      }
    }
    return out.append('"').toString();
  }
}
