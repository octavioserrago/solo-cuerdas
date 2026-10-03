package ar.solocuerdas.backend.listings;

public class ListingQuotaExceededException extends RuntimeException {

    public ListingQuotaExceededException() {
        super("Llegaste al limite de publicaciones activas de tu plan. "
                + "Pausa una publicacion existente o mejora tu plan para publicar otra.");
    }
}
