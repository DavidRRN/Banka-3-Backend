package rs.raf.stock_service.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Exchange {
    @Id
    private String mic;
    private String name;
    private String acronym;
    @ManyToOne
    private Country polity;
    private String currencyCode;
    private Long timeZone;

}