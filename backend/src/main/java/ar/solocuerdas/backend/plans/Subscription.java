package ar.solocuerdas.backend.plans;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Entidad minima, de solo lectura: alcanza con saber el plan activo de un
// perfil. El resto de las columnas (starts_at, ends_at) se agregan cuando
// algun endpoint propio de subscriptions las necesite.
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    private UUID id;

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "plan_id")
    private Integer planId;

    private String status;

    public Subscription() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getProfileId() {
        return profileId;
    }

    public void setProfileId(UUID profileId) {
        this.profileId = profileId;
    }

    public Integer getPlanId() {
        return planId;
    }

    public void setPlanId(Integer planId) {
        this.planId = planId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
