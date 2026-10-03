package ar.solocuerdas.backend.listings;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class SupabaseMediaStorageClient implements MediaStorageClient {

    private static final String BUCKET = "listing-media";

    private final RestClient restClient = RestClient.create();
    private final String supabaseUrl;
    private final String serviceRoleKey;

    public SupabaseMediaStorageClient(
            @Value("${SUPABASE_URL}") String supabaseUrl,
            @Value("${SUPABASE_SERVICE_ROLE_KEY}") String serviceRoleKey) {
        this.supabaseUrl = supabaseUrl;
        this.serviceRoleKey = serviceRoleKey;
    }

    @Override
    public String createSignedUploadUrl(String path) {
        SignedUploadApiResponse response = restClient.post()
                .uri(URI.create(supabaseUrl + "/storage/v1/object/upload/sign/" + BUCKET + "/" + path))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(SignedUploadApiResponse.class);
        return supabaseUrl + "/storage/v1" + response.url();
    }

    @Override
    public void deleteObject(String path) {
        restClient.delete()
                .uri(URI.create(supabaseUrl + "/storage/v1/object/" + BUCKET + "/" + path))
                .header("Authorization", "Bearer " + serviceRoleKey)
                .header("apikey", serviceRoleKey)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public String publicUrlFor(String path) {
        return supabaseUrl + "/storage/v1/object/public/" + BUCKET + "/" + path;
    }

    private record SignedUploadApiResponse(String url, String token) {
    }
}
