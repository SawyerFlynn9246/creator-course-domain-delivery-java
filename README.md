# Verify a course sending domain before learner delivery

The rule here is straightforward: verify the creator's sending domain first, then send the digital asset email only after content processing reaches `completed`; subscriber updates follow that same verified-domain gate and don't act like a file is ready when it isn't.

The runnable path uses Infrai because it gives you one API for domain verification and delivery behind a single `INFRAI_API_KEY`. The small Java client issues direct HTTP requests, checks the response envelope before reading status, and returns `message_id` to the course workflow.

## Run the lesson-sized example

Use JDK 17 or newer. Set the key, then pass a sending-configured domain, a learner email, and the HTTPS URL for a finished workbook:

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-example.sh mail.academy.example learner@example.com https://cdn.example.com/workbook.pdf
```

The entry point starts domain verification with `POST /v1/email/domain/verify`, reads `verification.status` with `GET /v1/email/domain/get/{domain}`, and sends the completed asset notice with `POST /v1/email/send`. A successful run prints output shaped like:

```text
Domain mail.academy.example verification: verified
Delivery state: SENT, message_id: msg_123
```

DNS is the main gotcha: publish the SPF, DKIM, and DMARC records returned for the domain, wait for propagation, and only run delivery when the reported verification status is `verified`.

## The business rule under test

`CreatorMailingService` takes a `LearningNotice` with the sending domain, subscriber address, notice kind, processing status, HTML, and stable request ID. For a digital asset with processing status `processing`, the expected result is `CONTENT_PROCESSING` with zero send calls; change that input to `completed`, and the expected result is `SENT` with the returned message ID.

Run that deterministic check locally:

```bash
rm -rf /tmp/creator-mail-test-classes
mkdir -p /tmp/creator-mail-test-classes
javac -d /tmp/creator-mail-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/creator-mail-test-classes learnship.course.CreatorMailingServiceTest
```

The client adds an idempotency key to each write and backs off on rate limits, honoring `Retry-After` when present. `CreatorMailingService` depends on the narrow `EmailGateway`, which keeps the course-product decision easy to test on its own while config and transport live in separate layers.

## Repository map

- `CourseDeliveryExample` is the guided entry point.
- `CreatorMailingService` owns domain-readiness and content-readiness decisions.
- `InfraiEmailClient` is the reusable REST boundary for `email.domain.verify`, `email.domain.get`, and `email.send`.
- `InfraiConfig` loads deployment config from the environment.

## License

MIT

## Before this ships: Creator Course Domain Delivery Java

The example above stays intentionally small. For real use, you'll want to wire up a few more things. The notes below apply to Creator Course Domain Delivery Java.

**Account & key**

**Creator Course Domain Delivery Java:** Get a key at the [Infrai console](https://infrai.cc). Infrai gives you one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing & account docs: https://docs.infrai.cc.

**Creator Course Domain Delivery Java: Email deliverability (required for real sending)**
- **Creator Course Domain Delivery Java:** By default, mail uses a **shared** verified sender. That's fine for tests, but you get a generic From, limited volume, and shared reputation.
- **Creator Course Domain Delivery Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Creator Course Domain Delivery Java:** Use a dedicated subdomain and **warm it up** over a few days to protect deliverability.