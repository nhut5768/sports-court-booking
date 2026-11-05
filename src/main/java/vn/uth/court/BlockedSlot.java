package vn.uth.court;

import jakarta.persistence.*;

@Entity
@Table(name = "blocked_slots")
public class BlockedSlot {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  public Long getId() {
    return id;
  }

  @ManyToOne(optional = false, fetch = FetchType.EAGER)
  private Court court;

  public Court getCourt() {
    return court;
  }

  public void setCourt(Court value) {
    this.court = value;
  }

  @Column(nullable = false)
  private java.time.LocalDate playDate;

  public java.time.LocalDate getPlayDate() {
    return playDate;
  }

  public void setPlayDate(java.time.LocalDate value) {
    this.playDate = value;
  }

  @Column(nullable = false)
  private int startHour;

  public int getStartHour() {
    return startHour;
  }

  public void setStartHour(int value) {
    this.startHour = value;
  }

  @Column(nullable = false, unique = true, length = 80)
  private String slotKey;

  public String getSlotKey() {
    return slotKey;
  }

  public void setSlotKey(String value) {
    this.slotKey = value;
  }
}
