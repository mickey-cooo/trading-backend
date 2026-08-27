package trading.demo.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AddressEntity extends TemplateEntity {
    @Column(nullable = true)
    private String country;

    @Column(nullable = true)
    private String province;

    @Column(nullable = true)
    private String district;

    @Column(nullable = true)
    private String subDistrict;

    @Column(nullable = true)
    private String postalCode;

    @Column(nullable = true)
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserEntity user;
}
