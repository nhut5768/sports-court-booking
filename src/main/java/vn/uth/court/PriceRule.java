package vn.uth.court;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "price_rules")
public class PriceRule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  private Court court;

  @Column(nullable = false)
  private int startHour;

  @Column(nullable = false)
  private int endHour;

  @Column(nullable = false, precision = 12, scale = 0)
  private BigDecimal price;

  public Long getId() {
    return id;
  }

  public Court getCourt() {
    return court;
  }

  public void setCourt(Court value) {
    this.court = value;
  }

  public int getStartHour() {
    return startHour;
  }

  public void setStartHour(int value) {
    this.startHour = value;
  }

  public int getEndHour() {
    return endHour;
  }

  public void setEndHour(int value) {
    this.endHour = value;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal value) {
    this.price = value;
  }
}
