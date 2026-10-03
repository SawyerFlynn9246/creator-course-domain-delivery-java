package learnship;

import learnship.config.InfraiConfig;
import learnship.course.CreatorMailingService;
import learnship.course.CreatorMailingService.LearningNotice;
import learnship.course.CreatorMailingService.NoticeKind;
import learnship.email.InfraiEmailClient;

public final class CourseDeliveryExample {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: CourseDeliveryExample <domain> <subscriber-email> <asset-url>");
            System.exit(2);
        }
        CreatorMailingService service = new CreatorMailingService(new InfraiEmailClient(InfraiConfig.fromEnvironment()));
        var onboarding = service.onboardDomain(args[0], "domain-" + args[0]);
        System.out.println("Domain " + onboarding.domain() + " verification: " + onboarding.verificationStatus());

        String html = "<h1>Your workbook is ready</h1><p><a href=\"" + escapeHtml(args[2]) + "\">Download workbook</a></p>";
        var result = service.publish(new LearningNotice("asset-welcome-workbook", args[0], args[1],
                NoticeKind.DIGITAL_ASSET, "completed", html));
        System.out.println("Delivery state: " + result.state() + (result.messageId() == null ? "" : ", message_id: " + result.messageId()));
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
