package learnship.email;

public interface EmailGateway {
    String verifyDomain(String domain, String requestId);
    String domainStatus(String domain);
    String send(String to, String subject, String html, String requestId);
}
