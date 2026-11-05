package vn.uth.court;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "courts")
public class Court {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 50)
  private String sport;

  @Column(nullable = false, length = 100)
  private String area;

  @Column(nullable = false, length = 250)
  private String address;

  @Column(length = 2000)
  private String description;

  @Column(length = 1000)
  private String amenities;

  @Column(nullable = false, precision = 12, scale = 0)
  private BigDecimal price;

  @Column(nullable = false, precision = 12, scale = 0)
  private BigDecimal minPrice;

  @Column(nullable = false)
  private int openHour;

  @Column(nullable = false)
  private int closeHour;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  private Account owner;

  @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("startHour ASC")
  private List<PriceRule> priceRules = new ArrayList<>();

  @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("sortOrder ASC")
  private List<CourtImage> images = new ArrayList<>();

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = LocalDateTime.now(AppConstants.ZONE);
    }
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String value) {
    this.name = value;
  }

  public String getSport() {
    return sport;
  }

  public void setSport(String value) {
    this.sport = value;
  }

  public String getArea() {
    return area;
  }

  public void setArea(String value) {
    this.area = value;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String value) {
    this.address = value;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getAmenities() {
    return amenities;
  }

  public void setAmenities(String value) {
    this.amenities = value;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal value) {
    this.price = value;
  }

  public BigDecimal getMinPrice() {
    return minPrice;
  }

  public void setMinPrice(BigDecimal value) {
    this.minPrice = value;
  }

  public int getOpenHour() {
    return openHour;
  }

  public void setOpenHour(int value) {
    this.openHour = value;
  }

  public int getCloseHour() {
    return closeHour;
  }

  public void setCloseHour(int value) {
    this.closeHour = value;
  }

  public boolean getActive() {
    return active;
  }

  public void setActive(boolean value) {
    this.active = value;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Account getOwner() {
    return owner;
  }

  public void setOwner(Account value) {
    this.owner = value;
  }

  public List<PriceRule> getPriceRules() {
    return priceRules;
  }

  public List<CourtImage> getImages() {
    return images;
  }
}
