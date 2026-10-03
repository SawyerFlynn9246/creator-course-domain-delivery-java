package learnship.course;

import learnship.email.EmailGateway;

public final class CreatorMailingService {
    private final EmailGateway email;

    public CreatorMailingService(EmailGateway email) {
        this.email = email;
    }

    public DomainOnboarding onboardDomain(String domain, String requestId) {
        String status = email.verifyDomain(domain, requestId);
        return new DomainOnboarding(domain, status);
    }

    public DeliveryResult publish(LearningNotice notice) {
        String status = email.domainStatus(notice.sendingDomain());
        if (!"verified".equalsIgnoreCase(status)) {
            return new DeliveryResult("DOMAIN_PENDING", null);
        }
        if (notice.kind() == NoticeKind.DIGITAL_ASSET && !"completed".equalsIgnoreCase(notice.processingStatus())) {
            return new DeliveryResult("CONTENT_PROCESSING", null);
        }
        String subject = notice.kind() == NoticeKind.DIGITAL_ASSET
                ? "Your course download is ready" : "A new lesson is available";
        String messageId = email.send(notice.subscriberEmail(), subject, notice.html(), notice.requestId());
        return new DeliveryResult("SENT", messageId);
    }

    public enum NoticeKind { DIGITAL_ASSET, SUBSCRIBER_UPDATE }
    public record LearningNotice(String requestId, String sendingDomain, String subscriberEmail,
                                 NoticeKind kind, String processingStatus, String html) {}
    public record DomainOnboarding(String domain, String verificationStatus) {}
    public record DeliveryResult(String state, String messageId) {}
}
