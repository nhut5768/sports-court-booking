package vn.uth.court;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class CourtForm {
  @NotBlank
  @Size(max = 100)
  private String name;

  @NotBlank
  @Pattern(regexp = "Cầu lông|Bóng đá|Tennis|Bóng rổ", message = "Chọn môn thể thao hợp lệ")
  private String sport;

  @NotBlank
  @Size(max = 100)
  private String area;

  @NotBlank
  @Size(max = 250)
  private String address;

  @Size(max = 2000)
  private String description;

  @Size(max = 1000)
  private String amenities;

  @NotNull
  @DecimalMin("1000")
  @DecimalMax("10000000")
  @Digits(integer = 8, fraction = 0)
  private BigDecimal price;

  @Min(0)
  @Max(23)
  private Integer peakStart;

  @Min(1)
  @Max(24)
  private Integer peakEnd;

  @DecimalMin("1000")
  @DecimalMax("10000000")
  @Digits(integer = 8, fraction = 0)
  private BigDecimal peakPrice;

  @NotNull
  @Min(0)
  @Max(23)
  private Integer openHour = 6;

  @NotNull
  @Min(1)
  @Max(24)
  private Integer closeHour = 22;

  private boolean active = true;

  public CourtForm() {}

  public CourtForm(Court c) {
    name = c.getName();
    sport = c.getSport();
    area = c.getArea();
    address = c.getAddress();
    description = c.getDescription();
    amenities = c.getAmenities();
    price = c.getPrice();
    openHour = c.getOpenHour();
    closeHour = c.getCloseHour();
    active = c.getActive();
    if (!c.getPriceRules().isEmpty()) {
      PriceRule r = c.getPriceRules().get(0);
      peakStart = r.getStartHour();
      peakEnd = r.getEndHour();
      peakPrice = r.getPrice();
    }
  }

  public String getName() {
    return name;
  }

  public void setName(String x) {
    name = x;
  }

  public String getSport() {
    return sport;
  }

  public void setSport(String x) {
    sport = x;
  }

  public String getArea() {
    return area;
  }

  public void setArea(String x) {
    area = x;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String x) {
    address = x;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String x) {
    description = x;
  }

  public String getAmenities() {
    return amenities;
  }

  public void setAmenities(String x) {
    amenities = x;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal x) {
    price = x;
  }

  public Integer getPeakStart() {
    return peakStart;
  }

  public void setPeakStart(Integer x) {
    peakStart = x;
  }

  public Integer getPeakEnd() {
    return peakEnd;
  }

  public void setPeakEnd(Integer x) {
    peakEnd = x;
  }

  public BigDecimal getPeakPrice() {
    return peakPrice;
  }

  public void setPeakPrice(BigDecimal x) {
    peakPrice = x;
  }

  public Integer getOpenHour() {
    return openHour;
  }

  public void setOpenHour(Integer x) {
    openHour = x;
  }

  public Integer getCloseHour() {
    return closeHour;
  }

  public void setCloseHour(Integer x) {
    closeHour = x;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean x) {
    active = x;
  }
}
