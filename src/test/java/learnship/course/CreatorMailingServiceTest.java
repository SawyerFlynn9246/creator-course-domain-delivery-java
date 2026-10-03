package learnship.course;

import learnship.course.CreatorMailingService.LearningNotice;
import learnship.course.CreatorMailingService.NoticeKind;
import learnship.email.EmailGateway;

public final class CreatorMailingServiceTest {
    public static void main(String[] args) {
        FakeEmailGateway gateway = new FakeEmailGateway();
        CreatorMailingService service = new CreatorMailingService(gateway);

        var waiting = service.publish(new LearningNotice("asset-42", "mail.academy.test", "learner@example.com",
                NoticeKind.DIGITAL_ASSET, "processing", "<p>Workbook</p>"));
        check("CONTENT_PROCESSING".equals(waiting.state()), "processing asset should wait");
        check(gateway.sendCount == 0, "processing asset must not send email");

        var delivered = service.publish(new LearningNotice("asset-42", "mail.academy.test", "learner@example.com",
                NoticeKind.DIGITAL_ASSET, "completed", "<p>Workbook</p>"));
        check("SENT".equals(delivered.state()), "completed asset should send");
        check("msg-course-42".equals(delivered.messageId()), "message id should be observable");
        check(gateway.sendCount == 1, "completed asset should send exactly once");
        System.out.println("CreatorMailingServiceTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeEmailGateway implements EmailGateway {
        int sendCount;
        public String verifyDomain(String domain, String requestId) { return "verified"; }
        public String domainStatus(String domain) { return "verified"; }
        public String send(String to, String subject, String html, String requestId) {
            sendCount++;
            return "msg-course-42";
        }
    }
}
