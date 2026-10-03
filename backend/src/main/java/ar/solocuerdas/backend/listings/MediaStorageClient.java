package ar.solocuerdas.backend.listings;

// Abstrae la comunicacion con Supabase Storage. El controller depende de
// esta interfaz, no de la implementacion real -- en los tests se mockea
// (no hay infraestructura en el proyecto para levantar un servidor HTTP
// falso todavia, mismo criterio que con Postgres: la implementacion real
// se verifica a mano contra el servidor real, no con un test automatizado).
public interface MediaStorageClient {

    String createSignedUploadUrl(String path);

    void deleteObject(String path);

    String publicUrlFor(String path);
}
