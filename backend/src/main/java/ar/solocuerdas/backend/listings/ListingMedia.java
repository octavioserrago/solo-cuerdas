package ar.solocuerdas.backend.listings;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "listing_media")
public class ListingMedia {

    @Id
    private UUID id;

    @Column(name = "listing_id")
    private UUID listingId;

    // Enum nativo de Postgres (media_type). Igual que en Listing/Sale/Report:
    // String simple, DB_URL ya tiene stringtype=unspecified para escribirlo.
    @Column(name = "media_type")
    private String mediaType;

    private String url;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "perceptual_hash")
    private String perceptualHash;

    @Column(name = "is_verification_photo")
    private Boolean isVerificationPhoto;

    @Column(name = "moderation_status")
    private String moderationStatus;

    public ListingMedia() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getListingId() {
        return listingId;
    }

    public void setListingId(UUID listingId) {
        this.listingId = listingId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getPerceptualHash() {
        return perceptualHash;
    }

    public void setPerceptualHash(String perceptualHash) {
        this.perceptualHash = perceptualHash;
    }

    public Boolean getIsVerificationPhoto() {
        return isVerificationPhoto;
    }

    public void setIsVerificationPhoto(Boolean isVerificationPhoto) {
        this.isVerificationPhoto = isVerificationPhoto;
    }

    public String getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(String moderationStatus) {
        this.moderationStatus = moderationStatus;
    }
}
