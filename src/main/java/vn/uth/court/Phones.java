package vn.uth.court;

/** Số di động Việt Nam: 10 số, đầu 03, 05, 07, 08, 09. */
public final class Phones {
  public static final String RULE =
      "Số điện thoại phải là số di động Việt Nam 10 số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.";

  private Phones() {}

  /** Bỏ khoảng trắng, dấu chấm, gạch; đổi +84/84 thành 0. */
  public static String normalize(String raw) {
    if (raw == null) {
      return null;
    }
    String s = raw.replaceAll("[\\s.()-]", "");
    if (s.startsWith("+84")) {
      s = "0" + s.substring(3);
    } else if (s.startsWith("84") && s.length() == 11) {
      s = "0" + s.substring(2);
    }
    return s;
  }

  public static boolean valid(String normalized) {
    return normalized != null && normalized.matches("0[35789]\\d{8}");
  }

  /** Chuẩn hóa và kiểm tra; ném lỗi với thông báo dễ hiểu nếu sai. */
  public static String require(String raw) {
    String s = normalize(raw);
    if (!valid(s)) {
      throw new IllegalArgumentException(RULE);
    }
    return s;
  }
}
