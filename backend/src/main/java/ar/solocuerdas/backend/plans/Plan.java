package ar.solocuerdas.backend.plans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Entidad minima, de solo lectura: la app nunca crea ni edita planes (se
// seedean en la migracion), asi que solo se mapea lo que listings necesita
// para el cupo. Si mas adelante hace falta mostrar planes en un endpoint
// propio, se completa con el resto de las columnas (code, name, etc).
@Entity
@Table(name = "plans")
public class Plan {

    @Id
    private Integer id;

    @Column(name = "max_active_listings")
    private Integer maxActiveListings;

    public Plan() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getMaxActiveListings() {
        return maxActiveListings;
    }

    public void setMaxActiveListings(Integer maxActiveListings) {
        this.maxActiveListings = maxActiveListings;
    }
}
