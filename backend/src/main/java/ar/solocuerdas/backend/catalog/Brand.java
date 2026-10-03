package ar.solocuerdas.backend.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Catalogo de solo lectura: se seedea en la migracion, nadie lo crea ni
// edita desde la API todavia.
@Entity
@Table(name = "brands")
public class Brand {

    @Id
    private Integer id;

    private String name;

    public Brand() {
        // JPA
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
