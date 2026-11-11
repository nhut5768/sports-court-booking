package vn.uth.court;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.web.util.UriComponentsBuilder;

/** Carries the original search through customer pages without accepting external return links. */
final class CustomerNavigation {
  private CustomerNavigation() {}

  static String searchUrl(String q, String sport, String area, LocalDate date,
      String minPrice, String maxPrice, int page) {
    return UriComponentsBuilder.fromPath("/")
        .queryParam("q", q).queryParam("sport", sport).queryParam("area", area)
        .queryParamIfPresent("date", Optional.ofNullable(date))
        .queryParam("minPrice", minPrice).queryParam("maxPrice", maxPrice)
        .queryParam("page", page).fragment("find").build().encode().toUriString();
  }

  static String safeSearchUrl(String returnTo) {
    if (returnTo != null && (returnTo.equals("/") || returnTo.startsWith("/?"))
        && returnTo.chars().noneMatch(Character::isISOControl)) {
      return returnTo;
    }
    return "/";
  }

  static String detailUrl(Long courtId, LocalDate date, String returnTo) {
    var url = UriComponentsBuilder.fromPath("/courts/" + courtId).queryParam("date", date);
    String safe = safeSearchUrl(returnTo);
    if (!safe.equals("/")) url.queryParam("returnTo", safe);
    return url.build().encode().toUriString();
  }
}
