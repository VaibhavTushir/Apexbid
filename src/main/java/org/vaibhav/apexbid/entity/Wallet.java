package org.vaibhav.apexbid.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
public class Wallet {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Tells Hibernate to use the User's ID as the Wallet's ID
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "balance", nullable = false)
    private Long balance;

    @Version
    @Column(name = "version")
    private Long version;

}
