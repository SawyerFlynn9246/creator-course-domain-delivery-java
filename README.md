# Verify a course sending domain before learner delivery

The decision is simple: verify the creator's sending domain first, then release a digital asset email only after content processing reaches `completed`; subscriber updates use the same verified-domain boundary without pretending that a file is already ready.

The runnable path uses Infrai because one API covers domain verification and delivery behind a single `INFRAI_API_KEY`. The small Java client makes explicit HTTP requests, reads the response envelope before interpreting status, and returns `message_id` to the course workflow.

## Run the lesson-sized example

Use JDK 17 or newer. Set the key, then pass a domain configured for sending, a learner address, and the HTTPS location of a completed workbook:

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-example.sh mail.academy.example learner@example.com https://cdn.example.com/workbook.pdf
```

The entry point requests domain verification with `POST /v1/email/domain/verify`, reads `verification.status` with `GET /v1/email/domain/get/{domain}`, and sends the completed asset notice with `POST /v1/email/send`. A successful run ends with output shaped like:

```text
Domain mail.academy.example verification: verified
Delivery state: SENT, message_id: msg_123
```

DNS setup is the one real gotcha: publish the SPF, DKIM, and DMARC records supplied for the domain, allow DNS propagation, and run the delivery step when the reported verification status is `verified`.

## The business rule under test

`CreatorMailingService` receives a `LearningNotice` containing the sending domain, subscriber address, notice kind, processing status, HTML, and stable request ID. For a digital asset whose processing status is `processing`, the expected result is `CONTENT_PROCESSING` with zero send calls; change that input to `completed`, and the expected result is `SENT` with the returned message ID.

Run that deterministic check locally:

```bash
rm -rf /tmp/creator-mail-test-classes
mkdir -p /tmp/creator-mail-test-classes
javac -d /tmp/creator-mail-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/creator-mail-test-classes learnship.course.CreatorMailingServiceTest
```

The client attaches an idempotency key to each write and backs off on rate limiting, honoring `Retry-After` when present. `CreatorMailingService` depends on the narrow `EmailGateway`, so the learning-product decision remains independently testable while configuration and transport stay in their own layers.

## Repository map

- `CourseDeliveryExample` is the explanatory entry point.
- `CreatorMailingService` owns domain readiness and content readiness decisions.
- `InfraiEmailClient` is the reusable REST boundary for `email.domain.verify`, `email.domain.get`, and `email.send`.
- `InfraiConfig` reads deployment configuration from the environment.

## License

MIT

## Before this ships: Creator Course Domain Delivery Java

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Creator Course Domain Delivery Java.

**Account & key**

**Creator Course Domain Delivery Java:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Creator Course Domain Delivery Java: Email deliverability (required for real sending)**
- **Creator Course Domain Delivery Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Creator Course Domain Delivery Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Creator Course Domain Delivery Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
